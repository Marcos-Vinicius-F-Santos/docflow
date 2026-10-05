package com.dockflow.dockflow.document.exception;

import java.util.UUID;

public class DocumentContentStorageUnavailableException extends RuntimeException {
    public DocumentContentStorageUnavailableException(UUID documentId, Throwable cause) {
        super("Storage is unavailable for document " + documentId, cause);
    }
}
