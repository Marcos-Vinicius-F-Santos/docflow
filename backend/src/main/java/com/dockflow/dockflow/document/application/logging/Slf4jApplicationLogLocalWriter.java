package com.dockflow.dockflow.document.application.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

/** Writes the already-sanitized event as a structured SLF4J record. */
@Component
public class Slf4jApplicationLogLocalWriter implements ApplicationLogLocalWriter {

    private static final Logger LOG = LoggerFactory.getLogger("com.dockflow.application-log");

    @Override
    public void write(ApplicationLogEvent event) {
        if (event == null) {
            return;
        }

        LoggingEventBuilder builder = switch (event.level()) {
            case INFO -> LOG.atInfo();
            case WARN -> LOG.atWarn();
            case ERROR -> LOG.atError();
        };
        builder.addKeyValue("eventId", event.eventId())
            .addKeyValue("schemaVersion", event.schemaVersion())
            .addKeyValue("occurredAt", event.occurredAt())
            .addKeyValue("service", event.service())
            .addKeyValue("component", event.component())
            .addKeyValue("operation", event.operation())
            .addKeyValue("correlationId", event.correlationId())
            .addKeyValue("documentId", event.documentId())
            .addKeyValue("details", event.details())
            .log(event.message());
    }
}
