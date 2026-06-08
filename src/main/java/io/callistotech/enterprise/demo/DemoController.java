package io.callistotech.enterprise.demo;

import io.callistotech.enterprise.api.dto.ExtractionRequest;
import io.callistotech.enterprise.api.dto.ExtractionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Demo endpoint — no API key required, open to the public demo UI.
 * <p>
 * Serves {@code POST /demo/extract}. When real Azure DI credentials are
 * configured, delegates to the full extraction pipeline. When Azure DI is
 * not configured (local / demo mode), returns realistic canned data so the
 * UI is fully functional without credentials.
 * <p>
 * SecurityConfig permits {@code /demo/**} without authentication.
 * No PII is logged — document bytes are not echoed anywhere.
 */
@Slf4j
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoExtractionService demoExtractionService;

    /**
     * POST /demo/extract
     * <p>
     * Accepts an {@link ExtractionRequest} (same DTO as the authenticated endpoint)
     * and returns an {@link ExtractionResponse}. In demo mode the {@code bytes}
     * field is optional — if omitted the canned sample result is returned.
     */
    @PostMapping("/extract")
    public ResponseEntity<ExtractionResponse> extract(
            @Valid @RequestBody ExtractionRequest request) {

        log.info("Demo extraction: fieldMap=[{}] taxYear=[{}] hasBytes=[{}]",
                request.fieldMapName(),
                request.taxYear(),
                request.bytes() != null && request.bytes().length > 0);

        ExtractionResponse response = demoExtractionService.extract(request);
        return ResponseEntity.ok(response);
    }
}
