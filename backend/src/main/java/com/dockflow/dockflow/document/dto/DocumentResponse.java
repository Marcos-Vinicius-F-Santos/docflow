package com.dockflow.dockflow.document.dto;

import java.time.Instant;
import java.util.UUID;

import com.dockflow.dockflow.document.DocumentStatus;
import com.dockflow.dockflow.document.DocumentVisibility;

public record DocumentResponse(
    
    UUID id,
    String originalFilename,
    String contentType,
    long sizeBytes,
    DocumentStatus status,
    DocumentVisibility visibility,
    boolean ownedByCurrentUser,
    Instant createdAt,
    Instant updatedAt,
    long falhas,
    boolean isExcluded,
    Instant exclusionScheduledAt

) {

}
