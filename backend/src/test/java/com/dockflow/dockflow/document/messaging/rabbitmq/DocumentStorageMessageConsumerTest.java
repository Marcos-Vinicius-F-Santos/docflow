package com.dockflow.dockflow.document.messaging.rabbitmq;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentRepository;
import com.dockflow.dockflow.document.DocumentStatus;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingReader;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import com.dockflow.dockflow.document.storage.DocumentStorage;
import com.dockflow.dockflow.document.storage.DocumentStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentStorageMessageConsumerTest {

    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentStagingReader stagingReader;

    @Mock
    private DocumentStagingRemover stagingRemover;

    @Mock
    private DocumentStorage documentStorage;

    @Test
    void shouldClaimReadStoreRemoveAndCompleteTheDocument() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId);
        byte[] content = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        DocumentStorageRequestedMessage message = message(documentId, content.length);

        when(documentRepository.claimForProcessing(eq(documentId), eq(NOW))).thenAnswer(invocation -> {
            document.startProcessing();
            return 1;
        });
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentStorage.exists(message.objectKey())).thenReturn(false);
        when(stagingReader.open(message.contentReference())).thenReturn(
            new DocumentStagingReader.StagedContent(
                new ByteArrayInputStream(content),
                content.length,
                "application/pdf"
            )
        );

        DocumentStorageMessageConsumer consumer = consumer();
        DocumentStorageMessageConsumer.ProcessingResult result = consumer.process(message);

        assertEquals(DocumentStorageMessageConsumer.Disposition.ACKNOWLEDGE, result.disposition());
        assertEquals(DocumentStatus.COMPLETED, document.getStatus());
        assertEquals(message.objectKey(), document.getObjectKey());

        InOrder order = inOrder(documentRepository, documentStorage, stagingReader, stagingRemover);
        order.verify(documentRepository).claimForProcessing(documentId, NOW);
        order.verify(documentStorage).exists(message.objectKey());
        order.verify(stagingReader).open(message.contentReference());
        order.verify(documentStorage).store(
            eq(message.objectKey()),
            any(),
            eq((long) content.length),
            eq("application/pdf")
        );
        order.verify(stagingRemover).delete(message.contentReference());
        verify(documentRepository).save(document);
    }

    @Test
    void shouldNotStoreOrRemoveWhenTheFinalObjectAlreadyExists() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId);
        DocumentStorageRequestedMessage message = message(documentId, 8);

        when(documentRepository.claimForProcessing(eq(documentId), eq(NOW))).thenAnswer(invocation -> {
            document.startProcessing();
            return 1;
        });
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentStorage.exists(message.objectKey())).thenReturn(true);

        DocumentStorageMessageConsumer.ProcessingResult result = consumer().process(message);

        assertEquals(DocumentStorageMessageConsumer.Disposition.ACKNOWLEDGE, result.disposition());
        assertEquals(DocumentStatus.COMPLETED, document.getStatus());
        verify(documentStorage, never()).store(any(), any(), anyLong(), any());
        verify(stagingReader, never()).open(any());
        verify(stagingRemover, never()).delete(any());
    }

    @Test
    void shouldKeepStagingAndRequestRetryWhenFinalStorageFailsTransiently() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId);
        DocumentStorageRequestedMessage message = message(documentId, 8);

        when(documentRepository.claimForProcessing(eq(documentId), eq(NOW))).thenAnswer(invocation -> {
            document.startProcessing();
            return 1;
        });
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentStorage.exists(message.objectKey())).thenReturn(false);
        when(stagingReader.open(message.contentReference())).thenReturn(
            new DocumentStagingReader.StagedContent(
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                7,
                "text/plain"
            )
        );
        when(documentStorage.store(any(), any(), any(Long.class), any()))
            .thenThrow(new DocumentStorageException(
                DocumentStorageException.FailureType.UNAVAILABLE,
                "storage unavailable"
            ));

        DocumentStorageMessageConsumer.ProcessingResult result = consumer().process(message);

        assertEquals(DocumentStorageMessageConsumer.Disposition.RETRY, result.disposition());
        assertEquals(DocumentStatus.PROCESSING, document.getStatus());
        verify(stagingRemover, never()).delete(any());
        verify(documentRepository, never()).save(document);
    }

    @Test
    void shouldMarkTheDocumentFailedWhenStorageRejectsTheOperation() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId);
        DocumentStorageRequestedMessage message = message(documentId, 8);

        when(documentRepository.claimForProcessing(eq(documentId), eq(NOW))).thenAnswer(invocation -> {
            document.startProcessing();
            return 1;
        });
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentStorage.exists(message.objectKey())).thenThrow(new DocumentStorageException(
            DocumentStorageException.FailureType.REJECTED,
            "storage rejected the object"
        ));

        DocumentStorageMessageConsumer.ProcessingResult result = consumer().process(message);

        assertEquals(DocumentStorageMessageConsumer.Disposition.DEAD_LETTER, result.disposition());
        assertEquals(DocumentStatus.FAILED, document.getStatus());
        verify(documentRepository).save(document);
        verify(stagingRemover, never()).delete(any());
    }

    @Test
    void shouldKeepProcessingWhenStorageResultIsUnknown() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId);
        DocumentStorageRequestedMessage message = message(documentId, 8);

        when(documentRepository.claimForProcessing(eq(documentId), eq(NOW))).thenAnswer(invocation -> {
            document.startProcessing();
            return 1;
        });
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentStorage.exists(message.objectKey())).thenThrow(new DocumentStorageException(
            DocumentStorageException.FailureType.RESULT_UNKNOWN,
            "storage result is unknown"
        ));

        DocumentStorageMessageConsumer.ProcessingResult result = consumer().process(message);

        assertEquals(DocumentStorageMessageConsumer.Disposition.RETRY, result.disposition());
        assertEquals(DocumentStatus.PROCESSING, document.getStatus());
        assertTrue(!result.shouldMarkFailedOnRetryExhaustion());
        verify(documentRepository, never()).save(document);
    }

    @Test
    void shouldAcknowledgeCompletedDuplicateWithoutReadingOrWriting() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId);
        document.startProcessing();
        document.markCompleted("documents/" + documentId);
        DocumentStorageRequestedMessage message = message(documentId, 8);

        when(documentRepository.claimForProcessing(eq(documentId), eq(NOW))).thenReturn(0);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        DocumentStorageMessageConsumer.ProcessingResult result = consumer().process(message);

        assertEquals(DocumentStorageMessageConsumer.Disposition.ACKNOWLEDGE, result.disposition());
        verifyNoStorageInteractions();
    }

    @Test
    void shouldRequestRetryWhenAnotherConsumerOwnsTheLease() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId);
        document.startProcessing();
        DocumentStorageRequestedMessage message = message(documentId, 8);

        when(documentRepository.claimForProcessing(eq(documentId), eq(NOW))).thenReturn(0);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        DocumentStorageMessageConsumer.ProcessingResult result = consumer().process(message);

        assertEquals(DocumentStorageMessageConsumer.Disposition.RETRY, result.disposition());
        verifyNoStorageInteractions();
    }

    @Test
    void shouldRejectAnInvalidSchemaBeforeClaiming() {
        DocumentStorageRequestedMessage invalid = new DocumentStorageRequestedMessage(
            99,
            UUID.randomUUID(),
            "documents/object",
            "staging/object",
            1,
            "text/plain"
        );

        assertThrows(DocumentStorageMessageValidationException.class, () -> consumer().process(invalid));
        verify(documentRepository, never()).claimForProcessing(any(), any(Instant.class));
        verifyNoStorageInteractions();
    }

    @Test
    void shouldIncludeDocumentIdInStructuredConsumptionLogs() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId);
        document.startProcessing();
        DocumentStorageRequestedMessage message = message(documentId, 8);

        when(documentRepository.claimForProcessing(eq(documentId), eq(NOW))).thenReturn(0);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        Logger logger = (Logger) LoggerFactory.getLogger(DocumentStorageMessageConsumer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            consumer().process(message);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertTrue(appender.list.stream()
            .flatMap(event -> event.getKeyValuePairs().stream())
            .anyMatch(pair -> "documentId".equals(pair.key)
                && documentId.toString().equals(String.valueOf(pair.value))));
    }

    private DocumentStorageMessageConsumer consumer() {
        return new DocumentStorageMessageConsumer(
            documentRepository,
            stagingReader,
            stagingRemover,
            documentStorage,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private Document document(UUID id) {
        Document document = new Document(id, "document.pdf", "application/pdf", 8);
        return document;
    }

    private DocumentStorageRequestedMessage message(UUID id, long size) {
        return new DocumentStorageRequestedMessage(
            1,
            id,
            "documents/" + id,
            "staging/" + id,
            size,
            "application/pdf"
        );
    }

    private void verifyNoStorageInteractions() {
        verify(documentStorage, never()).exists(any());
        verify(documentStorage, never()).store(any(), any(), anyLong(), any());
        verify(stagingReader, never()).open(any());
        verify(stagingRemover, never()).delete(any());
    }
}
