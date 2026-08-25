package com.dockflow.dockflow.document;
import jakarta.persistence.*;
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
    @Column(nullable = false)
    private String originalFilename;
    @Column(nullable = false)
    private String contentType;

    @PositiveOrZero
    @Column(nullable = false)
    private long sizeBytes;

    @NotNull
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    

    public Document() {
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

}