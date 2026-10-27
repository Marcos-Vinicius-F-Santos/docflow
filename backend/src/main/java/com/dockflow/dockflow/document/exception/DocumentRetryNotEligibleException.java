package com.dockflow.dockflow.document.exception;

import java.util.UUID;

public class DocumentRetryNotEligibleException extends RuntimeException {
    public DocumentRetryNotEligibleException(UUID documentId) {
        super("Document " + documentId + " is not eligible for retry");
    }
}
