package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.DocumentNotificationDispatcher;
import com.dockflow.dockflow.document.application.logging.ApplicationLogContext;
import com.dockflow.dockflow.document.application.logging.ApplicationLogEventFactory;
import com.dockflow.dockflow.document.application.logging.ApplicationLogLevel;
import com.dockflow.dockflow.document.application.logging.ApplicationLogRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Provider-neutral consumer core for lifecycle notifications. */
public class DocumentNotificationMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentNotificationMessageConsumer.class);

    private final Optional<DocumentNotificationDispatcher> dispatcher;
    private final Optional<ApplicationLogRecorder> operationalLogRecorder;
    private final ApplicationLogEventFactory operationalLogEventFactory;
    private final Set<UUID> dispatchedEventIds = ConcurrentHashMap.newKeySet();

    public DocumentNotificationMessageConsumer(Optional<DocumentNotificationDispatcher> dispatcher) {
        this(dispatcher, Optional.empty());
    }

    public DocumentNotificationMessageConsumer(
        Optional<DocumentNotificationDispatcher> dispatcher,
        Optional<ApplicationLogRecorder> operationalLogRecorder
    ) {
        this.dispatcher = dispatcher;
        this.operationalLogRecorder = operationalLogRecorder == null ? Optional.empty() : operationalLogRecorder;
        this.operationalLogEventFactory = new ApplicationLogEventFactory();
    }

    public ProcessingResult process(DocumentLifecycleEvent event) {
        validate(event);
        if (!dispatchedEventIds.add(event.eventId())) {
            log.atDebug().addKeyValue("eventId", event.eventId()).log("duplicate notification acknowledged");
            return ProcessingResult.acknowledged(event.eventId());
        }

        try {
            DocumentNotificationDispatcher target = dispatcher.orElse(null);
            if (target == null) {
                dispatchedEventIds.remove(event.eventId());
                log.atWarn().addKeyValue("eventId", event.eventId())
                    .log("notification dispatcher is unavailable; message sent to dead letter");
                record(ApplicationLogLevel.ERROR, "notification.dispatch", "Notification dispatcher unavailable", event.documentId(), event.eventId());
                return ProcessingResult.deadLetter(event.eventId());
            }
            target.dispatch(event);
            record(ApplicationLogLevel.INFO, "notification.dispatch", "Document notification dispatched", event.documentId(), event.eventId());
            return ProcessingResult.acknowledged(event.eventId());
        } catch (RuntimeException exception) {
            dispatchedEventIds.remove(event.eventId());
            log.atWarn().addKeyValue("eventId", event.eventId()).setCause(exception)
                .log("notification dispatch failed; message sent to dead letter");
            record(ApplicationLogLevel.ERROR, "notification.dispatch", "Notification dispatch failed", event.documentId(), event.eventId());
            return ProcessingResult.deadLetter(event.eventId());
        }
    }

    private void record(ApplicationLogLevel level, String operation, String message, UUID documentId, UUID eventId) {
        operationalLogRecorder.ifPresent(recorder -> recorder.record(operationalLogEventFactory.create(
            level,
            "document-api",
            "notification-consumer",
            operation,
            message,
            new ApplicationLogContext(eventId, documentId),
            Map.of()
        )));
    }

    private void validate(DocumentLifecycleEvent event) {
        if (event == null) {
            throw new DocumentNotificationMessageValidationException("Notification event is required");
        }
        if (event.eventId() == null) {
            throw new DocumentNotificationMessageValidationException("eventId is required");
        }
        if (event.schemaVersion() != DocumentLifecycleEvent.SCHEMA_VERSION) {
            throw new DocumentNotificationMessageValidationException("Unsupported notification schema version");
        }
        if (event.type() == null) {
            throw new DocumentNotificationMessageValidationException("type is required");
        }
        if (event.documentId() == null) {
            throw new DocumentNotificationMessageValidationException("documentId is required");
        }
        if (event.occurredAt() == null) {
            throw new DocumentNotificationMessageValidationException("occurredAt is required");
        }
    }

    public record ProcessingResult(UUID eventId, Disposition disposition) {

        public static ProcessingResult acknowledged(UUID eventId) {
            return new ProcessingResult(eventId, Disposition.ACKNOWLEDGE);
        }

        public static ProcessingResult deadLetter(UUID eventId) {
            return new ProcessingResult(eventId, Disposition.DEAD_LETTER);
        }
    }

    public enum Disposition {
        ACKNOWLEDGE,
        DEAD_LETTER
    }
}
