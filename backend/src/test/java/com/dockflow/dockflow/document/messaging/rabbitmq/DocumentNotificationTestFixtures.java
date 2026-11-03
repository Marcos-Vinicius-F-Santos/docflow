package com.dockflow.dockflow.document.messaging.rabbitmq;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEvent.completed;
import static com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEvent.deleted;
import static com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEvent.deletionWarning;
import static com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEvent.failed;
import static com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEvent.processingStarted;

/** Deterministic lifecycle events shared by notification tests. */
public final class DocumentNotificationTestFixtures {

    public static final UUID DOCUMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    public static final Instant OCCURRED_AT = Instant.parse("2026-10-29T12:00:00Z");
    public static final Instant EXCLUSION_SCHEDULED_AT = Instant.parse("2026-10-29T14:30:00Z");

    private DocumentNotificationTestFixtures() {
    }

    public static List<DocumentLifecycleEvent> lifecycleEvents() {
        return List.of(
            processingStarted(DOCUMENT_ID, OCCURRED_AT),
            completed(DOCUMENT_ID, OCCURRED_AT.plusSeconds(30)),
            failed(DOCUMENT_ID, OCCURRED_AT.plusSeconds(60), 1, null),
            deletionWarning(DOCUMENT_ID, OCCURRED_AT.plusSeconds(90), 3, EXCLUSION_SCHEDULED_AT),
            deleted(DOCUMENT_ID, OCCURRED_AT.plusSeconds(120))
        );
    }
}
