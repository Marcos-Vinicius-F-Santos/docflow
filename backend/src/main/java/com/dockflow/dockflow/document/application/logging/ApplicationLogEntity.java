package com.dockflow.dockflow.document.application.logging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Persistence model kept separate from the public event contract. */
@Entity
@Table(name = "application_logs")
public class ApplicationLogEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApplicationLogLevel level;

    @Column(nullable = false, length = 120)
    private String service;

    @Column(nullable = false, length = 160)
    private String component;

    @Column(nullable = false, length = 160)
    private String operation;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "document_id")
    private UUID documentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "persisted_at", nullable = false, updatable = false)
    private Instant persistedAt;

    protected ApplicationLogEntity() {
    }

    public ApplicationLogEntity(ApplicationLogEvent event) {
        this(event, com.dockflow.dockflow.document.security.TenantIdentityService.BOOTSTRAP_TENANT_ID);
    }

    public ApplicationLogEntity(ApplicationLogEvent event, UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId cannot be null");
        }
        this.tenantId = tenantId;
        this.eventId = event.eventId();
        this.schemaVersion = event.schemaVersion();
        this.occurredAt = event.occurredAt();
        this.level = event.level();
        this.service = event.service();
        this.component = event.component();
        this.operation = event.operation();
        this.message = event.message();
        this.correlationId = event.correlationId();
        this.documentId = event.documentId();
        this.details = event.details();
    }

    @PrePersist
    void onPersist() {
        if (persistedAt == null) {
            persistedAt = Instant.now();
        }
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public ApplicationLogLevel getLevel() {
        return level;
    }

    public String getService() {
        return service;
    }

    public String getComponent() {
        return component;
    }

    public String getOperation() {
        return operation;
    }

    public String getMessage() {
        return message;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getPersistedAt() {
        return persistedAt;
    }

    public ApplicationLogEvent toEvent() {
        return new ApplicationLogEvent(
            eventId,
            schemaVersion,
            occurredAt,
            level,
            service,
            component,
            operation,
            message,
            correlationId,
            documentId,
            details
        );
    }
}
