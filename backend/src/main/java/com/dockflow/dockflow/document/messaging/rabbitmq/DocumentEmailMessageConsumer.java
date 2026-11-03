package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.adapter.out.email.EmailDeliveryException;
import com.dockflow.dockflow.document.application.logging.ApplicationLogContext;
import com.dockflow.dockflow.document.application.logging.ApplicationLogEventFactory;
import com.dockflow.dockflow.document.application.logging.ApplicationLogLevel;
import com.dockflow.dockflow.document.application.logging.ApplicationLogRecorder;
import com.dockflow.dockflow.document.port.out.email.DocumentEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Provider-neutral e-mail consumer with in-process duplicate suppression. */
public class DocumentEmailMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentEmailMessageConsumer.class);

    private final Optional<DocumentEmailSender> sender;
    private final Optional<ApplicationLogRecorder> operationalLogRecorder;
    private final ApplicationLogEventFactory operationalLogEventFactory;
    private final Set<UUID> sentEventIds = ConcurrentHashMap.newKeySet();

    public DocumentEmailMessageConsumer(Optional<DocumentEmailSender> sender) {
        this(sender, Optional.empty());
    }

    public DocumentEmailMessageConsumer(
        Optional<DocumentEmailSender> sender,
        Optional<ApplicationLogRecorder> operationalLogRecorder
    ) {
        this.sender = sender;
        this.operationalLogRecorder = operationalLogRecorder == null ? Optional.empty() : operationalLogRecorder;
        this.operationalLogEventFactory = new ApplicationLogEventFactory();
    }

    public ProcessingResult process(DocumentEmailNotificationMessage message) {
        validate(message);
        UUID eventId = message.event().eventId();
        if (!sentEventIds.add(eventId)) {
            log.atDebug().addKeyValue("eventId", eventId).log("duplicate e-mail command acknowledged");
            return ProcessingResult.acknowledged(eventId);
        }

        try {
            DocumentEmailSender target = sender.orElse(null);
            if (target == null) {
                sentEventIds.remove(eventId);
                record(ApplicationLogLevel.ERROR, "notification.email", "E-mail sender unavailable", message.event().documentId(), eventId);
                return ProcessingResult.permanentFailure(eventId);
            }
            target.send(message.event(), message.recipient());
            record(ApplicationLogLevel.INFO, "notification.email", "Document e-mail notification sent", message.event().documentId(), eventId);
            return ProcessingResult.acknowledged(eventId);
        } catch (EmailDeliveryException exception) {
            sentEventIds.remove(eventId);
            record(
                exception.failureType() == EmailDeliveryException.FailureType.TRANSIENT
                    ? ApplicationLogLevel.WARN : ApplicationLogLevel.ERROR,
                "notification.email",
                "Document e-mail notification failed",
                message.event().documentId(),
                eventId
            );
            return exception.failureType() == EmailDeliveryException.FailureType.TRANSIENT
                ? ProcessingResult.transientFailure(eventId)
                : ProcessingResult.permanentFailure(eventId);
        } catch (RuntimeException exception) {
            sentEventIds.remove(eventId);
            log.atWarn().addKeyValue("eventId", eventId).setCause(exception)
                .log("unexpected e-mail failure treated as transient");
            record(ApplicationLogLevel.WARN, "notification.email", "Unexpected e-mail failure", message.event().documentId(), eventId);
            return ProcessingResult.transientFailure(eventId);
        }
    }

    private void record(ApplicationLogLevel level, String operation, String message, UUID documentId, UUID eventId) {
        operationalLogRecorder.ifPresent(recorder -> recorder.record(operationalLogEventFactory.create(
            level,
            "document-api",
            "email-consumer",
            operation,
            message,
            new ApplicationLogContext(eventId, documentId),
            Map.of()
        )));
    }

    private void validate(DocumentEmailNotificationMessage message) {
        if (message == null) {
            throw new DocumentEmailMessageValidationException("E-mail command is required");
        }
        if (message.schemaVersion() != DocumentEmailNotificationMessage.SCHEMA_VERSION) {
            throw new DocumentEmailMessageValidationException("Unsupported e-mail command schema version");
        }
        if (message.event() == null) {
            throw new DocumentEmailMessageValidationException("event is required");
        }
        if (message.recipient() == null || message.recipient().isBlank()) {
            throw new DocumentEmailMessageValidationException("recipient is required");
        }
    }

    public record ProcessingResult(UUID eventId, Disposition disposition) {

        public static ProcessingResult acknowledged(UUID eventId) {
            return new ProcessingResult(eventId, Disposition.ACKNOWLEDGE);
        }

        public static ProcessingResult transientFailure(UUID eventId) {
            return new ProcessingResult(eventId, Disposition.RETRY);
        }

        public static ProcessingResult permanentFailure(UUID eventId) {
            return new ProcessingResult(eventId, Disposition.DEAD_LETTER);
        }
    }

    public enum Disposition {
        ACKNOWLEDGE,
        RETRY,
        DEAD_LETTER
    }
}
