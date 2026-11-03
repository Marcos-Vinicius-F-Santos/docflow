package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentLifecycleEventContractTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final UUID DOCUMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-28T12:00:00Z");

    @Test
    void shouldSerializeFailedEventWithOnlyPublicVersionedFields() throws Exception {
        DocumentLifecycleEvent event = DocumentLifecycleEvent.failed(
            DOCUMENT_ID,
            OCCURRED_AT,
            1,
            null
        );

        JsonNode json = JSON.readTree(JSON.writeValueAsString(event));

        assertEquals(
            Set.of(
                "eventId",
                "schemaVersion",
                "type",
                "documentId",
                "occurredAt",
                "status",
                "failureCount"
            ),
            fieldsOf(json)
        );
        assertEquals(1, json.get("schemaVersion").asInt());
        assertEquals("DOCUMENT_FAILED", json.get("type").asText());
        assertEquals("FAILED", json.get("status").asText());
        assertEquals(1, json.get("failureCount").asInt());
        assertFalse(json.has("objectKey"));
        assertFalse(json.has("bucket"));
        assertFalse(json.has("routingKey"));
    }

    @Test
    void shouldSerializeDeletedEventWithoutStateOrInfrastructureFields() throws Exception {
        JsonNode json = JSON.readTree(JSON.writeValueAsString(
            DocumentLifecycleEvent.deleted(DOCUMENT_ID, OCCURRED_AT)
        ));

        assertEquals(
            Set.of("eventId", "schemaVersion", "type", "documentId", "occurredAt"),
            fieldsOf(json)
        );
        assertEquals("DOCUMENT_DELETED", json.get("type").asText());
        assertFalse(json.has("status"));
        assertFalse(json.has("failureCount"));
        assertFalse(json.has("exclusionScheduledAt"));
    }

    @Test
    void shouldExposeAllFivePublicEventTypesThroughFactories() {
        assertEquals(
            Set.of(
                DocumentLifecycleEventType.DOCUMENT_PROCESSING_STARTED,
                DocumentLifecycleEventType.DOCUMENT_COMPLETED,
                DocumentLifecycleEventType.DOCUMENT_FAILED,
                DocumentLifecycleEventType.DOCUMENT_DELETION_WARNING,
                DocumentLifecycleEventType.DOCUMENT_DELETED
            ),
            Set.of(
                DocumentLifecycleEvent.processingStarted(DOCUMENT_ID, OCCURRED_AT).type(),
                DocumentLifecycleEvent.completed(DOCUMENT_ID, OCCURRED_AT).type(),
                DocumentLifecycleEvent.failed(DOCUMENT_ID, OCCURRED_AT, 1, null).type(),
                DocumentLifecycleEvent.deletionWarning(
                    DOCUMENT_ID,
                    OCCURRED_AT,
                    3,
                    OCCURRED_AT.plusSeconds(3 * 60 * 60)
                ).type(),
                DocumentLifecycleEvent.deleted(DOCUMENT_ID, OCCURRED_AT).type()
            )
        );
    }

    @Test
    void shouldRejectInvalidPresenceRules() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentLifecycleEvent(
            UUID.randomUUID(),
            1,
            DocumentLifecycleEventType.DOCUMENT_DELETION_WARNING,
            DOCUMENT_ID,
            OCCURRED_AT,
            null,
            3L,
            null
        ));
        assertThrows(IllegalArgumentException.class, () -> new DocumentLifecycleEvent(
            UUID.randomUUID(),
            2,
            DocumentLifecycleEventType.DOCUMENT_DELETED,
            DOCUMENT_ID,
            OCCURRED_AT,
            null,
            null,
            null
        ));
    }

    private static Set<String> fieldsOf(JsonNode json) {
        Set<String> fields = new java.util.HashSet<>();
        json.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
