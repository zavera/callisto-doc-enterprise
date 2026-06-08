package io.callistotech.enterprise.summary;

import io.callistotech.enterprise.domain.ExtractedField;
import io.callistotech.enterprise.domain.Severity;
import io.callistotech.enterprise.reconciliation.FieldDiscrepancy;
import io.callistotech.enterprise.reconciliation.ReconciliationReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates plain-English summaries of extraction results using Groq (via Spring AI).
 *
 * Two modes:
 *   - Per-document: summarises extracted fields and any flagged discrepancies for one form.
 *   - Per-file:     summarises cross-document reconciliation findings across all forms in a
 *                   mortgage file or tax package. This is the high-value mode for processors.
 *
 * The LLM only ever sees canonical field names, parsed values, severity ratings, and document
 * type labels — never raw PDF content, never raw Azure DI output, never client PII beyond
 * what is already in the structured payload.
 *
 * Summary generation is optional. Set callisto.summary.enabled=false (or leave GROQ_API_KEY
 * unset) to skip LLM calls — fields and reconciliation are always returned regardless.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryService {

    private final ChatClient chatClient;

    @Value("${callisto.summary.enabled:true}")
    private boolean summaryEnabled;

    // -----------------------------------------------------------------------
    // Per-document summary
    // -----------------------------------------------------------------------

    /**
     * Summarises extraction results for a single financial document.
     *
     * Prompt keeps the LLM focused: report what was found, flag anything that needs
     * attention, use plain language an accountant or mortgage processor would expect.
     *
     * @param fields        extracted fields from one document
     * @param sourceDocType document type (e.g. "IRS Form W-2", "IRS Form 1040")
     * @param jobId         for logging only — not included in prompt
     * @return plain-English summary, or empty string if summary is disabled
     */
    public String summariseDocument(List<ExtractedField> fields, String sourceDocType, String jobId) {
        if (!summaryEnabled) {
            return "";
        }
        if (fields == null || fields.isEmpty()) {
            return "No fields were extracted from this document.";
        }

        String fieldBlock = buildFieldBlock(fields);
        String prompt = """
                You are a financial document analyst assisting an accountant or mortgage processor.

                Document type: %s

                The following fields were extracted from the document:
                %s

                Write a concise 2-3 sentence summary of what was found. If any fields are marked
                HIGH or MEDIUM severity, explicitly call them out and state what the discrepancy is.
                Use plain language. Do not repeat every field — focus on what matters for review.
                Do not invent values not present in the data above.
                """.formatted(sourceDocType, fieldBlock);

        try {
            String summary = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            log.info("Document summary generated: job=[{}]", jobId);
            return summary;
        } catch (Exception e) {
            log.warn("Summary generation failed for job=[{}]: {}", jobId, e.getMessage());
            return "";
        }
    }

    // -----------------------------------------------------------------------
    // Per-file summary (cross-document — the high-value mode)
    // -----------------------------------------------------------------------

    /**
     * Summarises cross-document reconciliation findings across all forms in a file.
     *
     * This is the primary value-add for mortgage processors and tax preparers reviewing
     * a full client package (1040 + W-2s + Schedule C + bank statements). Instead of
     * reading individual field discrepancies, the reviewer gets a single paragraph
     * describing what is consistent, what conflicts, and what requires follow-up.
     *
     * @param report        reconciliation report produced by CrossDocumentReconciler
     * @param docTypes      list of document types present in the file
     * @param jobId         for logging only
     * @return plain-English file-level summary, or empty string if summary is disabled
     */
    public String summariseFile(ReconciliationReport report, List<String> docTypes, String jobId) {
        if (!summaryEnabled) {
            return "";
        }
        if (report == null) {
            return "";
        }

        String docList = String.join(", ", docTypes);
        String discrepancyBlock = buildDiscrepancyBlock(report.discrepancies());
        boolean clean = !report.hasDiscrepancies();

        String prompt = """
                You are a financial document analyst assisting an accountant or mortgage processor.

                Documents reviewed: %s

                Cross-document reconciliation results:
                %s

                Write a concise 3-4 sentence summary of the file review findings. If income figures
                are consistent across documents, state that clearly. If discrepancies were found,
                identify which fields conflict, between which documents, and what the difference is.
                Recommend whether human review is required. Use plain language appropriate for a
                mortgage underwriter or tax professional. Do not invent values not present above.
                """.formatted(docList, clean ? "No discrepancies found. All cross-document fields are consistent." : discrepancyBlock);

        try {
            String summary = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            log.info("File summary generated: job=[{}] discrepancies={}", jobId, report.discrepancies().size());
            return summary;
        } catch (Exception e) {
            log.warn("File summary generation failed for job=[{}]: {}", jobId, e.getMessage());
            return "";
        }
    }

    // -----------------------------------------------------------------------
    // Prompt builders — structured data only, no raw PDF content or PII
    // -----------------------------------------------------------------------

    private String buildFieldBlock(List<ExtractedField> fields) {
        return fields.stream()
                .filter(f -> f.extractedValue() != null)
                .map(f -> {
                    String line = "- %s: %s (confidence: %.0f%%)".formatted(
                            f.fieldName(),
                            f.extractedValue().toPlainString(),
                            f.confidence().multiply(java.math.BigDecimal.valueOf(100)));
                    if (f.severity() == Severity.HIGH || f.severity() == Severity.MEDIUM) {
                        line += " ⚠ %s SEVERITY DISCREPANCY".formatted(f.severity().name());
                    }
                    return line;
                })
                .collect(Collectors.joining("\n"));
    }

    private String buildDiscrepancyBlock(List<FieldDiscrepancy> discrepancies) {
        if (discrepancies == null || discrepancies.isEmpty()) {
            return "No discrepancies found.";
        }
        return discrepancies.stream()
                .map(d -> "- %s: %s shows %s, %s shows %s — delta %s [%s]".formatted(
                        d.fieldName(),
                        d.sourceDocA(), d.valueA() != null ? d.valueA().toPlainString() : "null",
                        d.sourceDocB(), d.valueB() != null ? d.valueB().toPlainString() : "null",
                        d.delta() != null ? d.delta().toPlainString() : "N/A",
                        d.severity().name()))
                .collect(Collectors.joining("\n"));
    }
}
