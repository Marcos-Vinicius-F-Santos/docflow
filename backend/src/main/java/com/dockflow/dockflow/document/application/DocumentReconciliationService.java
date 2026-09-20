package com.dockflow.dockflow.document.application;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentRepository;
import com.dockflow.dockflow.document.exception.DocumentNotFoundException;
import com.dockflow.dockflow.document.storage.DocumentStorage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

/**
 * Application use case for reconciling a document after an unknown storage result.
 *
 * The repository claim and the storage existence check run in one transaction.
 * Triggering this use case is provided by the internal scheduler or by an
 * explicitly invoked operational fallback.
 */
public class DocumentReconciliationService
    implements ReconcileUnknownStorageResultUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final long maxAttempts;
    private final Clock clock;
    private final List<Duration> backoff;

    private static final List<Duration> DEFAULT_BACKOFF = List.of(
        Duration.ofMinutes(1),
        Duration.ofMinutes(5),
        Duration.ofMinutes(15),
        Duration.ofMinutes(30),
        Duration.ofMinutes(60)
    );

    public DocumentReconciliationService(
        DocumentRepository documentRepository,
        DocumentStorage documentStorage,
        long maxAttempts
    ) {
        this(documentRepository, documentStorage, maxAttempts, Clock.systemUTC());
    }

    public DocumentReconciliationService(
        DocumentRepository documentRepository,
        DocumentStorage documentStorage,
        long maxAttempts,
        Clock clock
    ) {
        this(documentRepository, documentStorage, maxAttempts, clock, DEFAULT_BACKOFF);
    }

    public DocumentReconciliationService(
        DocumentRepository documentRepository,
        DocumentStorage documentStorage,
        long maxAttempts,
        Clock clock,
        List<Duration> backoff
    ) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (maxAttempts > 5) {
            throw new IllegalArgumentException("maxAttempts cannot exceed five");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock cannot be null");
        }
        if (backoff == null || backoff.size() < maxAttempts || backoff.stream().anyMatch(this::isInvalidBackoff)) {
            throw new IllegalArgumentException("backoff must contain a positive delay for every reconciliation attempt");
        }

        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.maxAttempts = maxAttempts;
        this.clock = clock;
        this.backoff = List.copyOf(new ArrayList<>(backoff));
    }

    private boolean isInvalidBackoff(Duration delay) {
        return delay == null || delay.isZero() || delay.isNegative();
    }

    @Override
    @Transactional
    public void reconcile(UUID documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId cannot be null");
        }

        Instant now = clock.instant();
        Document document = documentRepository.claimReconciliation(documentId, now, maxAttempts)
            .orElse(null);

        if (document == null) {
            if (documentRepository.findById(documentId).isEmpty()) {
                throw new DocumentNotFoundException(documentId);
            }
            return;
        }

        reconcileClaimedDocument(document, now);
    }

    @Override
    @Transactional
    public boolean reconcileNextEligible() {
        Instant now = clock.instant();
        Document document = documentRepository
            .claimNextReconciliation(now, maxAttempts)
            .orElse(null);

        if (document == null) {
            return false;
        }

        reconcileClaimedDocument(document, now);
        return true;
    }

    private void reconcileClaimedDocument(Document document, Instant now) {
        document.beginReconciliationAttempt();

        if (documentStorage.exists(document.getObjectKey())) {
            document.markCompleted(document.getObjectKey());
        } else if (document.getReconciliationAttempts() >= maxAttempts) {
            document.markFailed();
        } else {
            int nextBackoffIndex = Math.toIntExact(document.getReconciliationAttempts() - 1);
            document.scheduleReconciliation(now.plus(backoff.get(nextBackoffIndex)));
        }

        documentRepository.save(document);
    }
}
