package com.dockflow.dockflow.document.exception;

import java.util.UUID;

public class DocumentDeletionStorageUnavailableException extends RuntimeException {
    public DocumentDeletionStorageUnavailableException(UUID documentId, Throwable cause) {
        super("Storage is unavailable while deleting document " + documentId, cause);
    }
}
