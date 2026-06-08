package io.callistotech.enterprise.api.dto;

import io.callistotech.enterprise.domain.DocumentSource;
import io.callistotech.enterprise.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request payload for single-document extraction.
 *
 * @param documentSource  where the document should be fetched from
 * @param sourceReference source-specific reference (URL, Drive ID, S3 bucket/key); null if BYTES
 * @param bytes           raw PDF bytes; required if documentSource is BYTES
 * @param credentials     source-specific credentials map; null if no auth needed
 * @param fieldMapName    name of the field map to use (e.g. "form_1040")
 * @param taxYear         4-digit tax year (e.g. "2023")
 * @param referenceValues optional map of canonical field name → reference value for delta comparison
 * @param priority        processing priority; defaults to NORMAL
 */
public record ExtractionRequest(
        @NotNull DocumentSource documentSource,
        String sourceReference,
        byte[] bytes,
        Map<String, String> credentials,
        @NotBlank String fieldMapName,
        @NotBlank String taxYear,
        Map<String, BigDecimal> referenceValues,
        Priority priority
) {
    public ExtractionRequest {
        if (priority == null) {
            priority = Priority.NORMAL;
        }
    }
}
