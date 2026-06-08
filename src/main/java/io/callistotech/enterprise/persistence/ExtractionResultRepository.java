package io.callistotech.enterprise.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExtractionResultRepository extends JpaRepository<ExtractionResultEntity, UUID> {

    Optional<ExtractionResultEntity> findByClientIdAndDocumentIdAndDocumentHash(
            UUID clientId, String documentId, String documentHash);

    List<ExtractionResultEntity> findByJobId(UUID jobId);
}
