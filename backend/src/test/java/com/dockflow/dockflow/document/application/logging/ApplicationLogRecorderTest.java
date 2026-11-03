package com.dockflow.dockflow.document.application.logging;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationLogRecorderTest {

    @Test
    void shouldWriteLocallyBeforeSchedulingRemoteDelivery() {
        List<String> calls = new java.util.ArrayList<>();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        ApplicationLogEvent event = event();
        ApplicationLogLocalWriter local = ignored -> calls.add("local");
        ApplicationLogPublisher remote = ignored -> calls.add("remote");
        Executor executor = scheduled::set;

        ApplicationLogRecorder recorder = new ApplicationLogRecorder(local, java.util.Optional.of(remote), executor);

        recorder.record(event);

        assertEquals(List.of("local"), calls);
        assertTrue(scheduled.get() != null);
        scheduled.get().run();
        assertEquals(List.of("local", "remote"), calls);
    }

    @Test
    void shouldRetainLocalEventWhenRemotePublisherFails() {
        AtomicReference<ApplicationLogEvent> localEvent = new AtomicReference<>();
        ApplicationLogLocalWriter local = localEvent::set;
        ApplicationLogPublisher remote = ignored -> { throw new IllegalStateException("broker unavailable"); };
        ApplicationLogRecorder recorder = new ApplicationLogRecorder(local, java.util.Optional.of(remote), Runnable::run);

        assertDoesNotThrow(() -> recorder.record(event()));
        assertEquals(event().message(), localEvent.get().message());
    }

    @Test
    void shouldNotRepublishWhenRemoteDeliveryFails() {
        AtomicInteger remoteCalls = new AtomicInteger();
        ApplicationLogPublisher remote = ignored -> {
            remoteCalls.incrementAndGet();
            throw new IllegalStateException("broker unavailable");
        };
        ApplicationLogRecorder recorder = new ApplicationLogRecorder(
            ignored -> { },
            java.util.Optional.of(remote),
            Runnable::run
        );

        recorder.record(event());

        assertEquals(1, remoteCalls.get());
    }

    @Test
    void shouldNotFailBusinessCallerWhenExecutorRejectsRemoteDelivery() {
        ApplicationLogEvent event = event();
        AtomicReference<ApplicationLogEvent> localEvent = new AtomicReference<>();
        ApplicationLogLocalWriter local = localEvent::set;
        Executor rejected = ignored -> { throw new java.util.concurrent.RejectedExecutionException(); };
        ApplicationLogRecorder recorder = new ApplicationLogRecorder(
            local,
            java.util.Optional.of(ignored -> { }),
            rejected
        );

        assertDoesNotThrow(() -> recorder.record(event));
        assertEquals(event.eventId(), localEvent.get().eventId());
    }

    private static ApplicationLogEvent event() {
        return new ApplicationLogEvent(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
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
