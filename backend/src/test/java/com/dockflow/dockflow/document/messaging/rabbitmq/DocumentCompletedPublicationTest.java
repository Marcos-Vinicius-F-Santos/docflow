package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentRepository;
import com.dockflow.dockflow.document.application.DocumentLifecycleEventPublisher;
import com.dockflow.dockflow.document.application.DocumentReconciliationService;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingReader;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import com.dockflow.dockflow.document.storage.DocumentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentCompletedPublicationTest {

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

    @Test
    void publishesCompletedOnlyAfterStorageAndPersistenceAreConfirmed() {
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
        when(documentStorage.exists(message.objectKey())).thenReturn(false);
        when(stagingReader.open(message.contentReference())).thenReturn(
            new DocumentStagingReader.StagedContent(
                new ByteArrayInputStream("content".getBytes()),
                7,
                "application/pdf"
            )
        );

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

        assertEquals(DocumentStorageMessageConsumer.Disposition.ACKNOWLEDGE, result.disposition());
        assertEquals(com.dockflow.dockflow.document.DocumentStatus.COMPLETED, document.getStatus());
        verify(documentRepository).save(document);
        verify(documentRepository).flush();
        verify(lifecyclePublisher).publish(org.mockito.ArgumentMatchers.argThat(event ->
            event.type() == DocumentLifecycleEventType.DOCUMENT_COMPLETED
                && event.documentId().equals(documentId)
        ));
    }

    @Test
    void doesNotPublishCompletedWhenStorageDoesNotConfirm() {
        UUID documentId = UUID.randomUUID();
        Document document = new Document(documentId, "document.pdf", "application/pdf", 8);
        document.startProcessing();
        document.registerStorageObjectKey("documents/" + documentId);
        document.scheduleReconciliation(NOW.minusSeconds(1));
        when(documentRepository.claimReconciliation(documentId, NOW, 3)).thenReturn(Optional.of(document));
        when(documentStorage.exists(document.getObjectKey())).thenReturn(false);

        DocumentReconciliationService service = new DocumentReconciliationService(
            documentRepository,
            documentStorage,
            3,
            Clock.fixed(NOW, ZoneOffset.UTC),
            List.of(
                java.time.Duration.ofMinutes(1),
                java.time.Duration.ofMinutes(5),
                java.time.Duration.ofMinutes(15)
            ),
            Optional.of(lifecyclePublisher)
        );

        service.reconcile(documentId);

        verify(lifecyclePublisher, never()).publish(any());
    }
}
