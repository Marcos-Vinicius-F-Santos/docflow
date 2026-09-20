package com.dockflow.dockflow.document.adapter.in.web.exception;

import com.dockflow.dockflow.document.exception.DocumentNotFoundException;
import com.dockflow.dockflow.document.application.DocumentContentValidator;
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
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void handleDocumentNotFound() {
    }
}
