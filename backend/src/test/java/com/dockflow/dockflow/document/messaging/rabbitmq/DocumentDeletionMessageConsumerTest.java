package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentRepository;
import com.dockflow.dockflow.document.DocumentStatus;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import com.dockflow.dockflow.document.storage.DocumentStorage;
import com.dockflow.dockflow.document.storage.DocumentStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentDeletionMessageConsumerTest {

    private static final Instant NOW = Instant.parse("2026-09-16T15:00:00Z");

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentStorage documentStorage;

    @Mock
    private DocumentStagingRemover stagingRemover;

    @Test
    void shouldRemoveFinalObjectStagingAndMetadataOnlyAfterTheDueDocumentIsLocked() {
        UUID documentId = UUID.randomUUID();
        Document document = dueDocument(documentId);
        when(documentRepository.findScheduledDeletionForUpdate(documentId, NOW)).thenReturn(Optional.of(document));

        DocumentDeletionMessageConsumer.ProcessingResult result = consumer().process(message(documentId));

        assertEquals(DocumentDeletionMessageConsumer.Disposition.ACKNOWLEDGE, result.disposition());
        assertEquals(DocumentStatus.FAILED, document.getStatus());
        InOrder order = inOrder(documentStorage, stagingRemover, documentRepository);
        order.verify(documentStorage).delete("documents/" + documentId);
        order.verify(stagingRemover).delete("staging/" + documentId);
        order.verify(documentRepository).delete(document);
        order.verify(documentRepository).flush();
    }

    @Test
    void shouldAcknowledgeADeletionMessageWhenManualDeletionAlreadyRemovedTheDocument() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findScheduledDeletionForUpdate(documentId, NOW)).thenReturn(Optional.empty());
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        DocumentDeletionMessageConsumer.ProcessingResult result = consumer().process(message(documentId));

        assertEquals(DocumentDeletionMessageConsumer.Disposition.ACKNOWLEDGE, result.disposition());
        verifyNoInteractions(documentStorage, stagingRemover);
    }

    @Test
    void shouldDeadLetterWhenStorageDeletionFailsWithoutDeletingMetadata() {
        UUID documentId = UUID.randomUUID();
        Document document = dueDocument(documentId);
        when(documentRepository.findScheduledDeletionForUpdate(documentId, NOW)).thenReturn(Optional.of(document));
        org.mockito.Mockito.doThrow(new DocumentStorageException(
            DocumentStorageException.FailureType.UNAVAILABLE,
            "storage unavailable"
        )).when(documentStorage).delete("documents/" + documentId);

        DocumentDeletionMessageConsumer.ProcessingResult result = consumer().process(message(documentId));

        assertEquals(DocumentDeletionMessageConsumer.Disposition.DEAD_LETTER, result.disposition());
        verify(stagingRemover, never()).delete(any());
        verify(documentRepository, never()).delete(any(Document.class));
        verify(documentRepository, never()).flush();
    }

    @Test
    void shouldDeadLetterAStaleMessageForAnExistingDocument() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findScheduledDeletionForUpdate(documentId, NOW)).thenReturn(Optional.empty());
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(new Document(documentId, "x.txt", "text/plain", 1)));

        DocumentDeletionMessageConsumer.ProcessingResult result = consumer().process(message(documentId));

        assertEquals(DocumentDeletionMessageConsumer.Disposition.DEAD_LETTER, result.disposition());
        verifyNoInteractions(documentStorage, stagingRemover);
    }

    @Test
    void shouldRejectInvalidMessagesBeforeTouchingTheRepository() {
        assertThrows(DocumentDeletionMessageValidationException.class, () -> consumer().process(
            new DocumentDeletionScheduledMessage(99, UUID.randomUUID())
        ));
        verifyNoInteractions(documentRepository, documentStorage, stagingRemover);
    }

    private DocumentDeletionMessageConsumer consumer() {
        return new DocumentDeletionMessageConsumer(
            documentRepository,
            documentStorage,
            stagingRemover,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private DocumentDeletionScheduledMessage message(UUID documentId) {
        return new DocumentDeletionScheduledMessage(
            DocumentStorageRabbitTopology.DELETION_MESSAGE_SCHEMA_VERSION,
            documentId
        );
    }

    private Document dueDocument(UUID documentId) {
        Document document = new Document(documentId, "document.pdf", "application/pdf", 8);
        document.markFailed(NOW.minusSeconds(3 * 60 * 60));
        document.startRetryProcessing();
        document.markFailed(NOW.minusSeconds(3 * 60 * 60));
        document.startRetryProcessing();
        document.markFailed(NOW.minusSeconds(3 * 60 * 60));
        return document;
    }
}
