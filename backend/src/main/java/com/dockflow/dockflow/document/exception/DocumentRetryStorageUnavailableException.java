package com.dockflow.dockflow.document.exception;

import java.util.UUID;

public class DocumentRetryStorageUnavailableException extends RuntimeException {
    public DocumentRetryStorageUnavailableException(UUID documentId, Throwable cause) {
        super("Storage is unavailable while retrying document " + documentId, cause);
    }
}
