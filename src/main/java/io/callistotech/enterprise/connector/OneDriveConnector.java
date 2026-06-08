package io.callistotech.enterprise.connector;

import io.callistotech.enterprise.domain.DocumentSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fetches a PDF from Microsoft OneDrive / SharePoint via the Microsoft Graph API.
 *
 * Expected credentials map keys:
 *   "access_token"  — OAuth2 access token with Files.Read scope
 *   "tenant_id"     — Azure tenant ID (for token refresh flow)
 *   "client_id"     — App registration client ID (for token refresh flow)
 *   "client_secret" — App registration client secret (for token refresh flow)
 *
 * The reference parameter is a Graph API item ID or drive item path
 * (e.g. "/drives/{drive-id}/items/{item-id}" or a SharePoint itemId).
 *
 * TODO(phase-2): implement using Microsoft Graph SDK (com.microsoft.graph:microsoft-graph)
 *   1. Authenticate via ClientSecretCredential or bearer token pass-through
 *   2. Call graphClient.drives(driveId).items(itemId).content().get()
 *   3. Read InputStream to byte array
 *   4. Return as DocumentPayload with DocumentSource.ONEDRIVE
 */
@Slf4j
@Component
public class OneDriveConnector implements SourceConnector {

    @Override
    public DocumentPayload fetch(String reference, Map<String, String> credentials) {
        // TODO(phase-2): implement OneDrive/SharePoint PDF fetch via Microsoft Graph API
        log.warn("OneDriveConnector is not yet implemented for reference=[{}]", reference);
        throw new UnsupportedOperationException(
                "OneDrive connector not yet implemented — use HTTP_URL or BYTES source for now");
    }
}
