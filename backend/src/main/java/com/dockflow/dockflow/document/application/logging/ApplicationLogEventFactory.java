package com.dockflow.dockflow.document.application.logging;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Creates valid events with one consistent clock and optional document context. */
@Component
public class ApplicationLogEventFactory {

    private final Clock clock;

    public ApplicationLogEventFactory() {
        this(Clock.systemUTC());
    }

    public ApplicationLogEventFactory(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public ApplicationLogEvent create(
        ApplicationLogLevel level,
        String service,
        String component,
        String operation,
        String message,
        ApplicationLogContext context,
        Map<String, ?> details
    ) {
        ApplicationLogContext safeContext = context == null ? ApplicationLogContext.empty() : context;
        return new ApplicationLogEvent(
            UUID.randomUUID(),
            ApplicationLogEvent.SCHEMA_VERSION,
            Instant.now(clock),
            level,
            service,
            component,
            operation,
            message,
            safeContext.correlationId(),
            safeContext.documentId(),
            details == null ? null : new java.util.LinkedHashMap<>(details)
        );
    }
}
