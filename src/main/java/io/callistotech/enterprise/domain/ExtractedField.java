package io.callistotech.enterprise.domain;

import java.math.BigDecimal;

/**
 * Represents a single extracted field from a financial document.
 *
 * @param fieldName     canonical field name (from FieldMap)
 * @param extractedValue parsed numeric value, null if non-numeric or absent
 * @param rawValue      raw string value from Azure DI before parsing
 * @param confidence    Azure DI confidence score (0.0 – 1.0)
 * @param section       document section or form line (e.g. "Line 1a", "Box 1")
 * @param sourceDocType document type identifier (e.g. "form_1040", "form_w2")
 * @param severity      cross-check severity vs reference value; NONE if no reference provided
 */
public record ExtractedField(
        String fieldName,
        BigDecimal extractedValue,
        String rawValue,
        BigDecimal confidence,
        String section,
        String sourceDocType,
        Severity severity
) {
    public static ExtractedField withSeverity(ExtractedField base, Severity severity) {
        return new ExtractedField(
                base.fieldName(),
                base.extractedValue(),
                base.rawValue(),
                base.confidence(),
                base.section(),
                base.sourceDocType(),
                severity
        );
    }
}
