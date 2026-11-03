package com.dockflow.dockflow.document.application.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationLogEventContractTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-29T12:00:00Z");
    private static final UUID CORRELATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DOCUMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void shouldCreateVersionedEventWithOptionalContextAndSafeDetails() throws Exception {
        ApplicationLogEvent event = new ApplicationLogEventFactory(
            Clock.fixed(OCCURRED_AT, ZoneOffset.UTC)
        ).create(
            ApplicationLogLevel.ERROR,
            "document-api",
            "storage",
            "document.processing",
            "storage failure token=secret-value",
            new ApplicationLogContext(CORRELATION_ID, DOCUMENT_ID),
            Map.of(
                "errorCode", "STORAGE_UNAVAILABLE",
                "apiKey", "should-not-leak",
                "payload", new byte[] {1, 2, 3},
                "nested", Map.of("password", "should-not-leak")
            )
        );

        JsonNode json = JSON.readTree(JSON.writeValueAsString(event));

        assertEquals(1, json.get("schemaVersion").asInt());
        assertEquals("ERROR", json.get("level").asText());
        assertEquals(OCCURRED_AT.toString(), json.get("occurredAt").asText());
        assertEquals(CORRELATION_ID.toString(), json.get("correlationId").asText());
        assertEquals(DOCUMENT_ID.toString(), json.get("documentId").asText());
        assertEquals("storage failure token=[REDACTED]", json.get("message").asText());
        assertEquals("[REDACTED]", json.at("/details/apiKey").asText());
        assertEquals("[REDACTED_BINARY]", json.at("/details/payload").asText());
        assertEquals("[REDACTED]", json.at("/details/nested/password").asText());
    }

    @Test
    void shouldSupportAllApprovedLevelsAndOmitEmptyOptionalFields() throws Exception {
        for (ApplicationLogLevel level : ApplicationLogLevel.values()) {
            ApplicationLogEvent event = new ApplicationLogEventFactory(
                Clock.fixed(OCCURRED_AT, ZoneOffset.UTC)
            ).create(level, "document-api", "test", "operation", "safe message", null, null);

            JsonNode json = JSON.readTree(JSON.writeValueAsString(event));
            assertEquals(level.name(), json.get("level").asText());
            assertFalse(json.has("correlationId"));
            assertFalse(json.has("documentId"));
            assertFalse(json.has("details"));
        }
    }

    @Test
    void shouldRejectMissingRequiredFieldsOrUnsupportedSchema() {
        assertThrows(IllegalArgumentException.class, () -> new ApplicationLogEvent(
            null, 1, OCCURRED_AT, ApplicationLogLevel.INFO, "service", "component", "operation", "message",
            null, null, null
        ));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationLogEvent(
            UUID.randomUUID(), 2, OCCURRED_AT, ApplicationLogLevel.INFO, "service", "component", "operation", "message",
            null, null, null
        ));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationLogEvent(
            UUID.randomUUID(), 1, OCCURRED_AT, ApplicationLogLevel.INFO, "service", "component", "operation", "   ",
            null, null, null
        ));
    }
}
