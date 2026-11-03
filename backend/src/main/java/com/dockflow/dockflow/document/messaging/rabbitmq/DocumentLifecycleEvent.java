package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.DocumentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Versioned provider-neutral contract for document lifecycle notifications.
 *
 * <p>The contract deliberately contains no database, broker or storage
 * references. Optional state fields are omitted from JSON when they do not
 * apply to the event type.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentLifecycleEvent(
    UUID eventId,
    int schemaVersion,
    DocumentLifecycleEventType type,
    UUID documentId,
    Instant occurredAt,
    DocumentStatus status,
    Long failureCount,
    Instant exclusionScheduledAt
) {

    public static final int SCHEMA_VERSION = 1;

    public DocumentLifecycleEvent {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported notification schema version");
        }
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId cannot be null");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt cannot be null");
        }
        if (failureCount != null && failureCount < 0) {
            throw new IllegalArgumentException("failureCount cannot be negative");
        }

        validateTypeFields(type, status, failureCount, exclusionScheduledAt);
    }

    public static DocumentLifecycleEvent processingStarted(UUID documentId, Instant occurredAt) {
        return new DocumentLifecycleEvent(
            UUID.randomUUID(),
            SCHEMA_VERSION,
            DocumentLifecycleEventType.DOCUMENT_PROCESSING_STARTED,
            documentId,
            occurredAt,
            DocumentStatus.PROCESSING,
            null,
            null
        );
    }

    public static DocumentLifecycleEvent completed(UUID documentId, Instant occurredAt) {
        return new DocumentLifecycleEvent(
            UUID.randomUUID(),
            SCHEMA_VERSION,
            DocumentLifecycleEventType.DOCUMENT_COMPLETED,
            documentId,
            occurredAt,
            DocumentStatus.COMPLETED,
            null,
            null
        );
    }

    public static DocumentLifecycleEvent failed(
        UUID documentId,
        Instant occurredAt,
        long failureCount,
        Instant exclusionScheduledAt
    ) {
        return new DocumentLifecycleEvent(
            UUID.randomUUID(),
            SCHEMA_VERSION,
            DocumentLifecycleEventType.DOCUMENT_FAILED,
            documentId,
            occurredAt,
            DocumentStatus.FAILED,
            failureCount,
            exclusionScheduledAt
        );
    }

    public static DocumentLifecycleEvent deletionWarning(
        UUID documentId,
        Instant occurredAt,
        long failureCount,
        Instant exclusionScheduledAt
    ) {
        return new DocumentLifecycleEvent(
            UUID.randomUUID(),
            SCHEMA_VERSION,
            DocumentLifecycleEventType.DOCUMENT_DELETION_WARNING,
            documentId,
            occurredAt,
            DocumentStatus.FAILED,
            failureCount,
            exclusionScheduledAt
        );
    }

    public static DocumentLifecycleEvent deleted(UUID documentId, Instant occurredAt) {
        return new DocumentLifecycleEvent(
            UUID.randomUUID(),
            SCHEMA_VERSION,
            DocumentLifecycleEventType.DOCUMENT_DELETED,
            documentId,
            occurredAt,
            null,
            null,
            null
        );
    }

    private static void validateTypeFields(
        DocumentLifecycleEventType type,
        DocumentStatus status,
        Long failureCount,
        Instant exclusionScheduledAt
    ) {
        switch (type) {
            case DOCUMENT_PROCESSING_STARTED -> requireStatus(status, DocumentStatus.PROCESSING);
            case DOCUMENT_COMPLETED -> requireStatus(status, DocumentStatus.COMPLETED);
            case DOCUMENT_FAILED -> {
                requireStatus(status, DocumentStatus.FAILED);
                requireFailureCount(failureCount);
            }
            case DOCUMENT_DELETION_WARNING -> {
                requireStatus(status, DocumentStatus.FAILED);
                requireFailureCount(failureCount);
                if (exclusionScheduledAt == null) {
                    throw new IllegalArgumentException(
                        "exclusionScheduledAt is required for deletion warnings"
                    );
                }
            }
            case DOCUMENT_DELETED -> {
                if (status != null || failureCount != null || exclusionScheduledAt != null) {
                    throw new IllegalArgumentException(
                        "Deleted events cannot carry document state fields"
                    );
                }
            }
        }
    }

    private static void requireStatus(DocumentStatus status, DocumentStatus expected) {
        if (status != expected) {
            throw new IllegalArgumentException("Invalid status for notification event type");
        }
    }

    private static void requireFailureCount(Long failureCount) {
        if (failureCount == null) {
            throw new IllegalArgumentException("failureCount is required for this notification event type");
        }
    }
}
