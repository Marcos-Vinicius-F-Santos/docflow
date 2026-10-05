package com.dockflow.dockflow.document.adapter.out.storage.minio;

import com.dockflow.dockflow.document.application.DocumentContentValidator;
import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentErrorCode;
import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentValidationException;
import com.dockflow.dockflow.document.port.out.storage.DocumentStaging;
import com.dockflow.dockflow.document.storage.DocumentStorageException;
import com.dockflow.dockflow.document.storage.DocumentStorageException.FailureType;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MinioDocumentStagingTest {

    @Test
    void shouldWriteContentCountBytesAndCloseTheInputStream() throws Exception {
        MinioClient client = mock(MinioClient.class);
        ByteArrayOutputStream storedBytes = new ByteArrayOutputStream();
        doAnswer(invocation -> {
            InputStream stream = invocation.<PutObjectArgs>getArgument(0).stream();
            stream.transferTo(storedBytes);
            return null;
        }).when(client).putObject(any(PutObjectArgs.class));

        byte[] content = "document content".getBytes(StandardCharsets.UTF_8);
        TrackingInputStream input = new TrackingInputStream(content);
        UUID documentId = UUID.randomUUID();
        MinioDocumentStaging staging = new MinioDocumentStaging(client);

        DocumentStaging.StagedObject result = staging.stage(
            documentId,
            input,
            "text/plain"
        );

        assertArrayEquals(content, storedBytes.toByteArray());
        assertEquals(content.length, result.sizeBytes());
        assertEquals("text/plain", result.contentType());
        assertEquals("staging/" + documentId, result.contentReference());
        assertTrue(input.closed);

        ArgumentCaptor<PutObjectArgs> argsCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(client).putObject(argsCaptor.capture());
        assertEquals("docflow-staging", argsCaptor.getValue().bucket());
        assertEquals("staging/" + documentId, argsCaptor.getValue().object());
        assertEquals("text/plain", argsCaptor.getValue().contentType());
    }

    @Test
    void shouldAcceptContentExactlyAtTheMaximumSize() throws Exception {
        MinioClient client = mock(MinioClient.class);
        consumePutObjectStream(client);
        MinioDocumentStaging staging = new MinioDocumentStaging(client);

        DocumentStaging.StagedObject result = staging.stage(
            UUID.randomUUID(),
            new GeneratedInputStream(DocumentContentValidator.MAX_SIZE_BYTES),
            "application/pdf"
        );

        assertEquals(DocumentContentValidator.MAX_SIZE_BYTES, result.sizeBytes());
    }

    @Test
    void shouldRejectContentAboveTheMaximumAndRemoveTheStagedObject() throws Exception {
        MinioClient client = mock(MinioClient.class);
        consumePutObjectStream(client);
        MinioDocumentStaging staging = new MinioDocumentStaging(client);
        UUID documentId = UUID.randomUUID();

        DocumentContentValidationException exception = assertThrows(
            DocumentContentValidationException.class,
            () -> staging.stage(
                documentId,
                new GeneratedInputStream(DocumentContentValidator.MAX_SIZE_BYTES + 1),
                "application/pdf"
            )
        );

        assertEquals(DocumentContentErrorCode.DOCUMENT_CONTENT_TOO_LARGE,
            exception.getErrorCode());
        verify(client).removeObject(any());
    }

    @Test
    void shouldRejectEmptyContentAndRemoveTheStagedObject() throws Exception {
        MinioClient client = mock(MinioClient.class);
        consumePutObjectStream(client);
        MinioDocumentStaging staging = new MinioDocumentStaging(client);

        DocumentContentValidationException exception = assertThrows(
            DocumentContentValidationException.class,
            () -> staging.stage(
                UUID.randomUUID(),
                new ByteArrayInputStream(new byte[0]),
                "application/pdf"
            )
        );

        assertEquals(DocumentContentErrorCode.DOCUMENT_CONTENT_EMPTY,
            exception.getErrorCode());
        verify(client).removeObject(any());
    }

    @Test
    void shouldCloseTheInputStreamWhenMinioFails() throws Exception {
        MinioClient client = mock(MinioClient.class);
        doThrow(new IOException("connection lost"))
            .when(client)
            .putObject(any(PutObjectArgs.class));
        TrackingInputStream input = new TrackingInputStream("content".getBytes(StandardCharsets.UTF_8));
        MinioDocumentStaging staging = new MinioDocumentStaging(client);

        assertThrows(DocumentStorageException.class, () -> staging.stage(
            UUID.randomUUID(),
            input,
            "text/plain"
        ));

        assertTrue(input.closed);
        verify(client).putObject(any(PutObjectArgs.class));
    }

    @Test
    void shouldMapSocketTimeoutWhenOpeningStagedContentToResultUnknown() throws Exception {
        MinioClient client = mock(MinioClient.class);
        doThrow(new SocketTimeoutException("read timed out"))
            .when(client)
            .getObject(any());
        MinioDocumentStaging staging = new MinioDocumentStaging(client);

        DocumentStorageException exception = assertThrows(
            DocumentStorageException.class,
            () -> staging.open("staging/timed-out")
        );

        assertEquals(FailureType.RESULT_UNKNOWN, exception.getFailureType());
    }

    @Test
    void shouldConfigureMinioHttpTimeoutsAndProcessingDeadline() {
        MinioDocumentConfiguration configuration = new MinioDocumentConfiguration();

        okhttp3.OkHttpClient client = configuration.minioHttpClient(
            java.time.Duration.ofSeconds(3),
            java.time.Duration.ofSeconds(3),
            java.time.Duration.ofSeconds(3),
            java.time.Duration.ofSeconds(3)
        );

        assertEquals(3_000, client.connectTimeoutMillis());
        assertEquals(3_000, client.readTimeoutMillis());
        assertEquals(3_000, client.writeTimeoutMillis());
        assertEquals(3_000, client.callTimeoutMillis());
    }

    private void consumePutObjectStream(MinioClient client) throws Exception {
        doAnswer(invocation -> {
            invocation.<PutObjectArgs>getArgument(0).stream().transferTo(new ByteArrayOutputStream());
            return null;
        }).when(client).putObject(any(PutObjectArgs.class));
    }

    private static final class TrackingInputStream extends FilterInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] content) {
            super(new ByteArrayInputStream(content));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class GeneratedInputStream extends InputStream {
        private long remaining;

        private GeneratedInputStream(long length) {
            this.remaining = length;
        }

        @Override
        public int read() {
            if (remaining == 0) {
                return -1;
            }
            remaining--;
            return 0;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (remaining == 0) {
                return -1;
            }
            int count = (int) Math.min(remaining, length);
            remaining -= count;
            return count;
        }
    }
}
