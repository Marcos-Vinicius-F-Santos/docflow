package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentRepository;
import com.dockflow.dockflow.document.application.DocumentLifecycleEventPublisher;
import com.dockflow.dockflow.document.application.DocumentReconciliationService;
import com.dockflow.dockflow.document.application.DocumentDeletionPublisher;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingReader;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import com.dockflow.dockflow.document.storage.DocumentStorage;
import com.dockflow.dockflow.document.storage.DocumentStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentFailedPublicationTest {

    private static final Instant NOW = Instant.parse("2026-10-29T13:00:00Z");

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentStagingReader stagingReader;
    @Mock
    private DocumentStagingRemover stagingRemover;
    @Mock
    private DocumentStorage documentStorage;
    @Mock
    private DocumentLifecycleEventPublisher lifecyclePublisher;
    @Mock
    private DocumentDeletionPublisher deletionPublisher;

    @Test
    void publishesFailedAfterAnEffectiveStorageFailureIsPersisted() {
        UUID documentId = UUID.randomUUID();
        Document document = new Document(documentId, "document.pdf", "application/pdf", 8);
        DocumentStorageRequestedMessage message = new DocumentStorageRequestedMessage(
            1, documentId, "documents/" + documentId, "staging/" + documentId, 8, "application/pdf"
        );
        when(documentRepository.claimForProcessing(eq(documentId), eq(NOW))).thenAnswer(invocation -> {
            document.startProcessing();
            return 1;
        });
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentStorage.exists(message.objectKey())).thenThrow(new DocumentStorageException(
            DocumentStorageException.FailureType.REJECTED,
            "storage rejected"
        ));

        DocumentStorageMessageConsumer consumer = new DocumentStorageMessageConsumer(
            documentRepository,
            stagingReader,
            stagingRemover,
            documentStorage,
            Optional.empty(),
            Optional.of(lifecyclePublisher),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        DocumentStorageMessageConsumer.ProcessingResult result = consumer.process(message);

        assertEquals(DocumentStorageMessageConsumer.Disposition.DEAD_LETTER, result.disposition());
        assertEquals(1L, document.getFailureCount());
        verify(documentRepository).save(document);
        verify(documentRepository).flush();
        verify(lifecyclePublisher).publish(org.mockito.ArgumentMatchers.argThat(event ->
            event.type() == DocumentLifecycleEventType.DOCUMENT_FAILED
                && event.documentId().equals(documentId)
                && event.failureCount() == 1L
        ));
    }

    @Test
    void publishesFailedWhenReconciliationExhaustsItsAttempts() {
        UUID documentId = UUID.randomUUID();
        Document document = new Document(documentId, "document.pdf", "application/pdf", 8);
        document.registerStorageObjectKey("documents/" + documentId);
        document.startProcessing();
        document.scheduleReconciliation(NOW.minusSeconds(1));
        when(documentRepository.claimReconciliation(documentId, NOW, 1)).thenReturn(Optional.of(document));
        when(documentStorage.exists(document.getObjectKey())).thenReturn(false);

        DocumentReconciliationService service = new DocumentReconciliationService(
            documentRepository,
            documentStorage,
            1,
            Clock.fixed(NOW, ZoneOffset.UTC),
            List.of(Duration.ofMinutes(1)),
            Optional.of(lifecyclePublisher)
        );

        service.reconcile(documentId);

        assertEquals(1L, document.getFailureCount());
        verify(lifecyclePublisher).publish(org.mockito.ArgumentMatchers.argThat(event ->
            event.type() == DocumentLifecycleEventType.DOCUMENT_FAILED
                && event.failureCount() == 1L
        ));
    }

    @Test
    void schedulesTheWarningThirtyMinutesBeforeTheExclusionDeadlineOnTheThirdFailure() {
        UUID documentId = UUID.randomUUID();
        Document document = new Document(documentId, "document.pdf", "application/pdf", 8);
        document.markFailed(NOW);
        document.startRetryProcessing();
        document.markFailed(NOW);
        document.startRetryProcessing();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        DocumentStorageMessageConsumer consumer = new DocumentStorageMessageConsumer(
            documentRepository,
            stagingReader,
            stagingRemover,
            documentStorage,
            Optional.of(deletionPublisher),
            Optional.of(lifecyclePublisher),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        consumer.markFailedAfterPermanentFailure(documentId);

        verify(lifecyclePublisher).scheduleDeletionWarning(org.mockito.ArgumentMatchers.argThat(event ->
            event.type() == DocumentLifecycleEventType.DOCUMENT_DELETION_WARNING
                && event.failureCount() == 3L
                && event.exclusionScheduledAt().equals(NOW.plus(Duration.ofHours(3)))
                && event.occurredAt().equals(NOW.plus(Duration.ofHours(2).plusMinutes(30)))
        ));
        verify(deletionPublisher).publish(any(DocumentDeletionPublisher.DocumentDeletionScheduled.class));
    }
}
