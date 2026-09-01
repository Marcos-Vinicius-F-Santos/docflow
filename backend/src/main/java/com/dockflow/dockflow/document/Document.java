package com.dockflow.dockflow.document;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;
import java.time.Instant;

import org.hibernate.annotations.UuidGenerator;

@Entity 
@Table(name = "documents")

public class Document {
    @Id
    @GeneratedValue
    @UuidGenerator
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
        
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = DocumentStatus.PENDING;

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