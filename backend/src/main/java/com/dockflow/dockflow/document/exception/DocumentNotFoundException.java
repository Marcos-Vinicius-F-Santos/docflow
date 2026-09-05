package com.dockflow.dockflow.document.exception;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(UUID documentId) {
        super("Document with ID " + documentId + " not found.");
    }
    
}
