package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.DocumentStatus;
import com.dockflow.dockflow.document.application.DocumentNotificationDispatcher;
import com.dockflow.dockflow.document.application.logging.ApplicationLogEvent;
import com.dockflow.dockflow.document.application.logging.ApplicationLogLocalWriter;
import com.dockflow.dockflow.document.application.logging.ApplicationLogRecorder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DocumentOperationalLogEmissionTest {

    private static final UUID DOCUMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void shouldEmitInfoForSuccessfulNotificationDispatch() {
        AtomicReference<ApplicationLogEvent> captured = new AtomicReference<>();
        ApplicationLogRecorder recorder = recorder(captured);
        DocumentNotificationDispatcher dispatcher = ignored -> { };
        DocumentNotificationMessageConsumer consumer = new DocumentNotificationMessageConsumer(
            Optional.of(dispatcher),
            Optional.of(recorder)
        );

        consumer.process(DocumentLifecycleEvent.completed(DOCUMENT_ID, Instant.parse("2026-10-29T12:00:00Z")));

        assertEquals("notification.dispatch", captured.get().operation());
        assertEquals("INFO", captured.get().level().name());
        assertEquals(DOCUMENT_ID, captured.get().documentId());
    }

    @Test
    void shouldEmitErrorWhenNotificationDispatcherIsUnavailable() {
        AtomicReference<ApplicationLogEvent> captured = new AtomicReference<>();
        DocumentNotificationMessageConsumer consumer = new DocumentNotificationMessageConsumer(
            Optional.empty(),
            Optional.of(recorder(captured))
        );

        consumer.process(DocumentLifecycleEvent.failed(DOCUMENT_ID, Instant.parse("2026-10-29T12:00:00Z"), 1, null));

        assertEquals("notification.dispatch", captured.get().operation());
        assertEquals("ERROR", captured.get().level().name());
    }

    @Test
    void shouldEmitErrorWhenNotificationChannelFailsWithoutThrowingToDocumentFlow() {
        AtomicReference<ApplicationLogEvent> captured = new AtomicReference<>();
        DocumentNotificationMessageConsumer consumer = new DocumentNotificationMessageConsumer(
            Optional.of(ignored -> { throw new IllegalStateException("channel failed"); }),
            Optional.of(recorder(captured))
        );

        assertDoesNotThrow(() -> consumer.process(
            DocumentLifecycleEvent.processingStarted(DOCUMENT_ID, Instant.parse("2026-10-29T12:00:00Z"))
        ));
        assertEquals("notification.dispatch", captured.get().operation());
        assertEquals("ERROR", captured.get().level().name());
    }

    private static ApplicationLogRecorder recorder(AtomicReference<ApplicationLogEvent> captured) {
        ApplicationLogLocalWriter writer = captured::set;
        return new ApplicationLogRecorder(writer, Optional.empty(), Runnable::run);
    }
}
