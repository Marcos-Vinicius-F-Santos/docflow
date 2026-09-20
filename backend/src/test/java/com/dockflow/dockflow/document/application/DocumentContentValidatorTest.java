package com.dockflow.dockflow.document.application;

import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentErrorCode;
import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentValidationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentContentValidatorTest {

    private final DocumentContentValidator validator = new DocumentContentValidator();

    @Test
    void shouldAcceptCompatibleSignatureAndPreserveANonMarkableStream() throws Exception {
        byte[] pdf = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII);
        TrackingNonMarkableInputStream input = new TrackingNonMarkableInputStream(pdf);

        DocumentContent validated = validator.validate(
            new DocumentContent("document.pdf", "application/pdf", input)
        );

        assertArrayEquals(pdf, validated.content().readAllBytes());
        assertTrue(!input.closed);
        validated.content().close();
        assertTrue(input.closed);
    }

    @Test
    void shouldRejectAContentTypeThatDiffersFromTheDetectedSignature() {
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);

        DocumentContentValidationException exception = assertThrows(
            DocumentContentValidationException.class,
            () -> validator.validate(new DocumentContent(
                "document.pdf",
                "text/plain",
                new ByteArrayInputStream(pdf)
            ))
        );

        assertEquals(DocumentContentErrorCode.DOCUMENT_CONTENT_TYPE_MISMATCH,
            exception.getErrorCode());
        assertEquals("file", exception.getInvalidField());
    }

    @Test
    void shouldRejectAnUnknownSignature() {
        DocumentContentValidationException exception = assertThrows(
            DocumentContentValidationException.class,
            () -> validator.validate(new DocumentContent(
                "document.bin",
                "application/octet-stream",
                new ByteArrayInputStream(new byte[] {0, 1, 2, 3, 4, 5, 6})
            ))
        );

        assertEquals(DocumentContentErrorCode.DOCUMENT_CONTENT_TYPE_UNKNOWN,
            exception.getErrorCode());
    }

    @Test
    void shouldRejectEmptyContent() {
        DocumentContentValidationException exception = assertThrows(
            DocumentContentValidationException.class,
            () -> validator.validate(new DocumentContent(
                "empty.pdf",
                "application/pdf",
                new ByteArrayInputStream(new byte[0])
            ))
        );

        assertEquals(DocumentContentErrorCode.DOCUMENT_CONTENT_EMPTY,
            exception.getErrorCode());
    }

    @Test
    void shouldRejectMissingContentType() {
        DocumentContentValidationException exception = assertThrows(
            DocumentContentValidationException.class,
            () -> validator.validate(new DocumentContent(
                "document.pdf",
                " ",
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8))
            ))
        );

        assertEquals(DocumentContentErrorCode.DOCUMENT_CONTENT_TYPE_MISSING,
            exception.getErrorCode());
        assertEquals("file", exception.getInvalidField());
    }

    @Test
    void shouldRejectMissingBinary() {
        DocumentContentValidationException exception = assertThrows(
            DocumentContentValidationException.class,
            () -> validator.validate(new DocumentContent("document.pdf", "application/pdf", null))
        );

        assertEquals(DocumentContentErrorCode.DOCUMENT_CONTENT_MISSING,
            exception.getErrorCode());
    }

    private static final class TrackingNonMarkableInputStream extends FilterInputStream {
        private boolean closed;

        private TrackingNonMarkableInputStream(byte[] content) {
            super(new ByteArrayInputStream(content));
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public void mark(int readLimit) {
        }

        @Override
        public void reset() throws IOException {
            throw new IOException("mark/reset is not supported");
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
