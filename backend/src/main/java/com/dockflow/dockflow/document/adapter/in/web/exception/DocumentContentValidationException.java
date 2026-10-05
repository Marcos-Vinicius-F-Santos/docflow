package com.dockflow.dockflow.document.adapter.in.web.exception;

/**
 * Validation failure carrying the stable error code and optional invalid field
 * used to build an RFC 7807 ProblemDetail response.
 */
public class DocumentContentValidationException extends RuntimeException {

    private final DocumentContentErrorCode errorCode;
    private final String invalidField;

    public DocumentContentValidationException(
        DocumentContentErrorCode errorCode,
        String message,
        String invalidField
    ) {
        super(message);
        this.errorCode = errorCode;
        this.invalidField = invalidField;
    }

    public DocumentContentValidationException(
        DocumentContentErrorCode errorCode,
        String message
    ) {
        this(errorCode, message, errorCode.defaultInvalidField());
    }

    public DocumentContentValidationException(
        DocumentContentErrorCode errorCode,
        String message,
        Throwable cause
    ) {
        this(errorCode, message, errorCode.defaultInvalidField());
        initCause(cause);
    }

    public DocumentContentErrorCode getErrorCode() {
        return errorCode;
    }

    public String getInvalidField() {
        return invalidField;
    }
}
