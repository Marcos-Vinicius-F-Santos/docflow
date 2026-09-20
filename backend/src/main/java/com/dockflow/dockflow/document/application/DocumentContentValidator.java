package com.dockflow.dockflow.document.application;

import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentErrorCode;
import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentValidationException;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Validates the metadata and detected media type of received document content. */
@Component
public class DocumentContentValidator {

    public static final long MAX_SIZE_BYTES = 52_428_800L;

    private final Tika tika;

    public DocumentContentValidator() {
        this(new Tika());
    }

    DocumentContentValidator(Tika tika) {
        this.tika = tika;
    }

    /**
     * Validates the request and returns the same content with a Tika-managed
     * stream, preserving the stream for the subsequent staging write.
     */
    public DocumentContent validate(DocumentContent content) {
        if (content == null || content.content() == null) {
            throw invalid(DocumentContentErrorCode.DOCUMENT_CONTENT_MISSING,
                "Document content is required");
        }
        if (isBlank(content.originalFilename())) {
            throw invalid(DocumentContentErrorCode.DOCUMENT_FILENAME_MISSING,
                "The file filename is required");
        }
        if (isBlank(content.contentType())) {
            throw invalid(DocumentContentErrorCode.DOCUMENT_CONTENT_TYPE_MISSING,
                "The file Content-Type is required");
        }

        TikaInputStream tikaStream = null;
        try {
            tikaStream = TikaInputStream.get(content.content());
            tikaStream.mark(1);
            int firstByte = tikaStream.read();
            tikaStream.reset();

            if (firstByte < 0) {
                throw invalid(DocumentContentErrorCode.DOCUMENT_CONTENT_EMPTY,
                    "The file content cannot be empty");
            }

            String detectedContentType = tika.detect(tikaStream);
            if (isUnknown(detectedContentType)) {
                throw invalid(DocumentContentErrorCode.DOCUMENT_CONTENT_TYPE_UNKNOWN,
                    "The file signature could not be identified");
            }
            if (!normalize(content.contentType()).equals(normalize(detectedContentType))) {
                throw invalid(DocumentContentErrorCode.DOCUMENT_CONTENT_TYPE_MISMATCH,
                    "The declared Content-Type does not match the file signature");
            }

            return new DocumentContent(
                content.originalFilename(),
                content.contentType(),
                tikaStream
            );
        } catch (DocumentContentValidationException exception) {
            closeAfterRejectedValidation(tikaStream, content.content());
            throw exception;
        } catch (IOException exception) {
            closeAfterRejectedValidation(tikaStream, content.content());
            throw new DocumentContentValidationException(
                DocumentContentErrorCode.DOCUMENT_CONTENT_READ_FAILED,
                "The file content could not be read",
                exception
            );
        }
    }

    private void closeAfterRejectedValidation(TikaInputStream tikaStream, InputStream originalStream) {
        try {
            if (tikaStream != null) {
                tikaStream.close();
            } else {
                originalStream.close();
            }
        } catch (IOException ignored) {
            // The validation error remains the relevant failure for the request.
        }
    }

    private DocumentContentValidationException invalid(
        DocumentContentErrorCode code,
        String message
    ) {
        return new DocumentContentValidationException(code, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isUnknown(String detectedContentType) {
        return isBlank(detectedContentType)
            || "application/octet-stream".equalsIgnoreCase(detectedContentType);
    }

    private String normalize(String contentType) {
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }
}
