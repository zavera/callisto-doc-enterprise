package io.callistotech.enterprise.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "checkpoints")
@Getter
@Setter
public class CheckpointEntity {

    /** jobId is the primary key — one checkpoint row per batch job */
    @Id
    @Column(name = "job_id", length = 36)
    private String jobId;

    @Column(name = "last_completed_document_id", nullable = false, length = 500)
    private String lastCompletedDocumentId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
