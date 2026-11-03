package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentRepository;
import com.dockflow.dockflow.document.DocumentService;
import com.dockflow.dockflow.document.application.DocumentLifecycleEventPublisher;
import com.dockflow.dockflow.document.application.DocumentStorageRequestPublisher;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingReader;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import com.dockflow.dockflow.document.storage.DocumentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingStartedPublicationTest {

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
    private DocumentStorageRequestPublisher requestPublisher;

    @Test
    void publishesStartedAfterTheStorageClaimIsAccepted() {
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
        when(documentStorage.exists(message.objectKey())).thenReturn(true);

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
        verify(lifecyclePublisher).publish(org.mockito.ArgumentMatchers.argThat(event ->
            event.type() == DocumentLifecycleEventType.DOCUMENT_PROCESSING_STARTED
                && event.documentId().equals(documentId)
                && event.occurredAt().equals(NOW)
        ));
    }

    @Test
    void publishesStartedAfterAnExplicitRetryTransition() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = new Document(documentId, "document.pdf", "application/pdf", 8);
        document.markFailed(NOW);
        when(documentRepository.findRetryCandidateForUpdate(documentId)).thenReturn(Optional.of(document));
        when(stagingReader.open("staging/" + documentId)).thenReturn(
            new DocumentStagingReader.StagedContent(
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                7,
                "application/pdf"
            )
        );

        DocumentService service = new DocumentService(
            documentRepository,
            new com.dockflow.dockflow.document.application.DocumentContentValidator(),
            Optional.empty(),
            Optional.of(stagingReader),
            Optional.of(requestPublisher),
            Optional.empty(),
            Optional.empty(),
            Optional.of(lifecyclePublisher)
        );

        service.retry(documentId);

        verify(lifecyclePublisher).publish(org.mockito.ArgumentMatchers.argThat(event ->
            event.type() == DocumentLifecycleEventType.DOCUMENT_PROCESSING_STARTED
                && event.documentId().equals(documentId)
        ));
        verify(requestPublisher).publish(any(DocumentStorageRequestPublisher.DocumentStorageRequested.class));
    }
}
