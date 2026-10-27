package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentRepository;
import com.dockflow.dockflow.document.storage.DocumentStorage;
import com.dockflow.dockflow.document.storage.DocumentStorageException;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Provider-neutral cleanup core for the delayed deletion queue. */
public class DocumentDeletionMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentDeletionMessageConsumer.class);

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final DocumentStagingRemover stagingRemover;
    private final Clock clock;

    public DocumentDeletionMessageConsumer(
        DocumentRepository documentRepository,
        DocumentStorage documentStorage,
        DocumentStagingRemover stagingRemover
    ) {
        this(documentRepository, documentStorage, stagingRemover, Clock.systemUTC());
    }

    public DocumentDeletionMessageConsumer(
        DocumentRepository documentRepository,
        DocumentStorage documentStorage,
        DocumentStagingRemover stagingRemover,
        Clock clock
    ) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.stagingRemover = stagingRemover;
        this.clock = clock;
    }

    @Transactional
    public ProcessingResult process(DocumentDeletionScheduledMessage message) {
        validate(message);
        UUID documentId = message.documentId();
        Instant now = Instant.now(clock);
        Optional<Document> due = documentRepository.findScheduledDeletionForUpdate(documentId, now);
        if (due.isEmpty()) {
            if (documentRepository.findById(documentId).isEmpty()) {
                log.atInfo().addKeyValue("documentId", documentId)
                    .log("document deletion duplicate already absent");
                return ProcessingResult.acknowledged(documentId);
            }
            log.atWarn().addKeyValue("documentId", documentId)
                .log("document deletion message is stale or not eligible");
            return ProcessingResult.deadLetter(documentId);
        }

        Document document = due.get();
        String finalObjectKey = document.getObjectKey() == null || document.getObjectKey().isBlank()
            ? "documents/" + documentId
            : document.getObjectKey();
        String stagingReference = "staging/" + documentId;

        try {
            documentStorage.delete(finalObjectKey);
            stagingRemover.delete(stagingReference);
            documentRepository.delete(document);
            documentRepository.flush();
            log.atInfo().addKeyValue("documentId", documentId).log("document deletion completed");
            return ProcessingResult.acknowledged(documentId);
        } catch (DocumentStorageException exception) {
            log.atWarn().addKeyValue("documentId", documentId).setCause(exception)
                .log("document deletion failed in storage");
            return ProcessingResult.deadLetter(documentId);
        }
    }

    private void validate(DocumentDeletionScheduledMessage message) {
        if (message == null) {
            throw new DocumentDeletionMessageValidationException("Message is required");
        }
        if (message.schemaVersion() != DocumentStorageRabbitTopology.DELETION_MESSAGE_SCHEMA_VERSION) {
            throw new DocumentDeletionMessageValidationException("Unsupported deletion message schema version");
        }
        if (message.documentId() == null) {
            throw new DocumentDeletionMessageValidationException("documentId is required");
        }
    }

    public record ProcessingResult(UUID documentId, Disposition disposition) {

        public static ProcessingResult acknowledged(UUID documentId) {
            return new ProcessingResult(documentId, Disposition.ACKNOWLEDGE);
        }

        public static ProcessingResult deadLetter(UUID documentId) {
            return new ProcessingResult(documentId, Disposition.DEAD_LETTER);
        }
    }

    public enum Disposition {
        ACKNOWLEDGE,
        DEAD_LETTER
    }
}
