package com.dockflow.dockflow.document.mapper;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.dto.DocumentResponse;

public final class DocumentMapper {

    private DocumentMapper() {
    }

    public static DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
            document.getId(),
            document.getOriginalFilename(),
            document.getContentType(),
            document.getSizeBytes(),
            document.getStatus(),
            document.getCreatedAt(),
            document.getUpdatedAt()
        );
    }
    
}
