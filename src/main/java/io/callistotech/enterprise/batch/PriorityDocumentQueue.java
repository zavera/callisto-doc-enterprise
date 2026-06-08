package io.callistotech.enterprise.batch;

import io.callistotech.enterprise.connector.DocumentPayload;
import io.callistotech.enterprise.domain.Priority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Thread-safe priority queue for document processing.
 * HIGH priority documents drain before NORMAL priority documents.
 *
 * Uses Java's PriorityBlockingQueue internally — safe for concurrent producers and consumers.
 */
@Slf4j
@Component
public class PriorityDocumentQueue {

    private final PriorityBlockingQueue<PrioritisedDocument> queue;

    public PriorityDocumentQueue() {
        // Compare by priority ordinal: HIGH (0) sorts before NORMAL (1)
        this.queue = new PriorityBlockingQueue<>(
                64,
                Comparator.comparingInt(pd -> pd.priority().ordinal())
        );
    }

    /**
     * Enqueues a document for processing.
     *
     * @param payload  the document to process
     * @param priority HIGH drains before NORMAL
     */
    public void enqueue(DocumentPayload payload, Priority priority) {
        queue.put(new PrioritisedDocument(payload, priority));
        log.debug("Enqueued doc=[{}] priority=[{}] queueSize=[{}]",
                payload.documentId(), priority, queue.size());
    }

    /**
     * Retrieves and removes the next document from the queue.
     * Blocks until a document is available.
     *
     * @return next document in priority order
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public PrioritisedDocument take() throws InterruptedException {
        return queue.take();
    }

    /** Non-blocking poll; returns null if the queue is empty. */
    public PrioritisedDocument poll() {
        return queue.poll();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Wraps a DocumentPayload with its processing priority.
     *
     * @param payload  the document
     * @param priority processing priority
     */
    public record PrioritisedDocument(DocumentPayload payload, Priority priority) {}
}
