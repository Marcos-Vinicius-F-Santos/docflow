package com.dockflow.dockflow.document;
import com.dockflow.dockflow.IntegrationTestBase;
import com.dockflow.dockflow.test.PostgresOnlyIntegrationTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;

@PostgresOnlyIntegrationTest
class DocumentServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        documentRepository.deleteAll();
    }

    @Test
    void shouldPersistDocumentInDatabase() {

        String originalFilename = "integration_test_document.pdf";
        String contentType = "application/pdf";
        long sizeBytes = 2048;

        Document registeredDocument = documentService.registerDocument(originalFilename, contentType, sizeBytes);

        Document persistedDocument = documentRepository.findById(registeredDocument.getId()).orElseThrow();

        assertEquals(originalFilename, persistedDocument.getOriginalFilename());
        assertEquals(contentType, persistedDocument.getContentType());
        assertEquals(sizeBytes, persistedDocument.getSizeBytes());
        assertEquals(DocumentStatus.PENDING, persistedDocument.getStatus());
        assertNull(persistedDocument.getObjectKey());
        assertEquals(0L, persistedDocument.getReconciliationAttempts());
        assertEquals(0L, persistedDocument.getVersion());
        assertNotNull(persistedDocument.getId());
        assertNotNull(persistedDocument.getCreatedAt());
        assertNotNull(persistedDocument.getUpdatedAt());

    }

    @Test
    void shouldRejectCompletedDocumentWithoutObjectReference() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
            "INSERT INTO documents (id, status, original_filename, content_type, size_bytes, created_at, updated_at) "
                + "VALUES (gen_random_uuid(), 'COMPLETED', 'invalid.pdf', 'application/pdf', 1, now(), now())"));
    }

    @Test
    void shouldRejectDuplicateObjectReference() {
        Document first = documentService.registerDocument("first.pdf", "application/pdf", 1);
        first.startProcessing();
        first.markCompleted("documents/shared/content");
        documentRepository.saveAndFlush(first);

        Document second = documentService.registerDocument("second.pdf", "application/pdf", 1);
        second.startProcessing();
        second.markCompleted("documents/shared/content");

        assertThrows(DataIntegrityViolationException.class, () -> documentRepository.saveAndFlush(second));
    }

    @Test
    void shouldRejectStaleConcurrentStateTransition() {
        Document created = documentService.registerDocument("concurrent.pdf", "application/pdf", 1);

        Document first = documentRepository.findById(created.getId()).orElseThrow();
        Document second = documentRepository.findById(created.getId()).orElseThrow();

        first.startProcessing();
        documentRepository.saveAndFlush(first);

        second.startProcessing();
        assertThrows(ObjectOptimisticLockingFailureException.class,
            () -> documentRepository.saveAndFlush(second));
    }

    @Test
    void shouldPersistReconciliationAttemptCounter() {
        Document document = documentService.registerDocument(
            "reconciliation.pdf",
            "application/pdf",
            10
        );

        document.startProcessing();
        document.registerReconciliationAttempt();
        documentRepository.saveAndFlush(document);

        Document persistedDocument = documentRepository.findById(document.getId()).orElseThrow();

        assertEquals(DocumentStatus.PROCESSING, persistedDocument.getStatus());
        assertEquals(1L, persistedDocument.getReconciliationAttempts());
    }

    @Test
    void shouldAllowOnlyOneClaimBeforeTheLeaseExpiresAndAllowTakeoverAfterIt() {
        Document document = documentService.registerDocument("lease.pdf", "application/pdf", 10);
        Instant firstAttempt = Instant.parse("2026-09-16T12:00:00Z");

        assertEquals(1, documentRepository.claimForProcessing(document.getId(), firstAttempt));
        assertEquals(0, documentRepository.claimForProcessing(
            document.getId(),
            firstAttempt.plusMillis(9_999)
        ));
        assertEquals(1, documentRepository.claimForProcessing(
            document.getId(),
            firstAttempt.plusSeconds(10)
        ));

        Document claimed = documentRepository.findById(document.getId()).orElseThrow();
        assertEquals(DocumentStatus.PROCESSING, claimed.getStatus());
        assertEquals("documents/" + document.getId(), claimed.getObjectKey());
        assertEquals(firstAttempt.plusSeconds(10), claimed.getUpdatedAt());
    }

    @Test
    void shouldAllowOnlyOneConcurrentReconciliationClaim() throws Exception {
        Document document = documentService.registerDocument("reconciliation-claim.pdf", "application/pdf", 10);
        Instant now = Instant.parse("2026-09-18T12:00:00Z");
        document.startProcessing();
        document.registerStorageObjectKey("documents/" + document.getId());
        document.scheduleReconciliation(now.minusSeconds(1));
        documentRepository.saveAndFlush(document);

        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                boolean claimed = documentRepository
                    .claimReconciliation(document.getId(), now, 5)
                    .isPresent();
                firstClaimed.countDown();
                await(releaseFirst);
                return claimed;
            }));

            assertTrue(firstClaimed.await(5, TimeUnit.SECONDS));

            Future<Boolean> second = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status ->
                documentRepository.claimReconciliation(document.getId(), now, 5).isPresent()
            ));

            assertFalse(second.get(5, TimeUnit.SECONDS));
            releaseFirst.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the concurrent claim test", exception);
        }
    }

}
