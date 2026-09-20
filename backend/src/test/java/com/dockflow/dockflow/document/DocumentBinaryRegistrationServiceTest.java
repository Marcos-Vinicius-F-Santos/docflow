package com.dockflow.dockflow.document;

import com.dockflow.dockflow.document.application.DocumentContent;
import com.dockflow.dockflow.document.application.DocumentContentValidator;
import com.dockflow.dockflow.document.application.DocumentStorageRequestPublisher;
import com.dockflow.dockflow.document.application.DocumentPublicationException;
import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentValidationException;
import com.dockflow.dockflow.document.port.out.storage.DocumentStaging;
import com.dockflow.dockflow.document.storage.DocumentStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentBinaryRegistrationServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentStaging documentStaging;

    @Mock
    private DocumentStorageRequestPublisher requestPublisher;

    @Test
    void shouldStagePersistAndPublishOnlyAfterTheContentWasWritten() throws Exception {
        byte[] pdf = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII);
        DocumentStaging.StagedObject staged = new DocumentStaging.StagedObject(
            "staging/document",
            pdf.length,
            "application/pdf"
        );
        when(documentStaging.stage(any(UUID.class), any(InputStream.class), eq("application/pdf")))
            .thenAnswer(invocation -> {
                InputStream content = invocation.getArgument(1);
                try (content) {
                    assertArrayEquals(pdf, content.readAllBytes());
                }
                return staged;
            });
        when(documentRepository.save(any(Document.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentService service = service();
        Document result = service.registerDocument(new DocumentContent(
            "document.pdf",
            "application/pdf",
            new ByteArrayInputStream(pdf)
        ));

        assertEquals("document.pdf", result.getOriginalFilename());
        assertEquals("application/pdf", result.getContentType());
        assertEquals(pdf.length, result.getSizeBytes());
        assertEquals(DocumentStatus.PENDING, result.getStatus());

        InOrder order = inOrder(documentStaging, documentRepository, requestPublisher);
        order.verify(documentStaging).stage(any(UUID.class), any(InputStream.class), eq("application/pdf"));
        order.verify(documentRepository).save(any(Document.class));
        order.verify(requestPublisher).publish(any(DocumentStorageRequestPublisher.DocumentStorageRequested.class));

        ArgumentCaptor<DocumentStorageRequestPublisher.DocumentStorageRequested> requestCaptor =
            ArgumentCaptor.forClass(DocumentStorageRequestPublisher.DocumentStorageRequested.class);
        verify(requestPublisher).publish(requestCaptor.capture());
        DocumentStorageRequestPublisher.DocumentStorageRequested request = requestCaptor.getValue();
        assertEquals(result.getId(), request.documentId());
        assertEquals("documents/" + result.getId(), request.objectKey());
        assertEquals(staged.contentReference(), request.contentReference());
        assertEquals(pdf.length, request.sizeBytes());
        assertEquals("application/pdf", request.contentType());
    }

    @Test
    void shouldNotStagePersistOrPublishWhenValidationFails() {
        DocumentService service = service();

        assertThrows(
            DocumentContentValidationException.class,
            () -> service.registerDocument(new DocumentContent(
                "document.pdf",
                "text/plain",
                new ByteArrayInputStream("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))
            ))
        );

        verify(documentStaging, never()).stage(any(), any(), any());
        verify(documentRepository, never()).save(any());
        verify(requestPublisher, never()).publish(any());
    }

    @Test
    void shouldNotPersistOrPublishWhenStagingFails() {
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        when(documentStaging.stage(any(UUID.class), any(InputStream.class), eq("application/pdf")))
            .thenThrow(new DocumentStorageException(
                DocumentStorageException.FailureType.UNAVAILABLE,
                "Object storage is unavailable"
            ));

        DocumentService service = service();

        assertThrows(DocumentStorageException.class, () -> service.registerDocument(new DocumentContent(
            "document.pdf",
            "application/pdf",
            new ByteArrayInputStream(pdf)
        )));

        verify(documentRepository, never()).save(any());
        verify(requestPublisher, never()).publish(any());
    }

    @Test
    void shouldKeepTheDocumentPendingWhenPublicationIsNotConfirmed() {
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        DocumentStaging.StagedObject staged = new DocumentStaging.StagedObject(
            "staging/document",
            pdf.length,
            "application/pdf"
        );
        when(documentStaging.stage(any(UUID.class), any(InputStream.class), eq("application/pdf")))
            .thenReturn(staged);
        when(documentRepository.save(any(Document.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DocumentPublicationException(
            UUID.randomUUID(),
            "RabbitMQ did not confirm document publication"
        )).when(requestPublisher).publish(any());

        DocumentService service = service();

        assertThrows(DocumentPublicationException.class, () -> service.registerDocument(new DocumentContent(
            "document.pdf",
            "application/pdf",
            new ByteArrayInputStream(pdf)
        )));

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(documentCaptor.capture());
        assertEquals(DocumentStatus.PENDING, documentCaptor.getValue().getStatus());
        verify(documentRepository).flush();
        verify(requestPublisher).publish(any());
    }

    private DocumentService service() {
        return new DocumentService(
            documentRepository,
            new DocumentContentValidator(),
            Optional.of(documentStaging),
            Optional.of(requestPublisher)
        );
    }
}
