package io.callistotech.enterprise.api.dto;

import io.callistotech.enterprise.domain.JobStatus;

import java.util.List;
import java.util.UUID;

/**
 * Response payload for batch job status and results.
 *
 * @param jobId        batch job UUID
 * @param status       current job status
 * @param totalDocs    total documents submitted
 * @param completedDocs number of documents successfully processed
 * @param failedDocs   number of documents that failed
 * @param results      list of per-document extraction results (populated once status is COMPLETE or PARTIAL)
 */
public record BatchStatusResponse(
        UUID jobId,
        JobStatus status,
        int totalDocs,
        int completedDocs,
        int failedDocs,
        List<ExtractionResponse> results
) {}
