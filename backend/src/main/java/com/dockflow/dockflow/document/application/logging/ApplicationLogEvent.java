package com.dockflow.dockflow.document.application.logging;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Immutable versioned operational event shared by local logging and RabbitMQ. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicationLogEvent(
    UUID eventId,
    int schemaVersion,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant occurredAt,
    ApplicationLogLevel level,
    String service,
    String component,
    String operation,
    String message,
    UUID correlationId,
    UUID documentId,
    Map<String, Object> details
) {

    public static final int SCHEMA_VERSION = 1;

    public ApplicationLogEvent {
        require(eventId, "eventId");
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported application log schema version");
        }
        require(occurredAt, "occurredAt");
        require(level, "level");
        requireText(service, "service");
        requireText(component, "component");
        requireText(operation, "operation");
        message = ApplicationLogSanitizer.sanitizeMessage(requireText(message, "message"));
        details = ApplicationLogSanitizer.sanitizeDetails(details);
        if (details.isEmpty()) {
            details = null;
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
