package com.dockflow.dockflow.document;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class DocumentRegistrationEndToEndTest {

    private static final String MINIO_USER = "docflow";
    private static final String MINIO_PASSWORD = "docflow-test-password";
    private static final String STAGING_BUCKET = "docflow-staging";
    private static final String FINAL_BUCKET = "docflow-documents";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Container
    static final GenericContainer<?> minio = new GenericContainer<>("quay.io/minio/minio:latest")
        .withEnv("MINIO_ROOT_USER", MINIO_USER)
        .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
        .withCommand("server", "/data", "--console-address", ":9001")
        .withExposedPorts(9000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @BeforeAll
    static void createBuckets() throws Exception {
        MinioClient client = minioClient();
        createBucketIfNecessary(client, STAGING_BUCKET);
        createBucketIfNecessary(client, FINAL_BUCKET);
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
        registry.add("docflow.storage.minio.endpoint", () -> minioEndpoint());
        registry.add("docflow.storage.minio.access-key", () -> MINIO_USER);
        registry.add("docflow.storage.minio.secret-key", () -> MINIO_PASSWORD);
        registry.add("docflow.storage.staging-bucket", () -> STAGING_BUCKET);
        registry.add("docflow.storage.bucket", () -> FINAL_BUCKET);
    }

    @Test
    void shouldRegisterPublishConsumeStoreAndExposeTheCompletedDocument() throws Exception {
        documentRepository.deleteAll();
        byte[] content = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "document.pdf",
            "application/pdf",
            content
        );

        String location = mockMvc.perform(multipart("/documents").file(file))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/documents/")))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.sizeBytes").value(content.length))
            .andReturn()
            .getResponse()
            .getHeader("Location");

        UUID documentId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        Document completed = awaitCompleted(documentId);

        assertEquals(DocumentStatus.COMPLETED, completed.getStatus());
        assertEquals("documents/" + documentId, completed.getObjectKey());
        assertEquals(content.length, completed.getSizeBytes());

        MinioClient storage = minioClient();
        assertTrue(storage.statObject(StatObjectArgs.builder()
            .bucket(FINAL_BUCKET)
            .object(completed.getObjectKey())
            .build()).size() == content.length);
        assertFalse(objectExists(storage, STAGING_BUCKET, "staging/" + documentId));

        byte[] stored;
        try (var stream = storage.getObject(GetObjectArgs.builder()
            .bucket(FINAL_BUCKET)
            .object(completed.getObjectKey())
            .build())) {
            stored = stream.readAllBytes();
        }
        assertArrayEquals(content, stored);

        mockMvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.sizeBytes").value(content.length));
    }

    private Document awaitCompleted(UUID documentId) throws InterruptedException {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (System.nanoTime() < deadline) {
            var document = documentRepository.findById(documentId);
            if (document.isPresent() && document.get().getStatus() == DocumentStatus.COMPLETED) {
                return document.get();
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Document was not completed within the end-to-end timeout");
    }

    private static MinioClient minioClient() {
        return MinioClient.builder()
            .endpoint(minioEndpoint())
            .credentials(MINIO_USER, MINIO_PASSWORD)
            .build();
    }

    private static String minioEndpoint() {
        return "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
    }

    private static void createBucketIfNecessary(MinioClient client, String bucket) throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private static boolean objectExists(MinioClient client, String bucket, String object) throws Exception {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(object).build());
            return true;
        } catch (ErrorResponseException exception) {
            if (exception.response().code() == 404) {
                return false;
            }
            throw exception;
        }
    }
}
