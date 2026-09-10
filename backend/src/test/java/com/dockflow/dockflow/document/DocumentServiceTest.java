package com.dockflow.dockflow.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import org.mockito.junit.jupiter.MockitoExtension;

import com.dockflow.dockflow.document.exception.DocumentNotFoundException;

import org.mockito.Mock;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository);
    }

    @Test
    void shouldRegisterDocument() {
        String originalFilename = "test_document.pdf";
        String contentType = "application/pdf";
        long sizeBytes = 1024;

        when(documentRepository.save(org.mockito.ArgumentMatchers.any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document resultDocument = documentService.registerDocument(originalFilename, contentType, sizeBytes);

        assertEquals(originalFilename, resultDocument.getOriginalFilename());
        assertEquals(contentType, resultDocument.getContentType()); 
        assertEquals(sizeBytes, resultDocument.getSizeBytes());
        assertEquals(DocumentStatus.PENDING, resultDocument.getStatus());

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        
        verify(documentRepository).save(documentCaptor.capture());

        Document savedDocument = documentCaptor.getValue();

        assertEquals(originalFilename, savedDocument.getOriginalFilename());
        assertEquals(contentType, savedDocument.getContentType());
        assertEquals(sizeBytes, savedDocument.getSizeBytes());
        assertEquals(DocumentStatus.PENDING, savedDocument.getStatus());
    }

    @Test
    void shouldRejectNegativeSize() {
        String originalFilename = "test_document.pdf";
        String contentType = "application/pdf";
        long sizeBytes = -1024;

        assertThrows(IllegalArgumentException.class, () -> {
            documentService.registerDocument(originalFilename, contentType, sizeBytes);
        });

        verify(documentRepository, never()).save(org.mockito.ArgumentMatchers.any(Document.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectInvalidOriginalFilename(String originalFilename) {
        String contentType = "application/pdf";
        long sizeBytes = 1024;

        assertThrows(IllegalArgumentException.class, () -> {
            documentService.registerDocument(originalFilename, contentType, sizeBytes);
        });

        verify(documentRepository, never()).save(org.mockito.ArgumentMatchers.any(Document.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectInvalidContentType(String contentType) {
        String originalFilename = "test_document.pdf";
        long sizeBytes = 1024;

        assertThrows(IllegalArgumentException.class, () -> {
            documentService.registerDocument(originalFilename, contentType, sizeBytes);
        });

        verify(documentRepository, never()).save(org.mockito.ArgumentMatchers.any(Document.class));
    }

    @Test 
    void shouldFindDocumentById() {
        UUID id = UUID.randomUUID();
        Document mockDocument = new Document("test_document.pdf", "application/pdf", 1024);

        when(documentRepository.findById(id)).thenReturn(java.util.Optional.of(mockDocument));

        Document foundDocument = documentService.findById(id);

        assertEquals(mockDocument, foundDocument);

        verify(documentRepository).findById(id);
    }

    @Test
    void shouldThrowExceptionWhenDocumentNotFound() {
        UUID id = UUID.randomUUID();

        when(documentRepository.findById(id)).thenReturn(java.util.Optional.empty());

        assertThrows(DocumentNotFoundException.class, () -> {
            documentService.findById(id);
        });
    }

    @Test
    void shouldCompleteDocumentOnlyAfterProcessingWithObjectReference() {
        Document document = new Document("test_document.pdf", "application/pdf", 1024);

        document.startProcessing();
        document.markCompleted("documents/123/content");

        assertEquals(DocumentStatus.COMPLETED, document.getStatus());
        assertEquals("documents/123/content", document.getObjectKey());
    }

    @Test
    void shouldRejectProcessingDocumentWithoutObjectReference() {
        Document document = new Document("test_document.pdf", "application/pdf", 1024);
        document.startProcessing();

        assertThrows(IllegalArgumentException.class, () -> document.markCompleted(" "));
        assertEquals(DocumentStatus.PROCESSING, document.getStatus());
        assertNull(document.getObjectKey());
    }

    @Test
    void shouldRejectConcurrentOrRepeatedProcessing() {
        Document document = new Document("test_document.pdf", "application/pdf", 1024);
        document.startProcessing();

        assertThrows(IllegalStateException.class, document::startProcessing);

        document.markCompleted("documents/123/content");
        assertThrows(IllegalStateException.class, document::startProcessing);
        assertThrows(IllegalStateException.class, () -> document.markCompleted("documents/456/content"));
    }

    @Test
    void shouldPreserveFailureTransitionAndRejectTerminalMutation() {
        Document document = new Document("test_document.pdf", "application/pdf", 1024);

        document.markFailed();

        assertEquals(DocumentStatus.FAILED, document.getStatus());
        assertThrows(IllegalStateException.class, document::startProcessing);
        assertThrows(IllegalStateException.class, document::markFailed);
    }

}
