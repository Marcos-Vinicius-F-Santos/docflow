package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.logging.ApplicationLogEvent;
import com.dockflow.dockflow.document.application.logging.ApplicationLogPersistenceService;

/** Provider-neutral persistence core; it never mutates Document. */
public class ApplicationLogMessageConsumer {

    private final ApplicationLogPersistenceService persistenceService;

    public ApplicationLogMessageConsumer(ApplicationLogPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public ApplicationLogPersistenceService.PersistenceResult process(ApplicationLogEvent event) {
        validate(event);
        return persistenceService.persist(event);
    }

    private void validate(ApplicationLogEvent event) {
        if (event == null) {
            throw new ApplicationLogMessageValidationException("Application log event is required");
        }
        if (event.eventId() == null || event.occurredAt() == null || event.level() == null
            || event.service() == null || event.component() == null || event.operation() == null
            || event.message() == null) {
            throw new ApplicationLogMessageValidationException("Application log required fields are missing");
        }
        if (event.schemaVersion() != ApplicationLogEvent.SCHEMA_VERSION) {
            throw new ApplicationLogMessageValidationException("Unsupported application log schema version");
        }
    }
}
