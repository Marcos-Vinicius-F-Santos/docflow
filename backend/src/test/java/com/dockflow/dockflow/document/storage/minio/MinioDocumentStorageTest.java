package com.dockflow.dockflow.document.storage.minio;

import com.dockflow.dockflow.document.storage.DocumentStorageException;
import com.dockflow.dockflow.document.storage.DocumentStorageException.FailureType;

import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;

import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MinioDocumentStorageTest {

    @Test
    void shouldReturnTrueWhenStatObjectSucceeds() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.statObject(any(StatObjectArgs.class))).thenReturn(null);

        MinioDocumentStorage storage = new MinioDocumentStorage(client, "documents");

        assertTrue(storage.exists("documents/confirmed/content"));
    }

    @Test
    void shouldReturnFalseWhenStatObjectReturnsNotFound() throws Exception {
        MinioClient client = mock(MinioClient.class);
        doThrow(errorResponseException(404))
            .when(client)
            .statObject(any(StatObjectArgs.class));

        MinioDocumentStorage storage = new MinioDocumentStorage(client, "documents");

        assertFalse(storage.exists("documents/missing/content"));
    }

    @Test
    void shouldMapInconclusiveStatObjectFailureToResultUnknown() throws Exception {
        MinioClient client = mock(MinioClient.class);
        doThrow(new IOException("connection lost"))
            .when(client)
            .statObject(any(StatObjectArgs.class));

        MinioDocumentStorage storage = new MinioDocumentStorage(client, "documents");

        DocumentStorageException exception = assertThrows(
            DocumentStorageException.class,
            () -> storage.exists("documents/unknown/content")
        );

        assertEquals(FailureType.RESULT_UNKNOWN, exception.getFailureType());
    }

    @Test
    void shouldMapSocketTimeoutToResultUnknown() throws Exception {
        MinioClient client = mock(MinioClient.class);
        doThrow(new SocketTimeoutException("read timed out"))
            .when(client)
            .statObject(any(StatObjectArgs.class));

        MinioDocumentStorage storage = new MinioDocumentStorage(client, "documents");

        DocumentStorageException exception = assertThrows(
            DocumentStorageException.class,
            () -> storage.exists("documents/timed-out/content")
        );

        assertEquals(FailureType.RESULT_UNKNOWN, exception.getFailureType());
    }

    private ErrorResponseException errorResponseException(int statusCode) {
        ErrorResponse errorResponse = new ErrorResponse(
            "NoSuchKey",
            "Object does not exist",
            "documents",
            "documents/missing/content",
            "",
            "request-id",
            "host-id"
        );

        Response response = new Response.Builder()
            .request(new Request.Builder().url("http://localhost").build())
            .protocol(Protocol.HTTP_1_1)
            .code(statusCode)
            .message("Not Found")
            .build();

        return new ErrorResponseException(errorResponse, response, "request-id");
    }
}
