package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

/** Maps persisted document state into the provider-neutral notification contract. */
public final class DocumentLifecycleEventMapper {

    public DocumentLifecycleEvent processingStarted(Document document, Instant occurredAt) {
        requireStatus(document, DocumentStatus.PROCESSING);
        return DocumentLifecycleEvent.processingStarted(document.getId(), requireOccurredAt(occurredAt));
    }

    public DocumentLifecycleEvent completed(Document document, Instant occurredAt) {
        requireStatus(document, DocumentStatus.COMPLETED);
        return DocumentLifecycleEvent.completed(document.getId(), requireOccurredAt(occurredAt));
    }

    public DocumentLifecycleEvent failed(Document document, Instant occurredAt) {
        requireStatus(document, DocumentStatus.FAILED);
        if (document.getFailureCount() < 1) {
            throw new IllegalArgumentException("A failed document must have a failure count");
        }
        return DocumentLifecycleEvent.failed(
            document.getId(),
            requireOccurredAt(occurredAt),
            document.getFailureCount(),
            null
        );
    }

    public DocumentLifecycleEvent deletionWarning(Document document, Instant occurredAt) {
        requireStatus(document, DocumentStatus.FAILED);
        if (!document.isExcluded() || document.getFailureCount() < Document.MAX_PROCESSING_FAILURES) {
            throw new IllegalArgumentException("A deletion warning requires an excluded document");
        }
        if (document.getExclusionScheduledAt() == null) {
            throw new IllegalArgumentException("A deletion warning requires an exclusion schedule");
        }
        return DocumentLifecycleEvent.deletionWarning(
            document.getId(),
            requireOccurredAt(occurredAt),
            document.getFailureCount(),
            document.getExclusionScheduledAt()
        );
    }

    public DocumentLifecycleEvent deleted(UUID documentId, Instant occurredAt) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId cannot be null");
        }
        return DocumentLifecycleEvent.deleted(documentId, requireOccurredAt(occurredAt));
    }

    private static void requireStatus(Document document, DocumentStatus expected) {
        if (document == null) {
            throw new IllegalArgumentException("document cannot be null");
        }
        if (document.getId() == null) {
            throw new IllegalArgumentException("documentId cannot be null");
        }
        if (document.getStatus() != expected) {
            throw new IllegalArgumentException("Document is not in the expected lifecycle state");
        }
    }

    private static Instant requireOccurredAt(Instant occurredAt) {
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt cannot be null");
        }
        return occurredAt;
    }
}
