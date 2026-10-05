package com.dockflow.dockflow.document.storage.minio;

import com.dockflow.dockflow.document.adapter.out.storage.minio.MinioDocumentStaging;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingReader;
import com.dockflow.dockflow.document.storage.DocumentStorageException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class MinioRealIntegrationTest {

    private static final String USER = "docflow-test";
    private static final String PASSWORD = "docflow-test-password";
    private static final String STAGING_BUCKET = "docflow-staging";
    private static final String FINAL_BUCKET = "docflow-documents";

    @Container
    static final GenericContainer<?> minio = new GenericContainer<>("quay.io/minio/minio:latest")
        .withEnv("MINIO_ROOT_USER", USER)
        .withEnv("MINIO_ROOT_PASSWORD", PASSWORD)
        .withCommand("server", "/data", "--console-address", ":9001")
        .withExposedPorts(9000);

    @BeforeAll
    static void createBuckets() throws Exception {
        MinioClient client = client(USER, PASSWORD);
        createBucketIfNecessary(client, STAGING_BUCKET);
        createBucketIfNecessary(client, FINAL_BUCKET);
    }

    @Test
    void shouldStageReadStoreAndRemoveContentUsingBothRealBuckets() throws Exception {
        MinioClient client = client(USER, PASSWORD);
        MinioDocumentStaging staging = new MinioDocumentStaging(client, STAGING_BUCKET);
        MinioDocumentStorage storage = new MinioDocumentStorage(client, FINAL_BUCKET);
        UUID documentId = UUID.randomUUID();
        byte[] content = "real-minio-content".getBytes(StandardCharsets.UTF_8);

        var staged = staging.stage(documentId, new ByteArrayInputStream(content), "text/plain");
        assertEquals("staging/" + documentId, staged.contentReference());
        assertEquals(content.length, staged.sizeBytes());

        try (DocumentStagingReader.StagedContent opened = staging.open(staged.contentReference())) {
            assertArrayEquals(content, opened.content().readAllBytes());
        }

        storage.store(
            "documents/" + documentId,
            new ByteArrayInputStream(content),
            content.length,
            "text/plain"
        );
        assertTrue(storage.exists("documents/" + documentId));

        storage.delete("documents/" + documentId);
        staging.delete(staged.contentReference());
        assertTrue(!storage.exists("documents/" + documentId));
    }

    @Test
    void shouldReadTheStoredObjectAsAStreamingContentWithMetadata() throws Exception {
        MinioClient client = client(USER, PASSWORD);
        MinioDocumentStorage storage = new MinioDocumentStorage(client, FINAL_BUCKET);
        UUID documentId = UUID.randomUUID();
        byte[] content = "streamed-real-minio-content".getBytes(StandardCharsets.UTF_8);
        String objectKey = "documents/" + documentId;

        storage.store(objectKey, new ByteArrayInputStream(content), content.length, "text/plain");

        var opened = storage.open(objectKey);
        try (var stream = opened.content()) {
            assertEquals(content.length, opened.sizeBytes());
            assertEquals("text/plain", opened.contentType());
            assertArrayEquals(content, stream.readAllBytes());
        } finally {
            storage.delete(objectKey);
        }
    }

    @Test
    void shouldRejectInvalidApplicationCredentialsAgainstRealMinio() {
        MinioDocumentStorage storage = new MinioDocumentStorage(
            client(USER, "wrong-password"),
            FINAL_BUCKET
        );

        DocumentStorageException exception = assertThrows(
            DocumentStorageException.class,
            () -> storage.exists("documents/missing")
        );

        assertEquals(DocumentStorageException.FailureType.REJECTED, exception.getFailureType());
    }

    private static MinioClient client(String user, String password) {
        return MinioClient.builder()
            .endpoint("http://" + minio.getHost() + ":" + minio.getMappedPort(9000))
            .credentials(user, password)
            .build();
    }

    private static void createBucketIfNecessary(MinioClient client, String bucket) throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
