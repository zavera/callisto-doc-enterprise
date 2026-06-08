package io.callistotech.enterprise.extraction;

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient;
import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzeResult;
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField;
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentKeyValuePair;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.util.BinaryData;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Calls Azure Document Intelligence to extract key-value pairs from a PDF document.
 *
 * Recovery chain (4 stages):
 *   Stage 0 — full document submitted as-is
 *   Stage 1 — split into single-page chunks via PDFBox; each page submitted separately
 *   Stage 2 — TODO(phase-2): DPI reduction before resubmission
 *   Stage 3 — TODO(phase-3): rotation correction before resubmission
 *
 * Endpoint pool: round-robin via AtomicInteger counter % pool size for throughput.
 * Exponential backoff with jitter on HTTP 429 (rate limit) responses.
 */
@Slf4j
@Component
public class AzureDocIntelExtractor {

    private static final String MODEL_ID = "prebuilt-document";
    private static final int MAX_RETRIES = 4;
    private static final long BASE_BACKOFF_MS = 1_000L;

    private final List<DocumentAnalysisClient> clientPool;
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    @Value("${callisto.extraction.confidence-threshold:0.50}")
    private double confidenceThreshold;

    public AzureDocIntelExtractor(List<DocumentAnalysisClient> clientPool) {
        if (clientPool == null || clientPool.isEmpty()) {
            throw new IllegalArgumentException("DocumentAnalysisClient pool must not be empty");
        }
        this.clientPool = clientPool;
    }

    /**
     * Extracts key-value pairs from a PDF document byte array.
     * Attempts stage 0 first; falls back through the recovery chain on failure.
     *
     * @param pdfBytes raw PDF bytes
     * @param jobId    job identifier for logging (no PII)
     * @param docId    document identifier for logging (no PII)
     * @return list of extracted KV entries
     */
    public List<KvEntry> extract(byte[] pdfBytes, String jobId, String docId) {
        log.info("Starting extraction stage 0 for job=[{}] doc=[{}]", jobId, docId);

        try {
            return extractWithRetry(pdfBytes, jobId, docId);
        } catch (Exception e) {
            log.warn("Stage 0 failed for job=[{}] doc=[{}], falling back to page chunks: {}",
                    jobId, docId, e.getMessage());
        }

        // Stage 1: page-by-page extraction via PDFBox split
        try {
            return extractPageChunks(pdfBytes, jobId, docId);
        } catch (Exception e) {
            log.warn("Stage 1 page-chunk extraction failed for job=[{}] doc=[{}]: {}",
                    jobId, docId, e.getMessage());
        }

        // Stage 2: TODO(phase-2): DPI reduction — reduce PDF resolution and resubmit
        // Stage 3: TODO(phase-3): rotation correction — detect and correct page rotation, resubmit

        log.error("All extraction stages failed for job=[{}] doc=[{}]", jobId, docId);
        return List.of();
    }

    /**
     * Submits a single PDF byte array to Azure DI with exponential backoff on 429.
     */
    private List<KvEntry> extractWithRetry(byte[] pdfBytes, String jobId, String docId) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return callAzureDi(pdfBytes);
            } catch (HttpResponseException e) {
                if (e.getResponse().getStatusCode() == 429 && attempt < MAX_RETRIES) {
                    long backoff = computeBackoff(attempt);
                    log.warn("Rate limited (429) for job=[{}] doc=[{}], attempt {}/{}, backing off {}ms",
                            jobId, docId, attempt + 1, MAX_RETRIES, backoff);
                    sleep(backoff);
                } else {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Exhausted retries for job=[" + jobId + "] doc=[" + docId + "]");
    }

    /**
     * Splits PDF into single-page documents using PDFBox and extracts each page separately.
     * Merges results from all pages.
     */
    private List<KvEntry> extractPageChunks(byte[] pdfBytes, String jobId, String docId) throws IOException {
        List<KvEntry> allEntries = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            log.info("Stage 1: splitting {} pages for job=[{}] doc=[{}]", pageCount, jobId, docId);

            for (int i = 0; i < pageCount; i++) {
                byte[] pageBytes = extractSinglePage(document, i);
                try {
                    List<KvEntry> pageEntries = extractWithRetry(pageBytes, jobId, docId + "_p" + i);
                    allEntries.addAll(pageEntries);
                } catch (Exception e) {
                    log.warn("Page {} extraction failed for job=[{}] doc=[{}]: {}", i, jobId, docId, e.getMessage());
                }
            }
        }

        return allEntries;
    }

    /**
     * Extracts a single page from a PDDocument into a new PDF byte array.
     */
    private byte[] extractSinglePage(PDDocument sourceDoc, int pageIndex) throws IOException {
        try (PDDocument singlePage = new PDDocument();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            PDPage page = sourceDoc.getPage(pageIndex);
            singlePage.addPage(page);
            singlePage.save(bos);
            return bos.toByteArray();
        }
    }

    /**
     * Picks the next client from the pool (round-robin) and calls Azure DI.
     */
    private List<KvEntry> callAzureDi(byte[] pdfBytes) {
        DocumentAnalysisClient client = nextClient();
        AnalyzeResult result = client
                .beginAnalyzeDocument(MODEL_ID, BinaryData.fromBytes(pdfBytes))
                .getFinalResult();

        return buildKvEntries(result);
    }

    private List<KvEntry> buildKvEntries(AnalyzeResult result) {
        List<KvEntry> entries = new ArrayList<>();

        if (result.getKeyValuePairs() == null) {
            return entries;
        }

        for (DocumentKeyValuePair pair : result.getKeyValuePairs()) {
            if (pair.getKey() == null || pair.getValue() == null) continue;

            String rawKey = pair.getKey().getContent();
            String rawValue = pair.getValue().getContent();
            Float confidenceFloat = pair.getConfidence();

            BigDecimal confidence = confidenceFloat != null
                    ? BigDecimal.valueOf(confidenceFloat.doubleValue())
                    : BigDecimal.ZERO;

            entries.add(new KvEntry(rawKey, rawValue, confidence));
        }

        return entries;
    }

    private DocumentAnalysisClient nextClient() {
        int idx = Math.abs(roundRobinCounter.getAndIncrement() % clientPool.size());
        return clientPool.get(idx);
    }

    private long computeBackoff(int attempt) {
        long exponential = BASE_BACKOFF_MS * (1L << attempt);
        long jitter = (long) (Math.random() * exponential * 0.25);
        return exponential + jitter;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Raw key-value entry from Azure DI, before normalisation.
     *
     * @param key        raw key string from Azure DI
     * @param value      raw value string from Azure DI
     * @param confidence Azure DI confidence score (0.0 – 1.0)
     */
    public record KvEntry(String key, String value, BigDecimal confidence) {}
}
