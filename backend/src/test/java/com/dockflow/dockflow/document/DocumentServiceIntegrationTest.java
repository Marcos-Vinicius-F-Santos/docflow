package com.dockflow.dockflow.document;
import com.dockflow.dockflow.IntegrationTestBase;
import com.dockflow.dockflow.test.PostgresOnlyIntegrationTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingReader;
import com.dockflow.dockflow.document.application.DocumentContentValidator;
import com.dockflow.dockflow.document.application.DocumentStorageRequestPublisher;
import com.dockflow.dockflow.document.storage.DocumentStorage;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @MockitoBean
    private DocumentStorage documentStorage;

    @MockitoBean
    private DocumentStagingRemover documentStagingRemover;

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
        assertEquals(0L, persistedDocument.getFailureCount());
        assertFalse(persistedDocument.isExcluded());
        assertNull(persistedDocument.getExclusionScheduledAt());
        assertEquals(0L, persistedDocument.getVersion());
        assertNotNull(persistedDocument.getId());
        assertNotNull(persistedDocument.getCreatedAt());
        assertNotNull(persistedDocument.getUpdatedAt());

    }

    @Test
    void shouldReadLegacyDocumentsWithV6Defaults() {
        UUID legacyId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO documents "
                + "(id, tenant_id, owner_subject, status, original_filename, content_type, size_bytes, created_at, updated_at) "
                + "VALUES (?, '00000000-0000-0000-0000-000000000001', 'bootstrap', 'PENDING', 'legacy.txt', 'text/plain', 1, now(), now())",
            legacyId
        );

        Document persistedDocument = documentRepository.findById(legacyId).orElseThrow();

        assertEquals(0L, persistedDocument.getFailureCount());
        assertFalse(persistedDocument.isExcluded());
        assertNull(persistedDocument.getExclusionScheduledAt());
    }

    @Test
    void shouldExposeTheV6PartialIndexForScheduledExclusions() {
        Integer matchingIndexes = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_indexes "
                + "WHERE tablename = 'documents' "
                + "AND indexname = 'idx_documents_exclusion_scheduled_at'",
            Integer.class
        );

        assertEquals(1, matchingIndexes);
    }

    @Test
    void shouldEnforceV6RetryAndDeletionInvariantsAtTheDatabaseBoundary() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
            "INSERT INTO documents "
                + "(id, tenant_id, owner_subject, status, original_filename, content_type, size_bytes, "
                + "failure_count, created_at, updated_at) "
                + "VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'bootstrap', 'FAILED', 'negative.txt', 'text/plain', 1, -1, now(), now())"
        ));

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
            "INSERT INTO documents "
                + "(id, tenant_id, owner_subject, status, original_filename, content_type, size_bytes, "
                + "failure_count, is_excluded, created_at, updated_at) "
                + "VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'bootstrap', 'FAILED', 'early.txt', 'text/plain', 1, 2, TRUE, now(), now())"
        ));

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
            "INSERT INTO documents "
                + "(id, tenant_id, owner_subject, status, original_filename, content_type, size_bytes, "
                + "failure_count, exclusion_scheduled_at, created_at, updated_at) "
                + "VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'bootstrap', 'FAILED', 'unscheduled.txt', 'text/plain', 1, 1, now(), now(), now())"
        ));
    }

    @Test
    void shouldRejectCompletedDocumentWithoutObjectReference() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
            "INSERT INTO documents (id, tenant_id, owner_subject, status, original_filename, content_type, size_bytes, created_at, updated_at) "
                + "VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'bootstrap', 'COMPLETED', 'invalid.pdf', 'application/pdf', 1, now(), now())"));
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
    @Transactional
    void shouldLockOnlyRetryEligibleDocuments() {
        Document retryable = documentService.registerDocument("retryable.pdf", "application/pdf", 1);
        retryable.markFailed(Instant.parse("2026-10-27T12:00:00Z"));
        documentRepository.saveAndFlush(retryable);

        Document processing = documentService.registerDocument("processing.pdf", "application/pdf", 1);
        processing.startProcessing();
        documentRepository.saveAndFlush(processing);

        Document exhausted = documentService.registerDocument("exhausted.pdf", "application/pdf", 1);
        exhausted.markFailed(Instant.parse("2026-10-27T12:00:00Z"));
        exhausted.startRetryProcessing();
        exhausted.markFailed(Instant.parse("2026-10-27T12:01:00Z"));
        exhausted.startRetryProcessing();
        exhausted.markFailed(Instant.parse("2026-10-27T12:02:00Z"));
        documentRepository.saveAndFlush(exhausted);

        assertTrue(documentRepository.findRetryCandidateForUpdate(retryable.getId()).isPresent());
        assertTrue(documentRepository.findRetryCandidateForUpdate(processing.getId()).isEmpty());
        assertTrue(documentRepository.findRetryCandidateForUpdate(exhausted.getId()).isEmpty());
    }

    @Test
    @Transactional
    void shouldClaimDueScheduledDeletionAndIgnoreFutureSchedule() {
        Instant failureTime = Instant.now().minus(Duration.ofHours(4));
        Document due = documentService.registerDocument("due.pdf", "application/pdf", 1);
        due.markFailed(failureTime);
        due.startRetryProcessing();
        due.markFailed(failureTime.plusSeconds(1));
        due.startRetryProcessing();
        due.markFailed(failureTime.plusSeconds(2));
        documentRepository.saveAndFlush(due);

        Instant futureFailure = Instant.now();
        Document future = documentService.registerDocument("future.pdf", "application/pdf", 1);
        future.markFailed(futureFailure);
        future.startRetryProcessing();
        future.markFailed(futureFailure.plusSeconds(1));
        future.startRetryProcessing();
        future.markFailed(futureFailure.plusSeconds(2));
        documentRepository.saveAndFlush(future);

        Document claimed = documentRepository.claimNextScheduledDeletion(Instant.now()).orElseThrow();

        assertEquals(due.getId(), claimed.getId());
        assertTrue(claimed.isExcluded());
        assertTrue(claimed.getExclusionScheduledAt().isBefore(Instant.now()));
    }

    @Test
    void shouldAllowOnlyOneRetryClaimWhenConcurrentTransactionsRace() throws Exception {
        Document document = documentService.registerDocument("concurrent-retry.pdf", "application/pdf", 1);
        document.markFailed(Instant.parse("2026-10-27T12:00:00Z"));
        documentRepository.saveAndFlush(document);
        UUID documentId = document.getId();

        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                Document candidate = documentRepository.findRetryCandidateForUpdate(documentId).orElseThrow();
                candidate.startRetryProcessing();
                documentRepository.saveAndFlush(candidate);
                firstClaimed.countDown();
                await(releaseFirst);
                return true;
            }));

            assertTrue(firstClaimed.await(5, TimeUnit.SECONDS));

            Future<Boolean> second = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status ->
                documentRepository.findRetryCandidateForUpdate(documentId).isPresent()
            ));

            Thread.sleep(200);
            assertFalse(second.isDone());
            releaseFirst.countDown();

            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertFalse(second.get(5, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldPublishOnlyOneRetryWhenTwoTransactionsRaceForTheSameDocument() throws Exception {
        Document document = documentService.registerDocument("concurrent-publish.pdf", "application/pdf", 1);
        document.markFailed(Instant.parse("2026-10-27T12:00:00Z"));
        documentRepository.saveAndFlush(document);

        DocumentStagingReader stagingReader = mock(DocumentStagingReader.class);
        DocumentStorageRequestPublisher requestPublisher = mock(DocumentStorageRequestPublisher.class);
        when(stagingReader.open("staging/" + document.getId())).thenReturn(
            new DocumentStagingReader.StagedContent(
                new java.io.ByteArrayInputStream(new byte[] {1}),
                1,
                "application/pdf"
            )
        );

        DocumentService retryService = new DocumentService(
            documentRepository,
            new DocumentContentValidator(),
            Optional.empty(),
            Optional.of(stagingReader),
            Optional.of(requestPublisher),
            Optional.empty(),
            Optional.empty()
        );
        CountDownLatch published = new CountDownLatch(1);
        CountDownLatch releasePublisher = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            published.countDown();
            await(releasePublisher);
            return null;
        }).when(requestPublisher).publish(any(DocumentStorageRequestPublisher.DocumentStorageRequested.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Document> first = executor.submit(() -> new TransactionTemplate(transactionManager)
                .execute(status -> retryService.retry(document.getId())));
            assertTrue(published.await(5, TimeUnit.SECONDS));

            Future<Document> second = executor.submit(() -> new TransactionTemplate(transactionManager)
                .execute(status -> retryService.retry(document.getId())));

            Thread.sleep(200);
            assertFalse(second.isDone());
            releasePublisher.countDown();

            assertEquals(DocumentStatus.PROCESSING, first.get(5, TimeUnit.SECONDS).getStatus());
            assertThrows(java.util.concurrent.ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));
            verify(requestPublisher, times(1)).publish(any(DocumentStorageRequestPublisher.DocumentStorageRequested.class));
        } finally {
            releasePublisher.countDown();
            executor.shutdownNow();
        }
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

    @Test
    void shouldWaitForProcessingTransactionBeforeDeletingTheDocument() throws Exception {
        Document document = documentService.registerDocument("delete-during-processing.pdf", "application/pdf", 10);
        UUID documentId = document.getId();
        CountDownLatch processingClaimed = new CountDownLatch(1);
        CountDownLatch releaseProcessing = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> processing = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                assertEquals(1, documentRepository.claimForProcessing(documentId, Instant.parse("2026-09-20T12:00:00Z")));
                processingClaimed.countDown();
                await(releaseProcessing);
                return true;
            }));

            assertTrue(processingClaimed.await(5, TimeUnit.SECONDS));
            Future<?> deletion = executor.submit(() -> documentService.delete(documentId));

            Thread.sleep(200);
            assertFalse(deletion.isDone());
            releaseProcessing.countDown();

            deletion.get(5, TimeUnit.SECONDS);
            processing.get(5, TimeUnit.SECONDS);
            assertTrue(documentRepository.findById(documentId).isEmpty());
        } finally {
            releaseProcessing.countDown();
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
