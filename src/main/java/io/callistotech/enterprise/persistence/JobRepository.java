package io.callistotech.enterprise.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {

    List<JobEntity> findByClientIdOrderByCreatedAtDesc(UUID clientId);
}
