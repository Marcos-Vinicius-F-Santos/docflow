package com.dockflow.dockflow.document;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndTenantId(UUID documentId, UUID tenantId);

    java.util.List<Document> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<Document> findByIdAndVisibility(UUID documentId, DocumentVisibility visibility);

    @Query("""
        select d
          from Document d
         where d.id = :documentId
           and (d.visibility = com.dockflow.dockflow.document.DocumentVisibility.PUBLIC
                or d.ownerSubject = :ownerSubject)
        """)
    Optional<Document> findReadableById(
        @Param("documentId") UUID documentId,
        @Param("ownerSubject") String ownerSubject
    );

    java.util.List<Document> findAllByVisibilityOrderByCreatedAtDesc(
        DocumentVisibility visibility
    );

    @Query("""
        select d
          from Document d
         where d.visibility = com.dockflow.dockflow.document.DocumentVisibility.PUBLIC
            or d.ownerSubject = :ownerSubject
         order by d.createdAt desc
        """)
    java.util.List<Document> findReadableByOwnerSubjectOrderByCreatedAtDesc(
        @Param("ownerSubject") String ownerSubject
    );

    long countByStatus(DocumentStatus status);

    @Query("select count(d) from Document d where d.status = com.dockflow.dockflow.document.DocumentStatus.PROCESSING and d.reconciliationNextAttemptAt is not null")
    long countResultUnknown();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :documentId")
    Optional<Document> findByIdForUpdate(@Param("documentId") UUID documentId);

    /**
     * Locks a document only when it is eligible for one explicit retry. The
     * staging existence check remains an application/storage concern because
     * PostgreSQL must not access MinIO.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select d
          from Document d
         where d.id = :documentId
           and d.status = :status
           and d.failureCount < :maxFailures
           and d.excluded = false
        """)
    Optional<Document> findRetryCandidateForUpdate(
        @Param("documentId") UUID documentId,
        @Param("status") DocumentStatus status,
        @Param("maxFailures") long maxFailures
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :documentId and d.tenantId = :tenantId")
    Optional<Document> findByIdForUpdateInTenant(
        @Param("documentId") UUID documentId,
        @Param("tenantId") UUID tenantId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select d
          from Document d
         where d.id = :documentId
           and d.tenantId = :tenantId
           and d.status = :status
           and d.failureCount < :maxFailures
           and d.excluded = false
        """)
    Optional<Document> findRetryCandidateForUpdateInTenant(
        @Param("documentId") UUID documentId,
        @Param("tenantId") UUID tenantId,
        @Param("status") DocumentStatus status,
        @Param("maxFailures") long maxFailures
    );

    default Optional<Document> findRetryCandidateForUpdate(UUID documentId) {
        return findRetryCandidateForUpdate(
            documentId,
            DocumentStatus.FAILED,
            Document.MAX_PROCESSING_FAILURES
        );
    }

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

    /** Claims one due document marked for delayed deletion for this transaction. */
    @Query(value = """
        SELECT *
          FROM documents
         WHERE is_excluded = TRUE
           AND exclusion_scheduled_at IS NOT NULL
           AND exclusion_scheduled_at <= :now
         ORDER BY exclusion_scheduled_at, id
         LIMIT 1
         FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<Document> claimNextScheduledDeletion(@Param("now") Instant now);

    /** Locks one due deletion message's document, or returns empty when it is stale. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select d
          from Document d
         where d.id = :documentId
           and d.excluded = true
           and d.exclusionScheduledAt is not null
           and d.exclusionScheduledAt <= :now
        """)
    Optional<Document> findScheduledDeletionForUpdate(
        @Param("documentId") UUID documentId,
        @Param("now") Instant now
    );
}
