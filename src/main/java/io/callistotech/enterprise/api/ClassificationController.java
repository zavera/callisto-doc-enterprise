package io.callistotech.enterprise.api;

import io.callistotech.enterprise.api.dto.ClassificationRequest;
import io.callistotech.enterprise.api.dto.ClassificationResponse;
import io.callistotech.enterprise.classification.DocumentClassification;
import io.callistotech.enterprise.classification.DocumentClassifierService;
import io.callistotech.enterprise.classification.DocumentClassifierService.DocumentToClassify;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal endpoint for classifying documents by IRS form type from their already-
 * extracted content payload. Intended for pipeline-internal callers that already
 * hold the raw OCR text (e.g. {@code ExtractionPipeline.ExtractionResult.rawText()})
 * rather than external clients submitting raw PDFs — see {@code /api/extract} for that.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/classify")
@RequiredArgsConstructor
public class ClassificationController {

    private final DocumentClassifierService documentClassifierService;

    /**
     * POST /api/internal/classify
     * Classifies one or many documents' content payload by IRS form type.
     * A single document's content may contain more than one distinct form
     * (e.g. a bundled upload), so each document maps to a list of form types.
     */
    @PostMapping
    public ResponseEntity<ClassificationResponse> classify(@Valid @RequestBody ClassificationRequest request) {
        log.info("Classification requested for {} document(s)", request.documents().size());

        List<DocumentToClassify> documents = request.documents().stream()
                .map(d -> new DocumentToClassify(d.documentId(), d.content()))
                .toList();

        List<DocumentClassification> classifications = documentClassifierService.classify(documents);

        return ResponseEntity.ok(new ClassificationResponse(classifications));
    }
}
