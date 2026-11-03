package com.dockflow.dockflow.document.application.logging;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationLogEntityTest {

    @Test
    void shouldMapTheImmutableEventToTheIndependentPersistenceModel() {
        ApplicationLogEvent event = new ApplicationLogEvent(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            1,
            Instant.parse("2026-10-29T12:00:00Z"),
            ApplicationLogLevel.WARN,
            "document-api",
            "test",
            "test.operation",
            "safe message",
            null,
            null,
            Map.of("code", "WARN")
        );

        ApplicationLogEntity entity = new ApplicationLogEntity(event);

        assertEquals(event.eventId(), entity.getEventId());
        assertEquals(event.occurredAt(), entity.getOccurredAt());
        assertEquals(event.details(), entity.getDetails());
        assertEquals(event, entity.toEvent());
    }
}
