package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.logging.ApplicationLogEvent;
import com.dockflow.dockflow.document.application.logging.ApplicationLogLevel;
import com.dockflow.dockflow.document.application.logging.ApplicationLogPersistenceService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApplicationLogMessageConsumerTest {

    @Test
    void shouldPersistTheEventBeforeTheHandlerCanAck() {
        ApplicationLogPersistenceService persistence = mock(ApplicationLogPersistenceService.class);
        ApplicationLogEvent event = event();
        org.mockito.Mockito.when(persistence.persist(event))
            .thenReturn(ApplicationLogPersistenceService.PersistenceResult.PERSISTED);

        ApplicationLogMessageConsumer consumer = new ApplicationLogMessageConsumer(persistence);

        assertEquals(ApplicationLogPersistenceService.PersistenceResult.PERSISTED, consumer.process(event));
        verify(persistence).persist(event);
    }

    @Test
    void shouldRejectInvalidEventsWithoutTouchingPersistence() {
        ApplicationLogPersistenceService persistence = mock(ApplicationLogPersistenceService.class);
        ApplicationLogMessageConsumer consumer = new ApplicationLogMessageConsumer(persistence);

        assertThrows(ApplicationLogMessageValidationException.class, () -> consumer.process(null));
        org.mockito.Mockito.verifyNoInteractions(persistence);
    }

    private static ApplicationLogEvent event() {
        return new ApplicationLogEvent(
            UUID.fromString("44444444-4444-4444-4444-444444444444"),
            1,
            Instant.parse("2026-10-29T12:00:00Z"),
            ApplicationLogLevel.INFO,
            "document-api",
            "test",
            "test.operation",
            "safe message",
            null,
            null,
            null
        );
    }
}
