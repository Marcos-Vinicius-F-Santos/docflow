package com.dockflow.dockflow.document.mapper;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.dto.DocumentResponse;

public final class DocumentMapper {

    private DocumentMapper() {
    }

    public static DocumentResponse toResponse(Document document) {
        return toResponse(document, false);
    }

    public static DocumentResponse toResponse(Document document, boolean ownedByCurrentUser) {
        return new DocumentResponse(
            document.getId(),
            document.getOriginalFilename(),
            document.getContentType(),
            document.getSizeBytes(),
            document.getStatus(),
            document.getVisibility(),
            ownedByCurrentUser,
            document.getCreatedAt(),
            document.getUpdatedAt(),
            document.getFailureCount(),
            document.isExcluded(),
            document.getExclusionScheduledAt()
        );
    }
    
}
