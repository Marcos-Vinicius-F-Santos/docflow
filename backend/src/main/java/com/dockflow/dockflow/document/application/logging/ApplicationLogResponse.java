package com.dockflow.dockflow.document.application.logging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ApplicationLogResponse(
    UUID eventId,
    int schemaVersion,
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

    public static ApplicationLogResponse from(ApplicationLogEntity entity) {
        return new ApplicationLogResponse(
            entity.getEventId(),
            entity.getSchemaVersion(),
            entity.getOccurredAt(),
            entity.getLevel(),
            entity.getService(),
            entity.getComponent(),
            entity.getOperation(),
            entity.getMessage(),
            entity.getCorrelationId(),
            entity.getDocumentId(),
            entity.getDetails()
        );
    }
}
