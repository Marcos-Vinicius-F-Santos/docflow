package com.dockflow.dockflow.document.dto;

import java.time.Instant;
import java.util.UUID;

import com.dockflow.dockflow.document.DocumentStatus;

public record DocumentResponse(
    
    UUID id,
    String originalFilename,
    String contentType,
    long sizeBytes,
    DocumentStatus status,
    Instant createdAt,
    Instant updatedAt

) {

}
