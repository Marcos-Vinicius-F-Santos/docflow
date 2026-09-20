package com.dockflow.dockflow.document;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Persistable;

import java.util.UUID;
import java.time.Instant;
import java.time.Duration;

@Entity 
@Table(name = "documents")

public class Document implements Persistable<UUID> {
    public static final Duration PROCESSING_LEASE = Duration.ofSeconds(10);

    @Id
    private UUID id;

    @NotNull
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DocumentStatus status;

    @NotBlank
    @Column(nullable = false)
    private String originalFilename;

    @NotBlank
    @Column(nullable = false)
    private String contentType;

    @PositiveOrZero
    @Column(nullable = false)
    private long sizeBytes;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "reconciliation_attempts", nullable = false)
    private long reconciliationAttempts;

    @Column(name = "reconciliation_next_attempt_at")
    private Instant reconciliationNextAttemptAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Transient
    private boolean newEntity = true;

    @NotNull
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @NotNull
    @Column(nullable = false)
    private Instant updatedAt;

    protected Document() {

    }

    public Document(String originalFilename, String contentType, long sizeBytes) {
        
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes cannot be negative");
        }

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("originalFilename cannot be null or blank");
        }

        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType cannot be null or blank");
        }
        
        this.id = UUID.randomUUID();
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = DocumentStatus.PENDING;

    }

    /**
     * Creates a document with the identifier already allocated for staging.
     * The existing constructor remains the default path for current callers.
     */
    public Document(UUID id, String originalFilename, String contentType, long sizeBytes) {
        this(originalFilename, contentType, sizeBytes);

        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }

        this.id = id;
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @PostLoad
    private void onLoad() {
        this.newEntity = false;
    }

    @PostPersist
    private void onPersist() {
        this.newEntity = false;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public long getReconciliationAttempts() {
        return reconciliationAttempts;
    }

    /** Registers the deterministic final-storage reference before processing can be uncertain. */
    public void registerStorageObjectKey(String objectKey) {
        if (status != DocumentStatus.PENDING && status != DocumentStatus.PROCESSING) {
            throw new IllegalStateException("Only pending or processing documents can register an objectKey");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey cannot be null or blank");
        }
        if (this.objectKey != null && !this.objectKey.equals(objectKey)) {
            throw new IllegalStateException("The document objectKey cannot be changed");
        }

        this.objectKey = objectKey;
    }

    public long registerReconciliationAttempt() {
        if (status != DocumentStatus.PROCESSING) {
            throw new IllegalStateException(
                "Only processing documents can be reconciled"
            );
        }

        reconciliationAttempts++;
        return reconciliationAttempts;
    }

    /** Starts a claimed reconciliation attempt using the persisted storage reference. */
    public long beginReconciliationAttempt() {
        if (status != DocumentStatus.PROCESSING) {
            throw new IllegalStateException("Only processing documents can be reconciled");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalStateException("A processing document must have an objectKey to be reconciled");
        }
        if (reconciliationAttempts >= 5) {
            throw new IllegalStateException("The reconciliation attempt limit has been reached");
        }

        reconciliationNextAttemptAt = null;
        return ++reconciliationAttempts;
    }

    public Instant getReconciliationNextAttemptAt() {
        return reconciliationNextAttemptAt;
    }

    public void scheduleReconciliation(Instant nextAttemptAt) {
        if (status != DocumentStatus.PROCESSING) {
            throw new IllegalStateException("Only processing documents can be scheduled for reconciliation");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalStateException("A processing document must have an objectKey to be reconciled");
        }
        if (nextAttemptAt == null) {
            throw new IllegalArgumentException("nextAttemptAt cannot be null");
        }

        reconciliationNextAttemptAt = nextAttemptAt;
    }

    public long getVersion() {
        return version;
    }

    public void startProcessing() {
        if (status != DocumentStatus.PENDING) {
            throw new IllegalStateException("Only pending documents can start processing");
        }

        status = DocumentStatus.PROCESSING;
    }

    public void markCompleted(String objectKey) {
        if (status != DocumentStatus.PROCESSING) {
            throw new IllegalStateException("Only processing documents can be completed");
        }

        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey cannot be null or blank");
        }
        if (this.objectKey != null && !this.objectKey.equals(objectKey)) {
            throw new IllegalStateException("The document objectKey cannot be changed");
        }

        this.objectKey = objectKey;
        this.reconciliationNextAttemptAt = null;
        status = DocumentStatus.COMPLETED;
    }

    public void markFailed() {
        if (status != DocumentStatus.PENDING && status != DocumentStatus.PROCESSING) {
            throw new IllegalStateException("Only pending or processing documents can fail");
        }

        status = DocumentStatus.FAILED;
        reconciliationNextAttemptAt = null;
    }
    
    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}
