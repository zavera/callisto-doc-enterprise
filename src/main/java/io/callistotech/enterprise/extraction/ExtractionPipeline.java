package io.callistotech.enterprise.extraction;

import io.callistotech.enterprise.domain.ExtractedField;
import io.callistotech.enterprise.domain.Severity;
import io.callistotech.enterprise.fieldmap.FieldMap;
import io.callistotech.enterprise.summary.SummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates extraction for a single document:
 *   1. AzureDocIntelExtractor → raw KvEntry list
 *   2. KvNormalizer → canonical field name + parsed BigDecimal value
 *   3. DeltaCalculator → severity vs. reference values
 *
 * Pure pipeline — no persistence. Callers are responsible for persisting the result.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractionPipeline {

    private final AzureDocIntelExtractor extractor;
    private final DeltaCalculator deltaCalculator;
    private final SummaryService summaryService;

    /**
     * Runs the full extraction pipeline for a single document.
     *
     * @param pdfBytes        raw PDF bytes
     * @param fieldMap        field map defining canonical field name resolution
     * @param referenceValues optional map of canonical field name → reference value for delta comparison
     * @param sourceDocType   document type identifier for provenance (e.g. "form_1040")
     * @param jobId           job identifier for logging (no PII)
     * @param docId           document identifier for logging (no PII)
     * @return list of extracted and severity-annotated fields
     */
    public List<ExtractedField> run(
            byte[] pdfBytes,
            FieldMap fieldMap,
            Map<String, BigDecimal> referenceValues,
            String sourceDocType,
            String jobId,
            String docId) {

        return runInternal(pdfBytes, fieldMap, referenceValues, sourceDocType, jobId, docId).fields();
    }

    private Extraction runInternal(
            byte[] pdfBytes,
            FieldMap fieldMap,
            Map<String, BigDecimal> referenceValues,
            String sourceDocType,
            String jobId,
            String docId) {

        log.info("Pipeline start: job=[{}] doc=[{}] fieldMap=[{}]", jobId, docId, fieldMap.name());

        KvNormalizer normalizer = new KvNormalizer(fieldMap);

        AzureDocIntelExtractor.AnalysisOutcome outcome = extractor.extract(pdfBytes, jobId, docId);
        List<AzureDocIntelExtractor.KvEntry> rawEntries = outcome.entries();
        log.info("Extraction complete: job=[{}] doc=[{}] rawEntries={}", jobId, docId, rawEntries.size());

        List<ExtractedField> fields = rawEntries.stream()
                .map(kv -> {
                    String canonicalName = normalizer.resolveFieldName(kv.key());
                    BigDecimal parsedValue = normalizer.parseValue(kv.value()).orElse(null);

                    ExtractedField field = new ExtractedField(
                            canonicalName,
                            parsedValue,
                            kv.value(),
                            kv.confidence(),
                            null,       // section not populated at this layer
                            sourceDocType,
                            Severity.NONE
                    );

                    return deltaCalculator.assignSeverity(field, referenceValues);
                })
                .toList();

        long highCount = fields.stream().filter(f -> f.severity() == Severity.HIGH).count();
        long mediumCount = fields.stream().filter(f -> f.severity() == Severity.MEDIUM).count();
        log.info("Pipeline complete: job=[{}] doc=[{}] fields={} high={} medium={}",
                jobId, docId, fields.size(), highCount, mediumCount);

        return new Extraction(fields, outcome.content());
    }

    /**
     * Runs the full extraction pipeline and appends a plain-English Groq summary.
     *
     * Use this for real-time single-document extractions where the caller wants
     * an immediately reviewable result. For batch jobs, call {@link #run} per
     * document and generate the file-level summary once via
     * {@link SummaryService#summariseFile} after reconciliation completes.
     *
     * @return {@link ExtractionResult} containing fields + per-document summary
     */
    public ExtractionResult runWithSummary(
            byte[] pdfBytes,
            FieldMap fieldMap,
            Map<String, BigDecimal> referenceValues,
            String sourceDocType,
            String jobId,
            String docId) {

        Extraction extraction = runInternal(pdfBytes, fieldMap, referenceValues, sourceDocType, jobId, docId);
        String summary = summaryService.summariseDocument(extraction.fields(), sourceDocType, jobId);
        return new ExtractionResult(extraction.fields(), summary, extraction.rawText());
    }

    /**
     * Holds the output of a pipeline run that includes summary generation.
     *
     * @param rawText full raw OCR text for the document (Azure DI {@code getContent()}),
     *                kept separate from {@code fields} since it's the input a document
     *                classifier would read instead of KV pairs
     */
    public record ExtractionResult(
            List<ExtractedField> fields,
            String documentSummary,
            String rawText
    ) {}

    /** Internal pairing of extracted fields with the raw OCR text from the same Azure DI call. */
    private record Extraction(List<ExtractedField> fields, String rawText) {}
}
