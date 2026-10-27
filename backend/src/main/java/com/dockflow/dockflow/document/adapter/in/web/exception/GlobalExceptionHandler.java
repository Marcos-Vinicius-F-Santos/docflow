package com.dockflow.dockflow.document.adapter.in.web.exception;

import com.dockflow.dockflow.document.exception.DocumentNotFoundException;
import com.dockflow.dockflow.document.exception.DocumentContentNotAvailableException;
import com.dockflow.dockflow.document.exception.DocumentContentNotFoundException;
import com.dockflow.dockflow.document.exception.DocumentContentStorageUnavailableException;
import com.dockflow.dockflow.document.exception.DocumentDeletionException;
import com.dockflow.dockflow.document.exception.DocumentDeletionStorageUnavailableException;
import com.dockflow.dockflow.document.application.DocumentContentValidator;
import com.dockflow.dockflow.document.application.DocumentPublicationException;
import com.dockflow.dockflow.document.exception.DocumentRetryNotEligibleException;
import com.dockflow.dockflow.document.exception.DocumentRetryStorageUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DocumentContentValidationException.class)
    ProblemDetail handleDocumentContentValidation(DocumentContentValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            exception.getMessage()
        );
        problem.setTitle("Invalid document content");
        problem.setProperty("errorCode", exception.getErrorCode().code());
        if (exception.getInvalidField() != null) {
            problem.setProperty("invalidField", exception.getInvalidField());
        }
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "The file exceeds the maximum size of " + DocumentContentValidator.MAX_SIZE_BYTES + " bytes"
        );
        problem.setTitle("Invalid document content");
        problem.setProperty("errorCode", DocumentContentErrorCode.DOCUMENT_CONTENT_TOO_LARGE.code());
        problem.setProperty("invalidField", "file");
        return problem;
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ProblemDetail handleDocumentNotFound(DocumentNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Document not found", "DOCUMENT_NOT_FOUND", exception);
    }

    @ExceptionHandler(DocumentRetryNotEligibleException.class)
    ProblemDetail handleDocumentRetryNotEligible(DocumentRetryNotEligibleException exception) {
        return problem(HttpStatus.CONFLICT, "Document retry is not eligible", "DOCUMENT_RETRY_NOT_ELIGIBLE", exception);
    }

    @ExceptionHandler(DocumentRetryStorageUnavailableException.class)
    ProblemDetail handleDocumentRetryStorageUnavailable(DocumentRetryStorageUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Document retry is unavailable", "DOCUMENT_STORAGE_UNAVAILABLE", exception);
    }

    @ExceptionHandler(DocumentPublicationException.class)
    ProblemDetail handleDocumentPublicationFailure(DocumentPublicationException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Document retry publication is unavailable", "DOCUMENT_RETRY_UNAVAILABLE", exception);
    }

    @ExceptionHandler(DocumentContentNotFoundException.class)
    ProblemDetail handleDocumentContentNotFound(DocumentContentNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Document content not found", "DOCUMENT_NOT_FOUND", exception);
    }

    @ExceptionHandler(DocumentContentNotAvailableException.class)
    ProblemDetail handleDocumentContentNotAvailable(DocumentContentNotAvailableException exception) {
        return problem(HttpStatus.CONFLICT, "Document content is not available", "DOCUMENT_CONTENT_NOT_AVAILABLE", exception);
    }

    @ExceptionHandler(DocumentContentStorageUnavailableException.class)
    ProblemDetail handleDocumentContentStorageUnavailable(DocumentContentStorageUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Document storage is unavailable", "DOCUMENT_STORAGE_UNAVAILABLE", exception);
    }

    @ExceptionHandler(DocumentDeletionStorageUnavailableException.class)
    ProblemDetail handleDocumentDeletionStorageUnavailable(DocumentDeletionStorageUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Document storage is unavailable", "DOCUMENT_STORAGE_UNAVAILABLE", exception);
    }

    @ExceptionHandler(DocumentDeletionException.class)
    ProblemDetail handleDocumentDeletion(DocumentDeletionException exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Document deletion failed", "DOCUMENT_DELETION_FAILED", exception);
    }

    private ProblemDetail problem(
        HttpStatus status,
        String title,
        String errorCode,
        RuntimeException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        return problem;
    }
}
