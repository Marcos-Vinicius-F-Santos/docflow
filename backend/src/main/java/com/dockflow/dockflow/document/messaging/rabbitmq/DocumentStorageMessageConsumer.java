package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentRepository;
import com.dockflow.dockflow.document.DocumentStatus;
import com.dockflow.dockflow.document.application.DocumentDeletionPublisher;
import com.dockflow.dockflow.document.application.DocumentLifecycleEventPublisher;
import com.dockflow.dockflow.document.application.logging.ApplicationLogContext;
import com.dockflow.dockflow.document.application.logging.ApplicationLogEventFactory;
import com.dockflow.dockflow.document.application.logging.ApplicationLogLevel;
import com.dockflow.dockflow.document.application.logging.ApplicationLogRecorder;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingReader;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import com.dockflow.dockflow.document.storage.DocumentStorage;
import com.dockflow.dockflow.document.storage.DocumentStorageException;
import com.dockflow.dockflow.document.observability.DocumentMetrics;
import com.dockflow.dockflow.document.observability.CorrelationIdContext;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import java.util.Map;

/**
 * Provider-neutral processing core for a document-storage message.
 * RabbitMQ acknowledgment and retry routing remain in the adapter boundary.
 */
public class DocumentStorageMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageMessageConsumer.class);

    private final DocumentRepository documentRepository;
    private final DocumentStagingReader stagingReader;
    private final DocumentStagingRemover stagingRemover;
    private final DocumentStorage documentStorage;
    private final Optional<DocumentDeletionPublisher> deletionPublisher;
    private final Optional<DocumentLifecycleEventPublisher> lifecycleEventPublisher;
    private final DocumentLifecycleEventMapper lifecycleEventMapper;
    private final Clock clock;
    private final Optional<ApplicationLogRecorder> operationalLogRecorder;
    private final ApplicationLogEventFactory operationalLogEventFactory;
    private Optional<DocumentMetrics> metrics = Optional.empty();

    public DocumentStorageMessageConsumer(
        DocumentRepository documentRepository,
        DocumentStagingReader stagingReader,
        DocumentStagingRemover stagingRemover,
        DocumentStorage documentStorage
    ) {
        this(
            documentRepository,
            stagingReader,
            stagingRemover,
            documentStorage,
            Optional.empty(),
            Optional.empty(),
            Clock.systemUTC(),
            Optional.empty()
        );
    }

    public DocumentStorageMessageConsumer(
        DocumentRepository documentRepository,
        DocumentStagingReader stagingReader,
        DocumentStagingRemover stagingRemover,
        DocumentStorage documentStorage,
        Optional<DocumentDeletionPublisher> deletionPublisher
    ) {
        this(
            documentRepository,
            stagingReader,
            stagingRemover,
            documentStorage,
            deletionPublisher,
            Optional.empty(),
            Clock.systemUTC(),
            Optional.empty()
        );
    }

    public DocumentStorageMessageConsumer(
        DocumentRepository documentRepository,
        DocumentStagingReader stagingReader,
        DocumentStagingRemover stagingRemover,
        DocumentStorage documentStorage,
        Clock clock
    ) {
        this(
            documentRepository,
            stagingReader,
            stagingRemover,
            documentStorage,
            Optional.empty(),
            Optional.empty(),
            clock,
            Optional.empty()
        );
    }

    public DocumentStorageMessageConsumer(
        DocumentRepository documentRepository,
        DocumentStagingReader stagingReader,
        DocumentStagingRemover stagingRemover,
        DocumentStorage documentStorage,
        Optional<DocumentDeletionPublisher> deletionPublisher,
        Clock clock
    ) {
        this(
            documentRepository,
            stagingReader,
            stagingRemover,
            documentStorage,
            deletionPublisher,
            Optional.empty(),
            clock,
            Optional.empty()
        );
    }

    public DocumentStorageMessageConsumer(
        DocumentRepository documentRepository,
        DocumentStagingReader stagingReader,
        DocumentStagingRemover stagingRemover,
        DocumentStorage documentStorage,
        Optional<DocumentDeletionPublisher> deletionPublisher,
        Optional<DocumentLifecycleEventPublisher> lifecycleEventPublisher,
        Clock clock
    ) {
        this(
            documentRepository,
            stagingReader,
            stagingRemover,
            documentStorage,
            deletionPublisher,
            lifecycleEventPublisher,
            clock,
            Optional.empty()
        );
    }

    public DocumentStorageMessageConsumer(
        DocumentRepository documentRepository,
        DocumentStagingReader stagingReader,
        DocumentStagingRemover stagingRemover,
        DocumentStorage documentStorage,
        Optional<DocumentDeletionPublisher> deletionPublisher,
        Optional<DocumentLifecycleEventPublisher> lifecycleEventPublisher,
        Clock clock,
        Optional<ApplicationLogRecorder> operationalLogRecorder
    ) {
        this.documentRepository = documentRepository;
        this.stagingReader = stagingReader;
        this.stagingRemover = stagingRemover;
        this.documentStorage = documentStorage;
        this.deletionPublisher = deletionPublisher;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
        this.lifecycleEventMapper = new DocumentLifecycleEventMapper();
        this.clock = clock;
        this.operationalLogRecorder = operationalLogRecorder == null ? Optional.empty() : operationalLogRecorder;
        this.operationalLogEventFactory = new ApplicationLogEventFactory(clock);
    }

    @org.springframework.beans.factory.annotation.Autowired
    void configureMetrics(DocumentMetrics documentMetrics) {
        this.metrics = Optional.of(documentMetrics);
    }

    @Transactional
    public ProcessingResult process(DocumentStorageRequestedMessage message) {
        validate(message);
        Timer.Sample processingTimer = metrics.map(DocumentMetrics::startProcessingTimer).orElse(null);
        UUID documentId = message.documentId();
        record(ApplicationLogLevel.INFO, "document.processing.started", "Document processing started", documentId, Map.of());
        log.atInfo().addKeyValue("documentId", documentId).log("document storage consumption started");

        Instant claimedAt = Instant.now(clock);
        int claimed = documentRepository.claimForProcessing(documentId, claimedAt);
        if (claimed == 0) {
            Document current = documentRepository.findById(documentId).orElse(null);
            if (current != null && current.getStatus() == DocumentStatus.COMPLETED) {
                log.atInfo().addKeyValue("documentId", documentId)
                    .log("document storage duplicate already completed");
                record(ApplicationLogLevel.INFO, "document.processing.duplicate", "Document was already completed", documentId, Map.of());
                return measured(ProcessingResult.acknowledged(documentId), processingTimer, "duplicate");
            }

            log.atWarn().addKeyValue("documentId", documentId)
                .log("document storage claim disputed; retry required");
            record(ApplicationLogLevel.WARN, "document.processing.retry", "Document processing claim disputed", documentId, Map.of());
            return measured(ProcessingResult.retryKeepingProcessing(documentId), processingTimer, "claim_disputed");
        }

        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentStorageMessageValidationException(
                "Document does not exist: " + documentId
            ));

        publishProcessingStarted(document, claimedAt);

        try {
            if (documentStorage.exists(message.objectKey())) {
                document.markCompleted(message.objectKey());
                documentRepository.save(document);
                documentRepository.flush();
                publishCompleted(document);
                log.atInfo().addKeyValue("documentId", documentId)
                    .log("document storage duplicate final object already exists");
                record(ApplicationLogLevel.INFO, "document.processing.completed", "Document storage already existed", documentId, Map.of());
                return measured(ProcessingResult.acknowledged(documentId), processingTimer, "already_exists");
            }

            try (DocumentStagingReader.StagedContent staged = stagingReader.open(message.contentReference())) {
                documentStorage.store(
                    message.objectKey(),
                    staged.content(),
                    staged.sizeBytes(),
                    staged.contentType()
                );
                metrics.ifPresent(value -> value.storage("final_write", "success"));
                stagingRemover.delete(message.contentReference());
                document.markCompleted(message.objectKey());
                documentRepository.save(document);
                documentRepository.flush();
            }

            publishCompleted(document);

            log.atInfo().addKeyValue("documentId", documentId)
                .log("document storage consumption succeeded");
            record(ApplicationLogLevel.INFO, "document.processing.completed", "Document storage completed", documentId, Map.of());
            return measured(ProcessingResult.acknowledged(documentId), processingTimer, "completed");
        } catch (DocumentStorageMessageValidationException exception) {
            throw exception;
        } catch (DocumentStorageException exception) {
            log.atWarn().addKeyValue("documentId", documentId).setCause(exception)
                .log("document storage consumption failed");
            record(
                exception.getFailureType().isRetryable() ? ApplicationLogLevel.WARN : ApplicationLogLevel.ERROR,
                exception.getFailureType().isRetryable() ? "document.processing.retry" : "document.processing.failed",
                "Document storage operation failed",
                documentId,
                Map.of("failureType", exception.getFailureType().name())
            );
            if (!exception.getFailureType().isRetryable()) {
                markFailed(document);
                return measured(ProcessingResult.deadLetter(documentId), processingTimer, "dead_letter");
            }
            metrics.ifPresent(value -> value.storage("final_write", exception.getFailureType().name().toLowerCase()));
            return exception.getFailureType() == DocumentStorageException.FailureType.RESULT_UNKNOWN
                ? measured(ProcessingResult.retryKeepingProcessing(documentId), processingTimer, "result_unknown")
                : measured(ProcessingResult.retry(documentId), processingTimer, "retry");
        } catch (IOException exception) {
            log.atWarn().addKeyValue("documentId", documentId).setCause(exception)
                .log("staged content could not be read");
            record(ApplicationLogLevel.WARN, "document.processing.retry", "Staged content could not be read", documentId, Map.of());
            return measured(ProcessingResult.retryKeepingProcessing(documentId), processingTimer, "staging_read_retry");
        }
    }

    private ProcessingResult measured(ProcessingResult result, Timer.Sample timer, String outcome) {
        metrics.ifPresent(value -> {
            value.processing(outcome);
            if (timer != null) {
                value.stopProcessingTimer(timer, outcome);
            }
        });
        return result;
    }

    /** Persists a permanent processing failure before the message is dead-lettered. */
    @Transactional
    public void markFailedAfterPermanentFailure(UUID documentId) {
        if (documentId == null) {
            return;
        }

        documentRepository.findById(documentId).ifPresent(document -> {
            if (document.getStatus() == DocumentStatus.PENDING ||
                document.getStatus() == DocumentStatus.PROCESSING) {
                markFailed(document);
            }
        });
    }

    private void markFailed(Document document) {
        boolean schedulesDeletion = !document.isExcluded()
            && document.getFailureCount() == Document.MAX_PROCESSING_FAILURES - 1;
        document.markFailed(Instant.now(clock));
        if (schedulesDeletion) {
            documentRepository.saveAndFlush(document);
            publishFailed(document);
            publishDeletionWarningScheduled(document);
            deletionPublisher.ifPresent(publisher -> publisher.publish(
                new DocumentDeletionPublisher.DocumentDeletionScheduled(
                    DocumentStorageRabbitTopology.DELETION_MESSAGE_SCHEMA_VERSION,
                    document.getId()
                )
            ));
            record(
                ApplicationLogLevel.ERROR,
                "document.processing.failed",
                "Document processing failed and deletion was scheduled",
                document.getId(),
                Map.of("failureCount", document.getFailureCount())
            );
            return;
        }
        documentRepository.save(document);
        documentRepository.flush();
        publishFailed(document);
        record(
            ApplicationLogLevel.ERROR,
            "document.processing.failed",
            "Document processing permanently failed",
            document.getId(),
            Map.of("failureCount", document.getFailureCount())
        );
    }

    private void publishProcessingStarted(Document document, Instant occurredAt) {
        lifecycleEventPublisher.ifPresent(publisher -> {
            try {
                publisher.publish(lifecycleEventMapper.processingStarted(document, occurredAt));
            } catch (RuntimeException exception) {
                log.atWarn().addKeyValue("documentId", document.getId()).setCause(exception)
                    .log("document processing started notification could not be published");
                record(ApplicationLogLevel.WARN, "notification.lifecycle.publish", "Processing notification could not be published", document.getId(), Map.of());
            }
        });
    }

    private void publishCompleted(Document document) {
        lifecycleEventPublisher.ifPresent(publisher -> {
            try {
                publisher.publish(lifecycleEventMapper.completed(document, Instant.now(clock)));
            } catch (RuntimeException exception) {
                log.atWarn().addKeyValue("documentId", document.getId()).setCause(exception)
                    .log("document completed notification could not be published");
                record(ApplicationLogLevel.WARN, "notification.lifecycle.publish", "Completed notification could not be published", document.getId(), Map.of());
            }
        });
    }

    private void publishFailed(Document document) {
        lifecycleEventPublisher.ifPresent(publisher -> {
            try {
                publisher.publish(lifecycleEventMapper.failed(document, Instant.now(clock)));
            } catch (RuntimeException exception) {
                log.atWarn().addKeyValue("documentId", document.getId()).setCause(exception)
                    .log("document failed notification could not be published");
                record(ApplicationLogLevel.WARN, "notification.lifecycle.publish", "Failed notification could not be published", document.getId(), Map.of());
            }
        });
    }

    private void publishDeletionWarningScheduled(Document document) {
        lifecycleEventPublisher.ifPresent(publisher -> {
            try {
                Instant warningAt = document.getExclusionScheduledAt().minus(java.time.Duration.ofMinutes(30));
                publisher.scheduleDeletionWarning(lifecycleEventMapper.deletionWarning(document, warningAt));
            } catch (RuntimeException exception) {
                log.atWarn().addKeyValue("documentId", document.getId()).setCause(exception)
                    .log("document deletion warning could not be scheduled");
                record(ApplicationLogLevel.WARN, "notification.deletion-warning.publish", "Deletion warning could not be scheduled", document.getId(), Map.of());
            }
        });
    }

    private void validate(DocumentStorageRequestedMessage message) {
        if (message == null) {
            throw new DocumentStorageMessageValidationException("Message is required");
        }
        if (message.schemaVersion() != DocumentStorageRabbitTopology.MESSAGE_SCHEMA_VERSION) {
            throw new DocumentStorageMessageValidationException("Unsupported message schema version");
        }
        if (message.documentId() == null) {
            throw new DocumentStorageMessageValidationException("documentId is required");
        }
        if (isBlank(message.objectKey()) || isBlank(message.contentReference()) ||
            !message.contentReference().startsWith("staging/")) {
            throw new DocumentStorageMessageValidationException("Object references are invalid");
        }
        if (message.sizeBytes() < 0 || isBlank(message.contentType())) {
            throw new DocumentStorageMessageValidationException("Document metadata is invalid");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
            "document-storage-consumer",
            operation,
            message,
            new ApplicationLogContext(CorrelationIdContext.currentUuid(), documentId),
            details
        )));
    }

    public record ProcessingResult(
        UUID documentId,
        Disposition disposition,
        RetryExhaustionPolicy retryExhaustionPolicy
    ) {

        public static ProcessingResult acknowledged(UUID documentId) {
            return new ProcessingResult(documentId, Disposition.ACKNOWLEDGE, null);
        }

        public static ProcessingResult retry(UUID documentId) {
            return new ProcessingResult(
                documentId,
                Disposition.RETRY,
                RetryExhaustionPolicy.MARK_FAILED
            );
        }

        public static ProcessingResult retryKeepingProcessing(UUID documentId) {
            return new ProcessingResult(
                documentId,
                Disposition.RETRY,
                RetryExhaustionPolicy.KEEP_PROCESSING
            );
        }

        public static ProcessingResult deadLetter(UUID documentId) {
            return new ProcessingResult(documentId, Disposition.DEAD_LETTER, null);
        }

        public boolean shouldMarkFailedOnRetryExhaustion() {
            return retryExhaustionPolicy == RetryExhaustionPolicy.MARK_FAILED;
        }
    }

    public enum RetryExhaustionPolicy {
        MARK_FAILED,
        KEEP_PROCESSING
    }

    public enum Disposition {
        ACKNOWLEDGE,
        RETRY,
        DEAD_LETTER
    }
}
