package com.dockflow.dockflow.document.adapter.in.web.exception;

/** Stable, client-facing identifiers for document-content validation failures. */
public enum DocumentContentErrorCode {
    DOCUMENT_CONTENT_MISSING("file"),
    DOCUMENT_CONTENT_EMPTY("file"),
    DOCUMENT_FILENAME_MISSING("file"),
    DOCUMENT_CONTENT_TYPE_MISSING("file"),
    DOCUMENT_CONTENT_TYPE_MISMATCH("file"),
    DOCUMENT_CONTENT_TYPE_UNKNOWN("file"),
    DOCUMENT_CONTENT_TOO_LARGE("file"),
    DOCUMENT_METADATA_NOT_ALLOWED("metadata"),
    DOCUMENT_CONTENT_READ_FAILED(null);

    private final String defaultInvalidField;

    DocumentContentErrorCode(String defaultInvalidField) {
        this.defaultInvalidField = defaultInvalidField;
    }

    public String code() {
        return name();
    }

    public String defaultInvalidField() {
        return defaultInvalidField;
    }
}
