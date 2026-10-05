package com.dockflow.dockflow.document.storage;

import java.util.UUID;

public class DocumentStorageException extends RuntimeException {

    public enum FailureType {
        UNAVAILABLE(true),
        REJECTED(false),
        RESULT_UNKNOWN(true),
        NOT_FOUND(false);

        private final boolean retryable;

        FailureType(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }

    private final FailureType failureType;
    private final UUID documentId;

    public DocumentStorageException(FailureType failureType, String message) {
        this(failureType, message, null, null);
    }

    public DocumentStorageException(FailureType failureType, String message, Throwable cause) {
        this(failureType, message, cause, null);
    }

    private DocumentStorageException(
        FailureType failureType,
        String message,
        Throwable cause,
        UUID documentId
    ) {
        super(message, cause);
        this.failureType = failureType;
        this.documentId = documentId;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public DocumentStorageException withDocumentId(UUID id) {
        return new DocumentStorageException(failureType, getMessage(), getCause(), id);
    }
}
