package io.callistotech.enterprise.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request payload for document classification. Accepts one or many documents in a
 * single call — a single upload can bundle multiple IRS form types across pages.
 *
 * @param documents one or more documents, each carrying its raw OCR content payload
 *                  (not KV pairs)
 */
public record ClassificationRequest(
        @NotEmpty List<@Valid Document> documents
) {
    /**
     * @param documentId caller-supplied identifier, echoed back in the response
     * @param content    full raw OCR text for the document (Azure DI content payload)
     */
    public record Document(
            @NotBlank String documentId,
            @NotBlank String content
    ) {}
}
