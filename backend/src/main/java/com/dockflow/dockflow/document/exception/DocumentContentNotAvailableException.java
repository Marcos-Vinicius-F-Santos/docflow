package com.dockflow.dockflow.document.exception;

import com.dockflow.dockflow.document.DocumentStatus;

import java.util.UUID;

public class DocumentContentNotAvailableException extends RuntimeException {
    public DocumentContentNotAvailableException(UUID documentId, DocumentStatus status) {
        super("Document content is not available for download in status " + status + ": " + documentId);
    }
}
