package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentRepository;
import com.dockflow.dockflow.document.DocumentService;
import com.dockflow.dockflow.document.application.DocumentContentValidator;
import com.dockflow.dockflow.document.application.DocumentLifecycleEventPublisher;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import com.dockflow.dockflow.document.storage.DocumentStorage;
import com.dockflow.dockflow.document.storage.DocumentStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentDeletedPublicationTest {

    private static final Instant NOW = Instant.parse("2026-10-29T13:00:00Z");

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentStorage documentStorage;

    @Mock
    private DocumentStagingRemover stagingRemover;

    @Mock
    private DocumentLifecycleEventPublisher lifecycleEventPublisher;

    @Test
    void publishesDeletedAfterManualDeletionCompletes() {
        Document document = document();
        when(documentRepository.findByIdForUpdate(document.getId())).thenReturn(Optional.of(document));

        manualService().delete(document.getId());

        ArgumentCaptor<DocumentLifecycleEvent> event = ArgumentCaptor.forClass(DocumentLifecycleEvent.class);
        InOrder order = inOrder(documentStorage, stagingRemover, documentRepository, lifecycleEventPublisher);
        order.verify(documentStorage).delete("documents/" + document.getId());
        order.verify(stagingRemover).delete("staging/" + document.getId());
        order.verify(documentRepository).delete(document);
        order.verify(documentRepository).flush();
        order.verify(lifecycleEventPublisher).publish(event.capture());
        assertEquals(DocumentLifecycleEventType.DOCUMENT_DELETED, event.getValue().type());
        assertEquals(document.getId(), event.getValue().documentId());
    }

    @Test
    void publishesDeletedAfterAutomaticDeletionCompletes() {
        Document document = document();
        when(documentRepository.findScheduledDeletionForUpdate(document.getId(), NOW))
            .thenReturn(Optional.of(document));

        DocumentDeletionMessageConsumer consumer = new DocumentDeletionMessageConsumer(
            documentRepository,
            documentStorage,
            stagingRemover,
            Optional.of(lifecycleEventPublisher),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        DocumentDeletionMessageConsumer.ProcessingResult result = consumer.process(
            new DocumentDeletionScheduledMessage(
                DocumentStorageRabbitTopology.DELETION_MESSAGE_SCHEMA_VERSION,
                document.getId()
            )
        );

        assertEquals(DocumentDeletionMessageConsumer.Disposition.ACKNOWLEDGE, result.disposition());
        ArgumentCaptor<DocumentLifecycleEvent> event = ArgumentCaptor.forClass(DocumentLifecycleEvent.class);
        InOrder order = inOrder(documentStorage, stagingRemover, documentRepository, lifecycleEventPublisher);
        order.verify(documentStorage).delete("documents/" + document.getId());
        order.verify(stagingRemover).delete("staging/" + document.getId());
        order.verify(documentRepository).delete(document);
        order.verify(documentRepository).flush();
        order.verify(lifecycleEventPublisher).publish(event.capture());
        assertEquals(DocumentLifecycleEventType.DOCUMENT_DELETED, event.getValue().type());
        assertEquals(NOW, event.getValue().occurredAt());
    }

    @Test
    void doesNotPublishDeletedWhenManualDeletionFails() {
        Document document = document();
        when(documentRepository.findByIdForUpdate(document.getId())).thenReturn(Optional.of(document));
        doThrow(
            new DocumentStorageException(DocumentStorageException.FailureType.UNAVAILABLE, "storage unavailable")
        ).when(documentStorage).delete("documents/" + document.getId());

        assertThrows(RuntimeException.class, () -> manualService().delete(document.getId()));

        verify(lifecycleEventPublisher, never()).publish(any());
        verify(documentRepository, never()).delete(any(Document.class));
    }

    private DocumentService manualService() {
        return new DocumentService(
            documentRepository,
            new DocumentContentValidator(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(documentStorage),
            Optional.of(stagingRemover),
            Optional.of(lifecycleEventPublisher)
        );
    }

    private Document document() {
        return new Document("document.pdf", "application/pdf", 10);
    }
}
