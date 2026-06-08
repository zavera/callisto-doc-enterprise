package io.callistotech.enterprise.batch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.callistotech.enterprise.connector.DocumentPayload;
import io.callistotech.enterprise.domain.ExtractedField;
import io.callistotech.enterprise.domain.JobStatus;
import io.callistotech.enterprise.domain.Priority;
import io.callistotech.enterprise.extraction.ExtractionPipeline;
import io.callistotech.enterprise.fieldmap.FieldMap;
import io.callistotech.enterprise.fieldmap.FieldMapRegistry;
import io.callistotech.enterprise.persistence.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes a batch of documents in parallel virtual threads.
 *
 * Concurrency model:
 *   - Each document is submitted as a virtual thread task
 *   - A semaphore limits active concurrent threads to max-concurrency
 *   - Results are written to DB as each document completes (not after full batch)
 *   - On per-document failure: marks that document as failed, continues batch
 *   - Checkpoint is written after each successful document for resume support
 *   - Resume support: documents with documentId <= lastCheckpointId are skipped
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchProcessor {

    private final ExtractionPipeline extractionPipeline;
    private final FieldMapRegistry fieldMapRegistry;
    private final JobRepository jobRepository;
    private final ExtractionResultRepository extractionResultRepository;
    private final CheckpointStore checkpointStore;
    private final ObjectMapper objectMapper;

    @Value("${callisto.batch.max-concurrency:8}")
    private int maxConcurrency;

    /**
     * Processes a list of documents in parallel virtual threads.
     * Persists each result as it completes. Supports resume via checkpoint.
     *
     * @param jobId          job identifier (used for checkpointing and logging)
     * @param clientId       client UUID for persistence
     * @param documents      list of document payloads to process
     * @param fieldMapName   field map name to use for all documents in this batch
     * @param taxYear        tax year for field map lookup
     * @param referenceValues optional reference values for delta calculation
     * @param priority       processing priority
     */
    public void process(
            UUID jobId,
            UUID clientId,
            List<DocumentPayload> documents,
            String fieldMapName,
            String taxYear,
            Map<String, BigDecimal> referenceValues,
            Priority priority) {

        log.info("BatchProcessor start: job=[{}] docs={} fieldMap=[{}] concurrency={}",
                jobId, documents.size(), fieldMapName, maxConcurrency);

        FieldMap fieldMap = fieldMapRegistry.require(fieldMapName, taxYear);
        Semaphore semaphore = new Semaphore(maxConcurrency);
        List<Thread> threads = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        // Load checkpoint for resume support
        Optional<String> lastCheckpoint = checkpointStore.findLastCompleted(jobId.toString());
        boolean resuming = lastCheckpoint.isPresent();
        log.info("Resume mode: job=[{}] resuming={} lastCheckpoint=[{}]",
                jobId, resuming, lastCheckpoint.orElse("none"));

        // Update job to PROCESSING
        updateJobStatus(jobId, JobStatus.PROCESSING, 0, 0);

        boolean skipMode = resuming;

        for (DocumentPayload doc : documents) {
            // Resume support: skip documents up to and including the last checkpoint
            if (skipMode) {
                if (doc.documentId().equals(lastCheckpoint.get())) {
                    skipMode = false;
                }
                log.debug("Skipping already-processed doc=[{}] for job=[{}]", doc.documentId(), jobId);
                continue;
            }

            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while acquiring semaphore for job=[{}]", jobId);
                break;
            }

            Thread t = Thread.ofVirtual().start(() -> {
                try {
                    processSingleDocument(
                            jobId, clientId, doc, fieldMap, referenceValues, fieldMapName, priority);
                    int c = completed.incrementAndGet();
                    incrementJobCompleted(jobId);
                    checkpointStore.save(jobId.toString(), doc.documentId());
                    log.info("Document complete: job=[{}] doc=[{}] completedSoFar={}", jobId, doc.documentId(), c);
                } catch (Exception e) {
                    int f = failed.incrementAndGet();
                    incrementJobFailed(jobId);
                    log.error("Document failed: job=[{}] doc=[{}] failedSoFar={} error={}",
                            jobId, doc.documentId(), f, e.getMessage());
                } finally {
                    semaphore.release();
                }
            });

            threads.add(t);
        }

        // Wait for all virtual threads to complete
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted waiting for batch threads to complete for job=[{}]", jobId);
            }
        }

        // Determine final job status
        int totalDocs = documents.size();
        int completedCount = completed.get();
        int failedCount = failed.get();

        JobStatus finalStatus;
        if (failedCount == 0) {
            finalStatus = JobStatus.COMPLETE;
        } else if (completedCount == 0) {
            finalStatus = JobStatus.FAILED;
        } else {
            finalStatus = JobStatus.PARTIAL;
        }

        updateJobFinal(jobId, finalStatus, completedCount, failedCount);
        checkpointStore.clear(jobId.toString());

        log.info("BatchProcessor complete: job=[{}] status={} completed={} failed={}",
                jobId, finalStatus, completedCount, failedCount);
    }

    /**
     * Extracts a single document and persists the result.
     * Checks for a cached result (same documentId + hash) before calling extraction.
     */
    private void processSingleDocument(
            UUID jobId,
            UUID clientId,
            DocumentPayload doc,
            FieldMap fieldMap,
            Map<String, BigDecimal> referenceValues,
            String fieldMapName,
            Priority priority) throws JsonProcessingException {

        String docHash = sha256Hex(doc.bytes());

        // Cache hit check: same client + documentId + hash already in DB → reuse
        Optional<ExtractionResultEntity> cached = extractionResultRepository
                .findByClientIdAndDocumentIdAndDocumentHash(clientId, doc.documentId(), docHash);

        if (cached.isPresent()) {
            log.info("Cache hit: job=[{}] doc=[{}] — reusing existing result", jobId, doc.documentId());
            return;
        }

        List<ExtractedField> fields = extractionPipeline.run(
                doc.bytes(),
                fieldMap,
                referenceValues,
                fieldMapName,
                jobId.toString(),
                doc.documentId()
        );

        String resultJson = objectMapper.writeValueAsString(fields);

        ExtractionResultEntity result = new ExtractionResultEntity();
        result.setId(UUID.randomUUID());
        result.setJobId(jobId);
        result.setClientId(clientId);
        result.setDocumentId(doc.documentId());
        result.setDocumentHash(docHash);
        result.setFieldMapName(fieldMapName);
        result.setResultJson(resultJson);
        result.setCreatedAt(Instant.now());

        extractionResultRepository.save(result);
    }

    @Transactional
    void updateJobStatus(UUID jobId, JobStatus status, int completed, int failed) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            job.setCompletedDocs(completed);
            job.setFailedDocs(failed);
            jobRepository.save(job);
        });
    }

    @Transactional
    void incrementJobCompleted(UUID jobId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setCompletedDocs(job.getCompletedDocs() + 1);
            jobRepository.save(job);
        });
    }

    @Transactional
    void incrementJobFailed(UUID jobId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setFailedDocs(job.getFailedDocs() + 1);
            jobRepository.save(job);
        });
    }

    @Transactional
    void updateJobFinal(UUID jobId, JobStatus status, int completed, int failed) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            job.setCompletedDocs(completed);
            job.setFailedDocs(failed);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
        });
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
