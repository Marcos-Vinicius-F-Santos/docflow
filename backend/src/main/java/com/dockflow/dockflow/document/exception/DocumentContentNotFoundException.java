package com.dockflow.dockflow.document.exception;

import java.util.UUID;

public class DocumentContentNotFoundException extends RuntimeException {
    public DocumentContentNotFoundException(UUID documentId) {
        super("Content for document " + documentId + " was not found");
    }
}
