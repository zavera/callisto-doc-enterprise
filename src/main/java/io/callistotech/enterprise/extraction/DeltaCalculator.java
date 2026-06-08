package io.callistotech.enterprise.extraction;

import io.callistotech.enterprise.domain.ExtractedField;
import io.callistotech.enterprise.domain.Severity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Computes severity by comparing an extracted value against a reference value.
 *
 * Severity thresholds (absolute delta):
 *   HIGH   — delta >= high-threshold (default $500)
 *   MEDIUM — delta >= medium-threshold (default $100)
 *   LOW    — delta > 0
 *   NONE   — no reference provided, or values match exactly
 *
 * Pure function — no I/O, no side effects.
 */
@Component
public class DeltaCalculator {

    private final BigDecimal highThreshold;
    private final BigDecimal mediumThreshold;

    public DeltaCalculator(
            @Value("${callisto.extraction.high-threshold:500}") BigDecimal highThreshold,
            @Value("${callisto.extraction.medium-threshold:100}") BigDecimal mediumThreshold) {
        this.highThreshold = highThreshold;
        this.mediumThreshold = mediumThreshold;
    }

    /**
     * Assigns severity to each ExtractedField by comparing its extractedValue against
     * the corresponding reference value (if present).
     *
     * @param field           extracted field with a canonical field name
     * @param referenceValues map of canonical field name → reference value (e.g. borrower-stated)
     * @return the same field with severity assigned
     */
    public ExtractedField assignSeverity(ExtractedField field, Map<String, BigDecimal> referenceValues) {
        if (referenceValues == null || !referenceValues.containsKey(field.fieldName())) {
            return ExtractedField.withSeverity(field, Severity.NONE);
        }

        BigDecimal reference = referenceValues.get(field.fieldName());
        BigDecimal extracted = field.extractedValue();

        if (extracted == null || reference == null) {
            return ExtractedField.withSeverity(field, Severity.NONE);
        }

        BigDecimal delta = extracted.subtract(reference).abs().setScale(2, RoundingMode.HALF_UP);

        Severity severity;
        if (delta.compareTo(highThreshold) >= 0) {
            severity = Severity.HIGH;
        } else if (delta.compareTo(mediumThreshold) >= 0) {
            severity = Severity.MEDIUM;
        } else if (delta.compareTo(BigDecimal.ZERO) > 0) {
            severity = Severity.LOW;
        } else {
            severity = Severity.NONE;
        }

        return ExtractedField.withSeverity(field, severity);
    }
}
