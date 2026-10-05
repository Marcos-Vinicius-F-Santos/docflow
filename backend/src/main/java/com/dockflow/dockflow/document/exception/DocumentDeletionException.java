package com.dockflow.dockflow.document.exception;

import java.util.UUID;

public class DocumentDeletionException extends RuntimeException {
    public DocumentDeletionException(UUID documentId, Throwable cause) {
        super("Document " + documentId + " could not be deleted", cause);
    }
}
