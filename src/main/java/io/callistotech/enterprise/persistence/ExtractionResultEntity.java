package io.callistotech.enterprise.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "extraction_results",
        indexes = {
                @Index(name = "idx_er_job_id", columnList = "job_id"),
                @Index(name = "idx_er_client_doc_hash", columnList = "client_id,document_id,document_hash")
        })
@Getter
@Setter
public class ExtractionResultEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "document_id", nullable = false, length = 500)
    private String documentId;

    /** SHA-256 of the raw document bytes. Used for cache hit detection. */
    @Column(name = "document_hash", nullable = false, length = 64)
    private String documentHash;

    @Column(name = "field_map_name", nullable = false, length = 100)
    private String fieldMapName;

    /** JSON-serialised List<ExtractedField> */
    @Column(name = "result_json", columnDefinition = "TEXT", nullable = false)
    private String resultJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
