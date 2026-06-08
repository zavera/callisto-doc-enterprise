package io.callistotech.enterprise.reconciliation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Summary of cross-document reconciliation findings for a batch job or single submission.
 *
 * @param jobId         batch job identifier
 * @param clientId      client UUID
 * @param discrepancies list of field-level discrepancies found across documents
 * @param generatedAt   timestamp when reconciliation was run
 */
public record ReconciliationReport(
        UUID jobId,
        UUID clientId,
        List<FieldDiscrepancy> discrepancies,
        Instant generatedAt
) {
    public boolean hasDiscrepancies() {
        return discrepancies != null && !discrepancies.isEmpty();
    }
}
