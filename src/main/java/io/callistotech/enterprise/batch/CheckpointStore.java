package io.callistotech.enterprise.batch;

import io.callistotech.enterprise.persistence.CheckpointEntity;
import io.callistotech.enterprise.persistence.CheckpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Persists resume checkpoints so a failed batch job can continue from the last successfully
 * completed document without re-processing already completed work.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckpointStore {

    private final CheckpointRepository checkpointRepository;

    /**
     * Records the last successfully completed documentId for a batch job.
     * Upserts — creates on first call, updates on subsequent calls.
     *
     * @param jobId                  batch job identifier
     * @param lastCompletedDocumentId documentId of the most recently completed document
     */
    @Transactional
    public void save(String jobId, String lastCompletedDocumentId) {
        CheckpointEntity entity = checkpointRepository.findById(jobId)
                .orElseGet(() -> {
                    CheckpointEntity e = new CheckpointEntity();
                    e.setJobId(jobId);
                    return e;
                });

        entity.setLastCompletedDocumentId(lastCompletedDocumentId);
        entity.setUpdatedAt(Instant.now());
        checkpointRepository.save(entity);
        log.debug("Checkpoint saved: job=[{}] lastDoc=[{}]", jobId, lastCompletedDocumentId);
    }

    /**
     * Returns the last completed documentId for a job, if a checkpoint exists.
     *
     * @param jobId batch job identifier
     * @return last completed documentId, or empty if no checkpoint found (new job)
     */
    @Transactional(readOnly = true)
    public Optional<String> findLastCompleted(String jobId) {
        return checkpointRepository.findById(jobId)
                .map(CheckpointEntity::getLastCompletedDocumentId);
    }

    /**
     * Deletes the checkpoint for a completed or abandoned job.
     *
     * @param jobId batch job identifier
     */
    @Transactional
    public void clear(String jobId) {
        checkpointRepository.deleteById(jobId);
        log.debug("Checkpoint cleared for job=[{}]", jobId);
    }
}
