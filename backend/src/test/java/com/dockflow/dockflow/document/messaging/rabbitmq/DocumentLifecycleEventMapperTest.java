package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentLifecycleEventMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final UUID DOCUMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-29T13:00:00Z");

    private final DocumentLifecycleEventMapper mapper = new DocumentLifecycleEventMapper();

    @Test
    void mapsAllFiveLifecycleStatesWithoutInfrastructureFields() throws Exception {
        Document processing = new Document(DOCUMENT_ID, "document.pdf", "application/pdf", 10);
        processing.startProcessing();

        Document completed = new Document(UUID.randomUUID(), "completed.pdf", "application/pdf", 10);
        completed.startProcessing();
        completed.markCompleted("documents/final");

        Document failed = new Document(UUID.randomUUID(), "failed.pdf", "application/pdf", 10);
        ReflectionTestUtils.setField(failed, "status", DocumentStatus.FAILED);
        ReflectionTestUtils.setField(failed, "failureCount", 1L);

        Document warning = new Document(UUID.randomUUID(), "warning.pdf", "application/pdf", 10);
        Instant scheduledAt = OCCURRED_AT.plusSeconds(3 * 60 * 60);
        ReflectionTestUtils.setField(warning, "status", DocumentStatus.FAILED);
        ReflectionTestUtils.setField(warning, "failureCount", 3L);
        ReflectionTestUtils.setField(warning, "excluded", true);
        ReflectionTestUtils.setField(warning, "exclusionScheduledAt", scheduledAt);

        Set<DocumentLifecycleEvent> events = Set.of(
            mapper.processingStarted(processing, OCCURRED_AT),
            mapper.completed(completed, OCCURRED_AT),
            mapper.failed(failed, OCCURRED_AT),
            mapper.deletionWarning(warning, OCCURRED_AT),
            mapper.deleted(UUID.randomUUID(), OCCURRED_AT)
        );

        assertEquals(Set.of(DocumentLifecycleEventType.values()), events.stream()
            .map(DocumentLifecycleEvent::type)
            .collect(java.util.stream.Collectors.toSet()));

        for (DocumentLifecycleEvent event : events) {
            JsonNode json = JSON.readTree(JSON.writeValueAsString(event));
            assertEquals(1, json.get("schemaVersion").asInt());
            assertFalse(json.has("bucket"));
            assertFalse(json.has("objectKey"));
            assertFalse(json.has("routingKey"));
            assertFalse(json.has("apiKey"));
        }
    }

    @Test
    void rejectsStateMismatchesAndInvalidDeletionWarnings() {
        Document pending = new Document(DOCUMENT_ID, "document.pdf", "application/pdf", 10);

        assertThrows(IllegalArgumentException.class, () -> mapper.processingStarted(pending, OCCURRED_AT));
        assertThrows(IllegalArgumentException.class, () -> mapper.deletionWarning(pending, OCCURRED_AT));
        assertThrows(IllegalArgumentException.class, () -> mapper.deleted(null, OCCURRED_AT));
    }
}
