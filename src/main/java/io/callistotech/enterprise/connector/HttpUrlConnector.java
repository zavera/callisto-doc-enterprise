package io.callistotech.enterprise.connector;

import io.callistotech.enterprise.domain.DocumentSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Fetches a PDF from an authenticated HTTP/HTTPS URL.
 * Supports Bearer token and Basic auth via the credentials map.
 *
 * Credentials map keys:
 *   "bearer_token"  — sets Authorization: Bearer <token>
 *   "basic_user"    — combined with "basic_pass" for Basic auth
 *   "basic_pass"    — combined with "basic_user" for Basic auth
 *   (no credentials present → unauthenticated GET)
 */
@Slf4j
@Component
public class HttpUrlConnector implements SourceConnector {

    private final RestTemplate restTemplate;

    public HttpUrlConnector() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public DocumentPayload fetch(String reference, Map<String, String> credentials) {
        log.info("HttpUrlConnector fetching document from URL");

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM));

        if (credentials != null) {
            if (credentials.containsKey("bearer_token")) {
                headers.setBearerAuth(credentials.get("bearer_token"));
            } else if (credentials.containsKey("basic_user") && credentials.containsKey("basic_pass")) {
                headers.setBasicAuth(credentials.get("basic_user"), credentials.get("basic_pass"));
            }
        }

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                reference,
                HttpMethod.GET,
                requestEntity,
                byte[].class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException(
                    "HTTP URL fetch returned status " + response.getStatusCode());
        }

        String documentId = extractDocumentIdFromUrl(reference);
        return new DocumentPayload(documentId, reference, response.getBody(), DocumentSource.HTTP_URL);
    }

    /**
     * Extracts a simple document identifier from a URL by taking the last path segment.
     * Never logs the full URL to avoid PII exposure in URLs.
     */
    private String extractDocumentIdFromUrl(String url) {
        if (url == null) return "unknown";
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1);
        }
        return "document";
    }
}
