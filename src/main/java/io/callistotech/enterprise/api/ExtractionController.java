package io.callistotech.enterprise.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.callistotech.enterprise.api.dto.ExtractionRequest;
import io.callistotech.enterprise.api.dto.ExtractionResponse;
import io.callistotech.enterprise.batch.BatchProcessor;
import io.callistotech.enterprise.connector.*;
import io.callistotech.enterprise.domain.*;
import io.callistotech.enterprise.extraction.ExtractionPipeline;
import io.callistotech.enterprise.fieldmap.FieldMap;
import io.callistotech.enterprise.fieldmap.FieldMapRegistry;
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
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExtractionController {

    private final ExtractionPipeline extractionPipeline;
    private final FieldMapRegistry fieldMapRegistry;
    private final HttpUrlConnector httpUrlConnector;
    private final GoogleDriveConnector googleDriveConnector;
    private final OneDriveConnector oneDriveConnector;
    private final S3Connector s3Connector;
    private final JobRepository jobRepository;
    private final ExtractionResultRepository extractionResultRepository;
    private final ObjectMapper objectMapper;

    /**
     * POST /api/extract
     * Extracts structured fields from a single financial document.
     * Runs synchronously — returns results in the response body.
     * For large documents or high-volume use, prefer /api/batch.
     */
    @PostMapping("/extract")
    public ResponseEntity<ExtractionResponse> extract(
            @Valid @RequestBody ExtractionRequest request,
            @AuthenticationPrincipal ClientEntity client) {

        long startMs = System.currentTimeMillis();
        UUID jobId = UUID.randomUUID();

        log.info("Single extraction: job=[{}] fieldMap=[{}] source=[{}]",
                jobId, request.fieldMapName(), request.documentSource());

        // Resolve document bytes from source
        DocumentPayload payload = resolveDocument(request);

        // Persist job record
        JobEntity job = createJobEntity(jobId, client.getId(), request.fieldMapName(), 1, request.priority());
        job.setStatus(JobStatus.PROCESSING);
        jobRepository.save(job);

        FieldMap fieldMap = fieldMapRegistry.require(request.fieldMapName(), request.taxYear());

        List<ExtractedField> fields;
        try {
            fields = extractionPipeline.run(
                    payload.bytes(),
                    fieldMap,
                    request.referenceValues(),
                    request.fieldMapName(),
                    jobId.toString(),
                    payload.documentId()
            );

            job.setStatus(JobStatus.COMPLETE);
            job.setCompletedDocs(1);
            job.setCompletedAt(Instant.now());
        } catch (Exception e) {
            log.error("Extraction failed: job=[{}] error={}", jobId, e.getMessage());
            job.setStatus(JobStatus.FAILED);
            job.setFailedDocs(1);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
            throw new RuntimeException("Extraction failed for job=[" + jobId + "]", e);
        }

        jobRepository.save(job);
        long processingMs = System.currentTimeMillis() - startMs;

        return ResponseEntity.ok(new ExtractionResponse(
                jobId,
                payload.documentId(),
                JobStatus.COMPLETE,
                fields,
                null,
                processingMs
        ));
    }

    /**
     * GET /api/results/{jobId}
     * Retrieves all extraction results for the given job.
     */
    @GetMapping("/results/{jobId}")
    public ResponseEntity<List<ExtractionResponse>> getResults(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal ClientEntity client) {

        log.info("Results requested: job=[{}] client=[{}]", jobId, client.getId());

        List<ExtractionResultEntity> results = extractionResultRepository.findByJobId(jobId);

        List<ExtractionResponse> responses = results.stream()
                .map(r -> {
                    List<ExtractedField> fields = deserialiseFields(r.getResultJson());
                    return new ExtractionResponse(
                            r.getJobId(),
                            r.getDocumentId(),
                            JobStatus.COMPLETE,
                            fields,
                            null,
                            0L
                    );
                })
                .toList();

        return ResponseEntity.ok(responses);
    }

    private DocumentPayload resolveDocument(ExtractionRequest request) {
        return switch (request.documentSource()) {
            case BYTES -> {
                if (request.bytes() == null || request.bytes().length == 0) {
                    throw new IllegalArgumentException("bytes must be provided when documentSource is BYTES");
                }
                yield new DocumentPayload("inline-" + UUID.randomUUID(), "inline",
                        request.bytes(), DocumentSource.BYTES);
            }
            case HTTP_URL -> httpUrlConnector.fetch(request.sourceReference(), request.credentials());
            case GOOGLE_DRIVE -> googleDriveConnector.fetch(request.sourceReference(), request.credentials());
            case ONEDRIVE -> oneDriveConnector.fetch(request.sourceReference(), request.credentials());
            case S3 -> s3Connector.fetch(request.sourceReference(), request.credentials());
        };
    }

    private JobEntity createJobEntity(UUID jobId, UUID clientId, String fieldMapName,
                                       int totalDocs, Priority priority) {
        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setClientId(clientId);
        job.setStatus(JobStatus.QUEUED);
        job.setPriority(priority != null ? priority : Priority.NORMAL);
        job.setTotalDocs(totalDocs);
        job.setCompletedDocs(0);
        job.setFailedDocs(0);
        job.setFieldMapName(fieldMapName);
        job.setCreatedAt(Instant.now());
        return job;
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
