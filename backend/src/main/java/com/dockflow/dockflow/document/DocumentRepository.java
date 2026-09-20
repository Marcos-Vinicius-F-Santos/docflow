package com.dockflow.dockflow.document;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /**
     * Atomically claims a document for one processing attempt.
     *
     * <p>The existing {@code updated_at} column is the lease timestamp and the
     * existing {@code version} column is advanced in the same conditional
     * update. A result of {@code 1} means that this caller owns the attempt;
     * {@code 0} means that another caller owns a valid lease, the document is
     * already completed, or the document does not exist.</p>
     *
     * <p>The caller must pass {@code leaseCutoff = now - 10 seconds} and a
     * single {@code claimedAt} instant for the attempt.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
        UPDATE documents
           SET status = 'PROCESSING',
               object_key = COALESCE(object_key, 'documents/' || id::text),
               updated_at = :claimedAt,
               version = version + 1
         WHERE id = :documentId
           AND (
                status = 'PENDING'
                OR (status = 'PROCESSING' AND updated_at <= :leaseCutoff)
           )
        """, nativeQuery = true)
    int claimForProcessing(
        @Param("documentId") UUID documentId,
        @Param("leaseCutoff") Instant leaseCutoff,
        @Param("claimedAt") Instant claimedAt
    );

    /**
     * Claims using the feature's approved ten-second processing lease.
     */
    default int claimForProcessing(UUID documentId, Instant now) {
        return claimForProcessing(
            documentId,
            now.minus(Document.PROCESSING_LEASE),
            now
        );
    }

    /**
     * Claims one due reconciliation attempt while keeping the row lock for the
     * caller's transaction. The lock is intentionally separate from the
     * ten-second RabbitMQ processing lease in {@code updated_at}.
     */
    @Query(value = """
        SELECT *
          FROM documents
         WHERE id = :documentId
           AND status = 'PROCESSING'
           AND object_key IS NOT NULL
           AND reconciliation_next_attempt_at IS NOT NULL
           AND reconciliation_next_attempt_at <= :now
           AND reconciliation_attempts < :maxAttempts
         FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<Document> claimReconciliation(
        @Param("documentId") UUID documentId,
        @Param("now") Instant now,
        @Param("maxAttempts") long maxAttempts
    );

    /**
     * Claims the next due reconciliation candidate for a scheduler cycle.
     */
    @Query(value = """
        SELECT *
          FROM documents
         WHERE status = 'PROCESSING'
           AND object_key IS NOT NULL
           AND reconciliation_next_attempt_at IS NOT NULL
           AND reconciliation_next_attempt_at <= :now
           AND reconciliation_attempts < :maxAttempts
         ORDER BY reconciliation_next_attempt_at, id
         LIMIT 1
         FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<Document> claimNextReconciliation(
        @Param("now") Instant now,
        @Param("maxAttempts") long maxAttempts
    );
}
