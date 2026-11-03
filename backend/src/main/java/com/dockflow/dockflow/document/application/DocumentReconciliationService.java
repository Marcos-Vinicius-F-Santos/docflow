package com.dockflow.dockflow.document.application;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentRepository;
import com.dockflow.dockflow.document.exception.DocumentNotFoundException;
import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEventMapper;
import com.dockflow.dockflow.document.storage.DocumentStorage;
import com.dockflow.dockflow.document.application.logging.ApplicationLogContext;
import com.dockflow.dockflow.document.application.logging.ApplicationLogEventFactory;
import com.dockflow.dockflow.document.application.logging.ApplicationLogLevel;
import com.dockflow.dockflow.document.application.logging.ApplicationLogRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

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

    private static final Logger log = LoggerFactory.getLogger(DocumentReconciliationService.class);

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final long maxAttempts;
    private final Clock clock;
    private final List<Duration> backoff;
    private final Optional<DocumentLifecycleEventPublisher> lifecycleEventPublisher;
    private final DocumentLifecycleEventMapper lifecycleEventMapper;
    private final Optional<ApplicationLogRecorder> operationalLogRecorder;
    private final ApplicationLogEventFactory operationalLogEventFactory;

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
        this(
            documentRepository,
            documentStorage,
            maxAttempts,
            clock,
            backoff,
            Optional.empty(),
            Optional.empty()
        );
    }

    public DocumentReconciliationService(
        DocumentRepository documentRepository,
        DocumentStorage documentStorage,
        long maxAttempts,
        Clock clock,
        List<Duration> backoff,
        Optional<DocumentLifecycleEventPublisher> lifecycleEventPublisher
    ) {
        this(
            documentRepository,
            documentStorage,
            maxAttempts,
            clock,
            backoff,
            lifecycleEventPublisher,
            Optional.empty()
        );
    }

    public DocumentReconciliationService(
        DocumentRepository documentRepository,
        DocumentStorage documentStorage,
        long maxAttempts,
        Clock clock,
        List<Duration> backoff,
        Optional<DocumentLifecycleEventPublisher> lifecycleEventPublisher,
        Optional<ApplicationLogRecorder> operationalLogRecorder
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
        this.lifecycleEventPublisher = lifecycleEventPublisher;
        this.lifecycleEventMapper = new DocumentLifecycleEventMapper();
        this.operationalLogRecorder = operationalLogRecorder == null ? Optional.empty() : operationalLogRecorder;
        this.operationalLogEventFactory = new ApplicationLogEventFactory(clock);
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
        record(
            ApplicationLogLevel.INFO,
            "document.reconciliation.started",
            "Document reconciliation started",
            document.getId(),
            Map.of("attempt", document.getReconciliationAttempts())
        );

        if (documentStorage.exists(document.getObjectKey())) {
            document.markCompleted(document.getObjectKey());
            record(ApplicationLogLevel.INFO, "document.reconciliation.completed", "Reconciliation confirmed storage", document.getId(), Map.of());
        } else if (document.getReconciliationAttempts() >= maxAttempts) {
            document.markFailed(now);
            record(ApplicationLogLevel.ERROR, "document.reconciliation.failed", "Reconciliation exhausted attempts", document.getId(), Map.of());
        } else {
            int nextBackoffIndex = Math.toIntExact(document.getReconciliationAttempts() - 1);
            document.scheduleReconciliation(now.plus(backoff.get(nextBackoffIndex)));
            record(
                ApplicationLogLevel.WARN,
                "document.reconciliation.retry",
                "Reconciliation scheduled for another attempt",
                document.getId(),
                Map.of("attempt", document.getReconciliationAttempts())
            );
        }

        documentRepository.save(document);
        if (document.getStatus() == com.dockflow.dockflow.document.DocumentStatus.COMPLETED) {
            documentRepository.flush();
            publishCompleted(document, now);
        } else if (document.getStatus() == com.dockflow.dockflow.document.DocumentStatus.FAILED) {
            documentRepository.flush();
            publishFailed(document, now);
        }
    }

    private void publishCompleted(Document document, Instant occurredAt) {
        lifecycleEventPublisher.ifPresent(publisher -> {
            try {
                publisher.publish(lifecycleEventMapper.completed(document, occurredAt));
            } catch (RuntimeException exception) {
                log.atWarn().addKeyValue("documentId", document.getId()).setCause(exception)
                    .log("reconciled document completed notification could not be published");
                record(ApplicationLogLevel.WARN, "notification.lifecycle.publish", "Reconciled completion notification could not be published", document.getId(), Map.of());
            }
        });
    }

    private void publishFailed(Document document, Instant occurredAt) {
        lifecycleEventPublisher.ifPresent(publisher -> {
            try {
                publisher.publish(lifecycleEventMapper.failed(document, occurredAt));
            } catch (RuntimeException exception) {
                log.atWarn().addKeyValue("documentId", document.getId()).setCause(exception)
                    .log("reconciled document failed notification could not be published");
                record(ApplicationLogLevel.WARN, "notification.lifecycle.publish", "Reconciled failure notification could not be published", document.getId(), Map.of());
            }
        });
    }

    private void record(
        ApplicationLogLevel level,
        String operation,
        String message,
        UUID documentId,
        Map<String, ?> details
    ) {
        operationalLogRecorder.ifPresent(recorder -> recorder.record(operationalLogEventFactory.create(
            level,
            "document-api",
            "document-reconciliation",
            operation,
            message,
            new ApplicationLogContext(null, documentId),
            details
        )));
    }
}
