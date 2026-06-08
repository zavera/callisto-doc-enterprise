package io.callistotech.enterprise.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.callistotech.enterprise.api.dto.BatchRequest;
import io.callistotech.enterprise.api.dto.BatchStatusResponse;
import io.callistotech.enterprise.api.dto.ExtractionResponse;
import io.callistotech.enterprise.batch.BatchProcessor;
import io.callistotech.enterprise.connector.*;
import io.callistotech.enterprise.domain.*;
import io.callistotech.enterprise.persistence.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class BatchController {

    private final BatchProcessor batchProcessor;
    private final HttpUrlConnector httpUrlConnector;
    private final GoogleDriveConnector googleDriveConnector;
    private final OneDriveConnector oneDriveConnector;
    private final S3Connector s3Connector;
    private final JobRepository jobRepository;
    private final ExtractionResultRepository extractionResultRepository;
    private final ObjectMapper objectMapper;

    /**
     * POST /api/batch
     * Submits a batch of documents for parallel extraction.
     * Processing runs asynchronously in virtual threads.
     * Poll /api/batch/{jobId}/status for results.
     */
    @PostMapping
    public ResponseEntity<BatchStatusResponse> submitBatch(
            @Valid @RequestBody BatchRequest request,
            @AuthenticationPrincipal ClientEntity client) {

        UUID jobId = UUID.randomUUID();
        log.info("Batch submitted: job=[{}] docs={} fieldMap=[{}]",
                jobId, request.documents().size(), request.fieldMapName());

        // Persist job record immediately so callers can poll status
        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setClientId(client.getId());
        job.setStatus(JobStatus.QUEUED);
        job.setPriority(request.priority() != null ? request.priority() : Priority.NORMAL);
        job.setTotalDocs(request.documents().size());
        job.setCompletedDocs(0);
        job.setFailedDocs(0);
        job.setFieldMapName(request.fieldMapName());
        job.setCreatedAt(Instant.now());
        jobRepository.save(job);

        // Resolve payloads for all documents
        List<DocumentPayload> payloads = request.documents().stream()
                .map(req -> resolveDocument(req))
                .toList();

        // Start batch processing in a virtual thread — returns immediately to caller
        Thread.ofVirtual().start(() -> {
            try {
                batchProcessor.process(
                        jobId,
                        client.getId(),
                        payloads,
                        request.fieldMapName(),
                        request.taxYear(),
                        request.referenceValues(),
                        request.priority()
                );
            } catch (Exception e) {
                log.error("Batch processing threw unexpected error: job=[{}] error={}", jobId, e.getMessage());
            }
        });

        return ResponseEntity.accepted().body(new BatchStatusResponse(
                jobId,
                JobStatus.QUEUED,
                request.documents().size(),
                0,
                0,
                List.of()
        ));
    }

    /**
     * GET /api/batch/{jobId}/status
     * Returns the current status and any completed results for the given batch job.
     */
    @GetMapping("/{jobId}/status")
    public ResponseEntity<BatchStatusResponse> getBatchStatus(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal ClientEntity client) {

        log.info("Batch status requested: job=[{}] client=[{}]", jobId, client.getId());

        JobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        List<ExtractionResultEntity> resultEntities = extractionResultRepository.findByJobId(jobId);

        List<ExtractionResponse> results = resultEntities.stream()
                .map(r -> {
                    List<ExtractedField> fields = deserialiseFields(r.getResultJson());
                    return new ExtractionResponse(
                            r.getJobId(),
                            r.getDocumentId(),
                            JobStatus.COMPLETE,
                            fields,
                            null,   // reconciliation — populated separately via file summary endpoint
                            "",     // documentSummary — TODO wire SummaryService per doc
                            "",     // fileSummary — TODO wire SummaryService.summariseFile() on batch complete
                            0L
                    );
                })
                .toList();

        return ResponseEntity.ok(new BatchStatusResponse(
                jobId,
                job.getStatus(),
                job.getTotalDocs(),
                job.getCompletedDocs(),
                job.getFailedDocs(),
                results
        ));
    }

    private DocumentPayload resolveDocument(io.callistotech.enterprise.api.dto.ExtractionRequest req) {
        return switch (req.documentSource()) {
            case BYTES -> {
                if (req.bytes() == null || req.bytes().length == 0) {
                    throw new IllegalArgumentException("bytes required when documentSource is BYTES");
                }
                yield new DocumentPayload("inline-" + UUID.randomUUID(), "inline",
                        req.bytes(), DocumentSource.BYTES);
            }
            case HTTP_URL -> httpUrlConnector.fetch(req.sourceReference(), req.credentials());
            case GOOGLE_DRIVE -> googleDriveConnector.fetch(req.sourceReference(), req.credentials());
            case ONEDRIVE -> oneDriveConnector.fetch(req.sourceReference(), req.credentials());
            case S3 -> s3Connector.fetch(req.sourceReference(), req.credentials());
        };
    }

    private List<ExtractedField> deserialiseFields(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ExtractedField>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialise extraction result JSON");
            return List.of();
        }
    }
}
