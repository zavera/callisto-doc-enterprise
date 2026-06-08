package io.callistotech.enterprise.connector;

import io.callistotech.enterprise.domain.DocumentSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;
import java.util.Map;

/**
 * Fetches a PDF from an Amazon S3 bucket using AWS SDK v2.
 *
 * Expected credentials map keys:
 *   "access_key_id"     — AWS access key ID
 *   "secret_access_key" — AWS secret access key
 *   "region"            — AWS region (e.g. "us-east-1"); defaults to us-east-1
 *   "session_token"     — (optional) for STS/assumed-role credentials
 *
 * The reference parameter must be in the format "bucket-name/object-key"
 * (e.g. "my-docs-bucket/2023/tax-return.pdf").
 *
 * TODO(phase-2): add support for STS assumed-role via session_token credential
 * TODO(phase-2): add support for IAM instance profile (no credentials in map → use default provider chain)
 */
@Slf4j
@Component
public class S3Connector implements SourceConnector {

    @Override
    public DocumentPayload fetch(String reference, Map<String, String> credentials) {
        String[] parts = reference.split("/", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "S3 reference must be in format 'bucket/key', got: [redacted]");
        }

        String bucket = parts[0];
        String key = parts[1];
        String region = credentials != null ? credentials.getOrDefault("region", "us-east-1") : "us-east-1";

        log.info("S3Connector fetching object from bucket=[{}] region=[{}]", bucket, region);

        S3Client s3 = buildS3Client(credentials, region);

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try (InputStream stream = s3.getObject(getRequest)) {
            byte[] bytes = stream.readAllBytes();
            String docId = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
            return new DocumentPayload(docId, reference, bytes, DocumentSource.S3);
        } catch (Exception e) {
            throw new RuntimeException("S3 fetch failed for bucket=[" + bucket + "]", e);
        }
    }

    private S3Client buildS3Client(Map<String, String> credentials, String region) {
        if (credentials != null
                && credentials.containsKey("access_key_id")
                && credentials.containsKey("secret_access_key")) {

            AwsBasicCredentials awsCreds = AwsBasicCredentials.create(
                    credentials.get("access_key_id"),
                    credentials.get("secret_access_key")
            );
            return S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                    .build();
        }

        // TODO(phase-2): fall back to DefaultCredentialsProvider for IAM instance profile / ECS task role
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
