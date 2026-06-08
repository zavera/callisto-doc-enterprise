package io.callistotech.enterprise.connector;

import io.callistotech.enterprise.domain.DocumentSource;

/**
 * Holds PDF bytes fetched from a source connector along with provenance metadata.
 *
 * @param documentId      unique identifier for this document (e.g. file name, S3 key, Drive ID)
 * @param sourceReference original reference string passed to the connector (no PII expected)
 * @param bytes           raw PDF bytes
 * @param sourceType      where the bytes were fetched from
 */
public record DocumentPayload(
        String documentId,
        String sourceReference,
        byte[] bytes,
        DocumentSource sourceType
) {}
