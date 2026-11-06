package com.dockflow.dockflow.document.application.logging;

import com.dockflow.dockflow.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import com.dockflow.dockflow.document.security.TenantIdentityService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({ApplicationLogPersistenceService.class, ApplicationLogQueryService.class, TenantIdentityService.class})
class ApplicationLogPostgresIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ApplicationLogRepository repository;

    @Autowired
    private ApplicationLogPersistenceService persistenceService;

    @Autowired
    private ApplicationLogQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanLogs() {
        repository.deleteAll();
    }

    @Test
    void shouldApplyMigrationWithIndexesAndPersistAnEventIdempotently() {
        UUID eventId = UUID.randomUUID();
        ApplicationLogEvent event = event(
            eventId,
            Instant.parse("2026-10-29T12:00:00Z"),
            ApplicationLogLevel.ERROR,
            UUID.randomUUID(),
            Map.of("errorCode", "STORAGE_UNAVAILABLE")
        );

        assertEquals(ApplicationLogPersistenceService.PersistenceResult.PERSISTED, persistenceService.persist(event));
        assertEquals(ApplicationLogPersistenceService.PersistenceResult.DUPLICATE, persistenceService.persist(event));
        assertEquals(1, repository.count());

        Integer tableCount = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_name = 'application_logs'",
            Integer.class
        );
        Integer indexCount = jdbcTemplate.queryForObject(
            "select count(*) from pg_indexes where tablename = 'application_logs' and indexname in "
                + "('idx_application_logs_occurred_at', 'idx_application_logs_level', "
                + "'idx_application_logs_service', 'idx_application_logs_document_id', "
                + "'idx_application_logs_correlation_id')",
            Integer.class
        );

        assertEquals(1, tableCount);
        assertEquals(5, indexCount);
    }

    @Test
    void shouldFilterPersistedLogsAndRetainOnlyTheRecentWindow() {
        Instant now = Instant.parse("2026-10-29T12:00:00Z");
        UUID documentId = UUID.randomUUID();
        ApplicationLogEvent oldEvent = event(
            UUID.randomUUID(),
            now.minusSeconds(3 * 60 * 60 + 1),
            ApplicationLogLevel.ERROR,
            documentId,
            Map.of()
        );
        ApplicationLogEvent recentEvent = event(
            UUID.randomUUID(),
            now.minusSeconds(60),
            ApplicationLogLevel.ERROR,
            documentId,
            Map.of()
        );
        persistenceService.persist(oldEvent);
        persistenceService.persist(recentEvent);

        ApplicationLogPageResponse filtered = queryService.find(
            "0", "10", "ERROR", "document-api", "storage", "document.processing",
            documentId.toString(), null, null, null
        );
        assertEquals(2, filtered.totalElements());
        assertTrue(filtered.items().stream().allMatch(item -> item.documentId().equals(documentId)));

        ApplicationLogRetentionService retention = new ApplicationLogRetentionService(
            repository,
            Clock.fixed(now, ZoneOffset.UTC),
            java.time.Duration.ofHours(3)
        );
        assertEquals(1, retention.purgeExpiredLogs());
        assertEquals(1, repository.count());
        assertTrue(repository.findById(recentEvent.eventId()).isPresent());
        assertEquals(0, retention.purgeExpiredLogs());
    }

    private ApplicationLogEvent event(
        UUID eventId,
        Instant occurredAt,
        ApplicationLogLevel level,
        UUID documentId,
        Map<String, Object> details
    ) {
        return new ApplicationLogEvent(
            eventId,
            1,
            occurredAt,
            level,
            "document-api",
            "storage",
            "document.processing",
            "Document storage completed",
            UUID.randomUUID(),
            documentId,
            details
        );
    }
}
