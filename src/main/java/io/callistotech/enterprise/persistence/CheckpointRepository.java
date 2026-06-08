package io.callistotech.enterprise.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckpointRepository extends JpaRepository<CheckpointEntity, String> {
}
