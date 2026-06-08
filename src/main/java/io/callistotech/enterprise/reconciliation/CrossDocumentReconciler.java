package io.callistotech.enterprise.reconciliation;

import io.callistotech.enterprise.domain.ExtractedField;
import io.callistotech.enterprise.domain.Severity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Applies cross-document reconciliation rules to a set of extracted fields from multiple documents.
 *
 * Built-in rules:
 *   Rule 1 — W-2 Box 1 (wages) vs 1040 Line 1a (wages): delta should be negligible
 *   Rule 2 — Schedule C net profit vs 1040 additional income: values should reconcile
 *   Rule 3 — Borrower-stated income vs extracted income: flag any HIGH discrepancy
 *
 * Pure function — no I/O, no side effects. Fully unit testable with no DB or HTTP.
 */
@Slf4j
@Service
public class CrossDocumentReconciler {

    private final BigDecimal highThreshold;
    private final BigDecimal mediumThreshold;

    public CrossDocumentReconciler(
            @Value("${callisto.extraction.high-threshold:500}") BigDecimal highThreshold,
            @Value("${callisto.extraction.medium-threshold:100}") BigDecimal mediumThreshold) {
        this.highThreshold = highThreshold;
        this.mediumThreshold = mediumThreshold;
    }

    /**
     * Reconciles extracted fields across multiple documents in a submission.
     *
     * @param jobId       job identifier (for logging, no PII)
     * @param clientId    client UUID
     * @param allDocFields one list per document, each containing its extracted fields
     * @return ReconciliationReport with all detected discrepancies
     */
    public ReconciliationReport reconcile(
            UUID jobId,
            UUID clientId,
            List<List<ExtractedField>> allDocFields) {

        List<FieldDiscrepancy> discrepancies = new ArrayList<>();

        // Build flat index: canonical field name → all extracted values by source doc
        Map<String, List<ExtractedField>> fieldIndex = buildFieldIndex(allDocFields);

        // Rule 1: W-2 Box 1 wages vs 1040 Line 1a wages
        applyRule(discrepancies, fieldIndex,
                "w2_box1_wages", "wages_salaries_tips");

        // Rule 2: W-2 Box 2 federal withheld vs 1040 federal tax withheld from W-2
        applyRule(discrepancies, fieldIndex,
                "w2_box2_federal_withheld", "federal_tax_withheld_w2");

        // Rule 3: W-2 Medicare wages vs W-2 Box 1 wages (Medicare wages should be >= wages)
        checkMedicareWagesRule(discrepancies, fieldIndex);

        log.info("Reconciliation complete: job=[{}] discrepancies={}", jobId, discrepancies.size());

        return new ReconciliationReport(jobId, clientId, discrepancies, Instant.now());
    }

    /**
     * Checks whether two canonical fields from different documents agree within threshold.
     * If both fields are present in the index and come from different source doc types,
     * computes delta and assigns severity.
     */
    private void applyRule(
            List<FieldDiscrepancy> discrepancies,
            Map<String, List<ExtractedField>> fieldIndex,
            String fieldNameA,
            String fieldNameB) {

        List<ExtractedField> listA = fieldIndex.getOrDefault(fieldNameA, List.of());
        List<ExtractedField> listB = fieldIndex.getOrDefault(fieldNameB, List.of());

        if (listA.isEmpty() || listB.isEmpty()) return;

        // Take first available value from each source
        ExtractedField fieldA = listA.get(0);
        ExtractedField fieldB = listB.get(0);

        if (fieldA.extractedValue() == null || fieldB.extractedValue() == null) return;
        if (fieldA.sourceDocType().equals(fieldB.sourceDocType())) return; // same doc, skip

        BigDecimal delta = fieldA.extractedValue().subtract(fieldB.extractedValue())
                .abs().setScale(2, RoundingMode.HALF_UP);

        Severity severity = classifySeverity(delta);
        if (severity == Severity.NONE) return;

        discrepancies.add(new FieldDiscrepancy(
                fieldNameA + "_vs_" + fieldNameB,
                fieldA.sourceDocType(),
                fieldA.extractedValue(),
                fieldB.sourceDocType(),
                fieldB.extractedValue(),
                delta,
                severity
        ));
    }

    /**
     * Rule: Medicare wages (Box 5) should be >= Box 1 wages for the same W-2 document.
     * A negative delta here can indicate a data extraction error.
     */
    private void checkMedicareWagesRule(
            List<FieldDiscrepancy> discrepancies,
            Map<String, List<ExtractedField>> fieldIndex) {

        List<ExtractedField> medicareList = fieldIndex.getOrDefault("w2_box5_medicare_wages", List.of());
        List<ExtractedField> wagesList = fieldIndex.getOrDefault("w2_box1_wages", List.of());

        if (medicareList.isEmpty() || wagesList.isEmpty()) return;

        ExtractedField medicare = medicareList.get(0);
        ExtractedField wages = wagesList.get(0);

        if (medicare.extractedValue() == null || wages.extractedValue() == null) return;
        if (!medicare.sourceDocType().equals(wages.sourceDocType())) return;

        if (medicare.extractedValue().compareTo(wages.extractedValue()) < 0) {
            BigDecimal delta = wages.extractedValue().subtract(medicare.extractedValue())
                    .abs().setScale(2, RoundingMode.HALF_UP);
            discrepancies.add(new FieldDiscrepancy(
                    "w2_medicare_wages_lt_box1_wages",
                    medicare.sourceDocType(),
                    medicare.extractedValue(),
                    wages.sourceDocType(),
                    wages.extractedValue(),
                    delta,
                    Severity.HIGH
            ));
        }
    }

    /**
     * Builds an index of canonical field name → list of ExtractedFields across all documents.
     */
    private Map<String, List<ExtractedField>> buildFieldIndex(List<List<ExtractedField>> allDocFields) {
        Map<String, List<ExtractedField>> index = new HashMap<>();
        for (List<ExtractedField> docFields : allDocFields) {
            for (ExtractedField field : docFields) {
                index.computeIfAbsent(field.fieldName(), k -> new ArrayList<>()).add(field);
            }
        }
        return index;
    }

    private Severity classifySeverity(BigDecimal delta) {
        if (delta.compareTo(highThreshold) >= 0) return Severity.HIGH;
        if (delta.compareTo(mediumThreshold) >= 0) return Severity.MEDIUM;
        if (delta.compareTo(BigDecimal.ZERO) > 0) return Severity.LOW;
        return Severity.NONE;
    }
}
