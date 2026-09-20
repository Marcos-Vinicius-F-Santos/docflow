package com.dockflow.dockflow.document;

import com.dockflow.dockflow.document.adapter.out.storage.minio.MinioDocumentStaging;
import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentStorageMessageConsumer;
import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentStorageRequestedMessage;
import com.dockflow.dockflow.document.port.out.storage.DocumentStaging;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingReader;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import com.dockflow.dockflow.document.storage.DocumentStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers
@Import(DocumentStorageConcurrencyIntegrationTest.ConsumerTestConfiguration.class)
@SpringBootTest(properties = {
    "docflow.messaging.enabled=false",
    "docflow.reconciliation.enabled=false",
    "spring.rabbitmq.username=test",
    "spring.rabbitmq.password=test"
})
class DocumentStorageConcurrencyIntegrationTest {

    private static final String MINIO_USER = "docflow-concurrency";
    private static final String MINIO_PASSWORD = "docflow-concurrency-password";
    private static final String STAGING_BUCKET = "docflow-staging";
    private static final String FINAL_BUCKET = "docflow-documents";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Container
    static final GenericContainer<?> minio = new GenericContainer<>("quay.io/minio/minio:latest")
        .withEnv("MINIO_ROOT_USER", MINIO_USER)
        .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
        .withCommand("server", "/data", "--console-address", ":9001")
        .withExposedPorts(9000);

    @MockitoSpyBean
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentStorageMessageConsumer consumer;

    @Autowired
    private MinioDocumentStaging staging;

    @MockitoSpyBean
    private DocumentStorage documentStorage;

    private ExecutorService executor;

    @BeforeAll
    static void createBuckets() throws Exception {
        io.minio.MinioClient client = io.minio.MinioClient.builder()
            .endpoint(minioEndpoint())
            .credentials(MINIO_USER, MINIO_PASSWORD)
            .build();
        for (String bucket : new String[] {STAGING_BUCKET, FINAL_BUCKET}) {
            if (!client.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucket).build());
            }
        }
    }

    @DynamicPropertySource
    static void registerMinioProperties(DynamicPropertyRegistry registry) {
        registry.add("docflow.storage.minio.endpoint", DocumentStorageConcurrencyIntegrationTest::minioEndpoint);
        registry.add("docflow.storage.minio.access-key", () -> MINIO_USER);
        registry.add("docflow.storage.minio.secret-key", () -> MINIO_PASSWORD);
        registry.add("docflow.storage.staging-bucket", () -> STAGING_BUCKET);
        registry.add("docflow.storage.bucket", () -> FINAL_BUCKET);
    }

    @BeforeEach
    void cleanDocuments() {
        documentRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void shouldStoreOnlyOnceWhenTwoConsumersReceiveTheSameDocument() throws Exception {
        UUID documentId = UUID.randomUUID();
        byte[] content = "duplicate-message-content".getBytes(StandardCharsets.UTF_8);
        Document document = new Document(documentId, "document.pdf", "text/plain", content.length);
        document.registerStorageObjectKey("documents/" + documentId);
        documentRepository.saveAndFlush(document);
        DocumentStaging.StagedObject staged = staging.stage(
            documentId,
            new ByteArrayInputStream(content),
            "text/plain"
        );
        DocumentStorageRequestedMessage message = new DocumentStorageRequestedMessage(
            1,
            documentId,
            "documents/" + documentId,
            staged.contentReference(),
            content.length,
            "text/plain"
        );

        CountDownLatch storeEntered = new CountDownLatch(1);
        CountDownLatch releaseStore = new CountDownLatch(1);
        CountDownLatch secondClaimStarted = new CountDownLatch(1);
        AtomicInteger claimCount = new AtomicInteger();
        doAnswer(invocation -> {
            if (claimCount.incrementAndGet() == 2) {
                secondClaimStarted.countDown();
            }
            try {
                return invocation.callRealMethod();
            } catch (Throwable exception) {
                throw new IllegalStateException("claim invocation failed", exception);
            }
        }).when(documentRepository).claimForProcessing(any(UUID.class), any(java.time.Instant.class));
        doAnswer(invocation -> awaitThenCallRealMethod(invocation, storeEntered, releaseStore))
            .when(documentStorage)
            .store(anyString(), any(), anyLong(), anyString());

        Future<DocumentStorageMessageConsumer.ProcessingResult> first = executor.submit(
            () -> consumer.process(message)
        );
        assertTrue(storeEntered.await(10, TimeUnit.SECONDS));

        Future<DocumentStorageMessageConsumer.ProcessingResult> second = executor.submit(
            () -> consumer.process(message)
        );
        assertTrue(secondClaimStarted.await(10, TimeUnit.SECONDS));
        releaseStore.countDown();

        DocumentStorageMessageConsumer.ProcessingResult firstResult = first.get(15, TimeUnit.SECONDS);
        DocumentStorageMessageConsumer.ProcessingResult secondResult = second.get(15, TimeUnit.SECONDS);

        assertEquals(DocumentStorageMessageConsumer.Disposition.ACKNOWLEDGE, firstResult.disposition());
        assertEquals(DocumentStorageMessageConsumer.Disposition.ACKNOWLEDGE, secondResult.disposition());
        verify(documentStorage, times(1)).store(anyString(), any(), anyLong(), anyString());
        assertEquals(DocumentStatus.COMPLETED, documentRepository.findById(documentId).orElseThrow().getStatus());
        assertTrue(!objectExists(staged.contentReference()));
    }

    private Object awaitThenCallRealMethod(
        InvocationOnMock invocation,
        CountDownLatch entered,
        CountDownLatch release
    ) {
        entered.countDown();
        try {
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", exception);
        } catch (Throwable exception) {
            throw new IllegalStateException("real storage invocation failed", exception);
        }
    }

    private boolean objectExists(String objectKey) {
        try {
            return staging.client().statObject(io.minio.StatObjectArgs.builder()
                .bucket(STAGING_BUCKET)
                .object(objectKey)
                .build()) != null;
        } catch (io.minio.errors.ErrorResponseException exception) {
            return false;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String minioEndpoint() {
        return "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConsumerTestConfiguration {

        @Bean
        DocumentStorageMessageConsumer documentStorageMessageConsumer(
            DocumentRepository documentRepository,
            DocumentStagingReader stagingReader,
            DocumentStagingRemover stagingRemover,
            DocumentStorage documentStorage
        ) {
            return new DocumentStorageMessageConsumer(
                documentRepository,
                stagingReader,
                stagingRemover,
                documentStorage
            );
        }
    }
}
