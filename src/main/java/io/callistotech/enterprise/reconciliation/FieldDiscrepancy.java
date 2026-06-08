package io.callistotech.enterprise.reconciliation;

import io.callistotech.enterprise.domain.Severity;

import java.math.BigDecimal;

/**
 * Describes a discrepancy between the same field extracted from two different documents.
 *
 * @param fieldName   canonical field name
 * @param sourceDocA  document type of the first source (e.g. "form_w2")
 * @param valueA      extracted value from document A
 * @param sourceDocB  document type of the second source (e.g. "form_1040")
 * @param valueB      extracted value from document B
 * @param delta       absolute difference between valueA and valueB
 * @param severity    severity classification of the discrepancy
 */
public record FieldDiscrepancy(
        String fieldName,
        String sourceDocA,
        BigDecimal valueA,
        String sourceDocB,
        BigDecimal valueB,
        BigDecimal delta,
        Severity severity
) {}
