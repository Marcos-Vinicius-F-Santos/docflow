package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentRepository;
import com.dockflow.dockflow.document.DocumentStatus;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingReader;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import com.dockflow.dockflow.document.storage.DocumentStorage;
import com.dockflow.dockflow.document.storage.DocumentStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

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
    private final Clock clock;

    public DocumentStorageMessageConsumer(
        DocumentRepository documentRepository,
        DocumentStagingReader stagingReader,
        DocumentStagingRemover stagingRemover,
        DocumentStorage documentStorage
    ) {
        this(documentRepository, stagingReader, stagingRemover, documentStorage, Clock.systemUTC());
    }

    public DocumentStorageMessageConsumer(
        DocumentRepository documentRepository,
        DocumentStagingReader stagingReader,
        DocumentStagingRemover stagingRemover,
        DocumentStorage documentStorage,
        Clock clock
    ) {
        this.documentRepository = documentRepository;
        this.stagingReader = stagingReader;
        this.stagingRemover = stagingRemover;
        this.documentStorage = documentStorage;
        this.clock = clock;
    }

    @Transactional
    public ProcessingResult process(DocumentStorageRequestedMessage message) {
        validate(message);
        UUID documentId = message.documentId();
        log.atInfo().addKeyValue("documentId", documentId).log("document storage consumption started");

        int claimed = documentRepository.claimForProcessing(documentId, Instant.now(clock));
        if (claimed == 0) {
            Document current = documentRepository.findById(documentId).orElse(null);
            if (current != null && current.getStatus() == DocumentStatus.COMPLETED) {
                log.atInfo().addKeyValue("documentId", documentId)
                    .log("document storage duplicate already completed");
                return ProcessingResult.acknowledged(documentId);
            }

            log.atWarn().addKeyValue("documentId", documentId)
                .log("document storage claim disputed; retry required");
            return ProcessingResult.retryKeepingProcessing(documentId);
        }

        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentStorageMessageValidationException(
                "Document does not exist: " + documentId
            ));

        try {
            if (documentStorage.exists(message.objectKey())) {
                document.markCompleted(message.objectKey());
                documentRepository.save(document);
                log.atInfo().addKeyValue("documentId", documentId)
                    .log("document storage duplicate final object already exists");
                return ProcessingResult.acknowledged(documentId);
            }

            try (DocumentStagingReader.StagedContent staged = stagingReader.open(message.contentReference())) {
                documentStorage.store(
                    message.objectKey(),
                    staged.content(),
                    staged.sizeBytes(),
                    staged.contentType()
                );
                stagingRemover.delete(message.contentReference());
                document.markCompleted(message.objectKey());
                documentRepository.save(document);
            }

            log.atInfo().addKeyValue("documentId", documentId)
                .log("document storage consumption succeeded");
            return ProcessingResult.acknowledged(documentId);
        } catch (DocumentStorageMessageValidationException exception) {
            throw exception;
        } catch (DocumentStorageException exception) {
            log.atWarn().addKeyValue("documentId", documentId).setCause(exception)
                .log("document storage consumption failed");
            if (!exception.getFailureType().isRetryable()) {
                markFailed(document);
                return ProcessingResult.deadLetter(documentId);
            }
            return exception.getFailureType() == DocumentStorageException.FailureType.RESULT_UNKNOWN
                ? ProcessingResult.retryKeepingProcessing(documentId)
                : ProcessingResult.retry(documentId);
        } catch (IOException exception) {
            log.atWarn().addKeyValue("documentId", documentId).setCause(exception)
                .log("staged content could not be read");
            return ProcessingResult.retryKeepingProcessing(documentId);
        }
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
        document.markFailed();
        documentRepository.save(document);
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
