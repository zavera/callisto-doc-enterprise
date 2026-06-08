package io.callistotech.enterprise.api.dto;

import io.callistotech.enterprise.domain.ExtractedField;
import io.callistotech.enterprise.domain.JobStatus;
import io.callistotech.enterprise.reconciliation.ReconciliationReport;

import java.util.List;
import java.util.UUID;

/**
 * Response payload for a single-document extraction.
 *
 * @param jobId          job UUID
 * @param documentId     document identifier (e.g. file name)
 * @param status         job status
 * @param fields         list of extracted and severity-annotated fields
 * @param reconciliation cross-document reconciliation report; null for single-document extractions
 * @param processingMs   total processing time in milliseconds
 */
public record ExtractionResponse(
        UUID jobId,
        String documentId,
        JobStatus status,
        List<ExtractedField> fields,
        ReconciliationReport reconciliation,
        long processingMs
) {}
