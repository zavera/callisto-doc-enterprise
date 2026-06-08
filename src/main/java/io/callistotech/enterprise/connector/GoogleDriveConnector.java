package io.callistotech.enterprise.connector;

import io.callistotech.enterprise.domain.DocumentSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fetches a PDF from Google Drive using a service account credential.
 *
 * Expected credentials map keys:
 *   "service_account_json" — full JSON string of the service account key file
 *   "impersonate_email"    — (optional) user email to impersonate via domain-wide delegation
 *
 * The reference parameter is a Google Drive file ID (e.g. "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgVE2upms").
 *
 * TODO(phase-2): implement using Google Drive API client library (com.google.apis:google-api-services-drive)
 *   1. Parse service_account_json into GoogleCredentials
 *   2. Build Drive service with FILES_READONLY scope
 *   3. Call drive.files().get(fileId).executeMediaAsInputStream()
 *   4. Return bytes as DocumentPayload
 */
@Slf4j
@Component
public class GoogleDriveConnector implements SourceConnector {

    @Override
    public DocumentPayload fetch(String reference, Map<String, String> credentials) {
        // TODO(phase-2): implement Google Drive PDF fetch via service account credentials
        log.warn("GoogleDriveConnector is not yet implemented; returning empty stub for fileId=[{}]", reference);
        throw new UnsupportedOperationException(
                "Google Drive connector not yet implemented — use HTTP_URL or BYTES source for now");
    }
}
