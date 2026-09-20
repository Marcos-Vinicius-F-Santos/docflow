package com.dockflow.dockflow.document.application;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentRepository;
import com.dockflow.dockflow.document.DocumentStatus;
import com.dockflow.dockflow.document.storage.DocumentStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentReconciliationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentStorage documentStorage;

    @Test
    void shouldKeepProcessingWhenStorageDoesNotConfirmAndAttemptsRemain() {
        UUID documentId = UUID.randomUUID();
        String objectKey = "documents/unknown/content";
        Document document = processingDocument(objectKey);

        when(documentRepository.claimReconciliation(documentId, NOW, 3))
            .thenReturn(Optional.of(document));
        when(documentStorage.exists(objectKey)).thenReturn(false);

        DocumentReconciliationService service = serviceWithMaxAttempts(3);

        service.reconcile(documentId);

        assertEquals(DocumentStatus.PROCESSING, document.getStatus());
        assertEquals(1L, document.getReconciliationAttempts());
        assertEquals(NOW.plusSeconds(60), document.getReconciliationNextAttemptAt());
        verify(documentRepository).save(document);
        verify(documentStorage).exists(objectKey);
        verify(documentStorage, never()).store(any(), any(), anyLong(), any());
        verify(documentStorage, never()).delete(any());
    }

    @Test
    void shouldCompleteWhenStorageConfirmsObject() {
        UUID documentId = UUID.randomUUID();
        String objectKey = "documents/confirmed/content";
        Document document = processingDocument(objectKey);

        when(documentRepository.claimReconciliation(documentId, NOW, 3))
            .thenReturn(Optional.of(document));
        when(documentStorage.exists(objectKey)).thenReturn(true);

        DocumentReconciliationService service = serviceWithMaxAttempts(3);

        service.reconcile(documentId);

        assertEquals(DocumentStatus.COMPLETED, document.getStatus());
        assertEquals(objectKey, document.getObjectKey());
        assertEquals(1L, document.getReconciliationAttempts());
        assertNull(document.getReconciliationNextAttemptAt());
        verify(documentRepository).save(document);
        verify(documentStorage).exists(objectKey);
        verify(documentStorage, never()).store(any(), any(), anyLong(), any());
        verify(documentStorage, never()).delete(any());
    }

    @Test
    void shouldFailAfterLastUnconfirmedAttempt() {
        UUID documentId = UUID.randomUUID();
        String objectKey = "documents/failed/content";
        Document document = processingDocument(objectKey);
        document.registerReconciliationAttempt();
        document.registerReconciliationAttempt();
        document.registerReconciliationAttempt();
        document.registerReconciliationAttempt();

        when(documentRepository.claimReconciliation(documentId, NOW, 5))
            .thenReturn(Optional.of(document));
        when(documentStorage.exists(objectKey)).thenReturn(false);

        DocumentReconciliationService service = serviceWithMaxAttempts(5);

        service.reconcile(documentId);

        assertEquals(DocumentStatus.FAILED, document.getStatus());
        assertEquals(5L, document.getReconciliationAttempts());
        assertNull(document.getReconciliationNextAttemptAt());
        verify(documentRepository).save(document);
        verify(documentStorage).exists(objectKey);
    }

    @Test
    void shouldUseTheApprovedBackoffForEachUnconfirmedAttempt() {
        UUID documentId = UUID.randomUUID();
        Document document = processingDocument("documents/backoff/content");

        when(documentRepository.claimReconciliation(documentId, NOW, 5))
            .thenReturn(Optional.of(document));
        when(documentStorage.exists(document.getObjectKey())).thenReturn(false);

        DocumentReconciliationService service = serviceWithMaxAttempts(5);

        service.reconcile(documentId);
        assertEquals(NOW.plusSeconds(60), document.getReconciliationNextAttemptAt());

        service.reconcile(documentId);
        assertEquals(NOW.plusSeconds(5 * 60), document.getReconciliationNextAttemptAt());

        service.reconcile(documentId);
        assertEquals(NOW.plusSeconds(15 * 60), document.getReconciliationNextAttemptAt());

        service.reconcile(documentId);
        assertEquals(NOW.plusSeconds(30 * 60), document.getReconciliationNextAttemptAt());

        service.reconcile(documentId);
        assertEquals(DocumentStatus.FAILED, document.getStatus());
        assertNull(document.getReconciliationNextAttemptAt());
        assertEquals(5L, document.getReconciliationAttempts());
    }

    private DocumentReconciliationService serviceWithMaxAttempts(long maxAttempts) {
        return new DocumentReconciliationService(
            documentRepository,
            documentStorage,
            maxAttempts,
            CLOCK
        );
    }

    private Document processingDocument(String objectKey) {
        Document document = new Document("document.pdf", "application/pdf", 10);
        document.registerStorageObjectKey(objectKey);
        document.startProcessing();
        document.scheduleReconciliation(NOW.minusSeconds(1));
        return document;
    }
}
