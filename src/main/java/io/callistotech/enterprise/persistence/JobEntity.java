package io.callistotech.enterprise.persistence;

import io.callistotech.enterprise.domain.JobStatus;
import io.callistotech.enterprise.domain.Priority;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@Getter
@Setter
public class JobEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Column(name = "total_docs", nullable = false)
    private int totalDocs;

    @Column(name = "completed_docs", nullable = false)
    private int completedDocs;

    @Column(name = "failed_docs", nullable = false)
    private int failedDocs;

    @Column(name = "field_map_name", nullable = false, length = 100)
    private String fieldMapName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
