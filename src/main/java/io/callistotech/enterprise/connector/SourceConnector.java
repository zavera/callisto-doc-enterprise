package io.callistotech.enterprise.connector;

import java.util.Map;

/**
 * Strategy interface for fetching raw PDF bytes from a document source.
 * Each implementation handles a specific DocumentSource type.
 */
public interface SourceConnector {

    /**
     * Fetches PDF bytes identified by the given reference.
     *
     * @param reference   source-specific reference (e.g. Google Drive file ID, S3 key, URL)
     * @param credentials source-specific credentials (e.g. access token, service account JSON key)
     * @return DocumentPayload containing raw PDF bytes and provenance
     */
    DocumentPayload fetch(String reference, Map<String, String> credentials);
}
