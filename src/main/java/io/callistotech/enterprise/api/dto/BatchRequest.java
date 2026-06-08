package io.callistotech.enterprise.api.dto;

import io.callistotech.enterprise.domain.Priority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Request payload for batch document extraction.
 *
 * @param documents       list of documents to extract (each carries its own source reference)
 * @param fieldMapName    field map name applied to all documents in this batch
 * @param taxYear         4-digit tax year applied to all documents in this batch
 * @param referenceValues optional reference values for delta comparison across all documents
 * @param priority        processing priority for the entire batch; defaults to NORMAL
 */
public record BatchRequest(
        @NotEmpty @Valid List<ExtractionRequest> documents,
        @NotBlank String fieldMapName,
        @NotBlank String taxYear,
        Map<String, BigDecimal> referenceValues,
        Priority priority
) {
    public BatchRequest {
        if (priority == null) {
            priority = Priority.NORMAL;
        }
    }
}
