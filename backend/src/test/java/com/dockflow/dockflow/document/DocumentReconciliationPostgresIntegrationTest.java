package com.dockflow.dockflow.document;

import com.dockflow.dockflow.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "docflow.messaging.enabled=false",
    "docflow.reconciliation.enabled=false",
    "spring.rabbitmq.username=test",
    "spring.rabbitmq.password=test",
    "docflow.storage.minio.endpoint=http://localhost:9000",
    "docflow.storage.minio.access-key=test-access",
    "docflow.storage.minio.secret-key=test-secret"
})
class DocumentReconciliationPostgresIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;

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
    void shouldAllowOnlyOneConcurrentReconciliationClaimAndPersistTheNextDate() throws Exception {
        UUID documentId = processingDocument();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Future<Boolean> first = executor.submit(() -> transaction.execute(status -> {
            Optional<Document> claimed = documentRepository.claimReconciliation(documentId, now, 5);
            assertTrue(claimed.isPresent());
            claimed.orElseThrow().beginReconciliationAttempt();
            claimed.orElseThrow().scheduleReconciliation(now.plusSeconds(60));
            documentRepository.saveAndFlush(claimed.orElseThrow());
            firstClaimed.countDown();
            await(releaseFirst);
            return true;
        }));

        assertTrue(firstClaimed.await(5, TimeUnit.SECONDS));

        Future<Boolean> second = executor.submit(() -> transaction.execute(status ->
            documentRepository.claimReconciliation(documentId, now, 5).isPresent()
        ));

        assertFalse(second.get(5, TimeUnit.SECONDS));
        releaseFirst.countDown();
        assertTrue(first.get(5, TimeUnit.SECONDS));

        Document persisted = documentRepository.findById(documentId).orElseThrow();
        assertEquals(1L, persisted.getReconciliationAttempts());
        assertEquals(
            now.plusSeconds(60).truncatedTo(ChronoUnit.MICROS),
            persisted.getReconciliationNextAttemptAt()
        );
    }

    @Test
    void shouldRespectTheTenSecondProcessingLeaseAndAllowTakeoverAfterExpiry() {
        Document document = new Document("document.pdf", "application/pdf", 8);
        UUID documentId = document.getId();
        documentRepository.saveAndFlush(document);

        Instant claimedAt = Instant.parse("2026-09-19T12:00:00Z");

        assertEquals(1, documentRepository.claimForProcessing(documentId, claimedAt));
        assertEquals(0, documentRepository.claimForProcessing(documentId, claimedAt.plusSeconds(5)));
        assertEquals(1, documentRepository.claimForProcessing(documentId, claimedAt.plusSeconds(11)));

        Document persisted = documentRepository.findById(documentId).orElseThrow();
        assertEquals(DocumentStatus.PROCESSING, persisted.getStatus());
        assertNotNull(persisted.getObjectKey());
        assertEquals(claimedAt.plusSeconds(11), persisted.getUpdatedAt());
    }

    private UUID processingDocument() {
        Document document = new Document("document.pdf", "application/pdf", 8);
        document.startProcessing();
        document.registerStorageObjectKey("documents/" + document.getId());
        document.scheduleReconciliation(Instant.parse("2026-09-19T11:59:00Z"));
        documentRepository.saveAndFlush(document);
        return document.getId();
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", exception);
        }
    }
}
