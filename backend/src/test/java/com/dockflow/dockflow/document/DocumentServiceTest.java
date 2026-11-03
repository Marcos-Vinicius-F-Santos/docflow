package com.dockflow.dockflow.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

import org.mockito.junit.jupiter.MockitoExtension;

import com.dockflow.dockflow.document.exception.DocumentNotFoundException;
import com.dockflow.dockflow.document.application.DocumentContentValidator;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingReader;
import com.dockflow.dockflow.document.application.DocumentStorageRequestPublisher;
import com.dockflow.dockflow.document.storage.DocumentStorage;
import com.dockflow.dockflow.document.application.logging.ApplicationLogEvent;
import com.dockflow.dockflow.document.application.logging.ApplicationLogEventFactory;
import com.dockflow.dockflow.document.application.logging.ApplicationLogLocalWriter;
import com.dockflow.dockflow.document.application.logging.ApplicationLogRecorder;

import org.mockito.Mock;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.any;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentStorage documentStorage;
    @Mock
    private DocumentStagingRemover documentStagingRemover;
    @Mock
    private DocumentStagingReader documentStagingReader;
    @Mock
    private DocumentStorageRequestPublisher requestPublisher;
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
    void shouldEmitOperationalLogForAcceptedDocument() {
        when(documentRepository.save(org.mockito.ArgumentMatchers.any(Document.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        AtomicReference<ApplicationLogEvent> captured = new AtomicReference<>();
        documentService.configureOperationalLogging(recorder(captured), new ApplicationLogEventFactory());

        Document document = documentService.registerDocument("accepted.pdf", "application/pdf", 10);

        assertEquals("document.accepted", captured.get().operation());
        assertEquals(document.getId(), captured.get().documentId());
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
    void shouldListAllDocumentsWithoutFilteringStatuses() {
        Document pending = new Document("pending.txt", "text/plain", 1);
        Document processing = new Document("processing.txt", "text/plain", 2);
        Document completed = new Document("completed.txt", "text/plain", 3);
        Document failed = new Document("failed.txt", "text/plain", 4);
        processing.startProcessing();
        completed.startProcessing();
        completed.markCompleted("documents/completed");
        failed.markFailed();
        when(documentRepository.findAll()).thenReturn(java.util.List.of(pending, processing, completed, failed));

        var result = documentService.findAll();

        assertEquals(4, result.size());
        assertEquals(
            java.util.List.of(DocumentStatus.PENDING, DocumentStatus.PROCESSING,
                DocumentStatus.COMPLETED, DocumentStatus.FAILED),
            result.stream().map(Document::getStatus).toList()
        );
        verify(documentRepository).findAll();
    }

    @Test
    void shouldPropagateListRepositoryFailures() {
        when(documentRepository.findAll()).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> documentService.findAll());
    }

    @Test
    void shouldRetryAFailedDocumentUsingItsExistingStagingReference() throws Exception {
        Document document = new Document("document.txt", "text/plain", 4);
        document.markFailed(java.time.Instant.parse("2026-10-27T12:00:00Z"));
        when(documentRepository.findRetryCandidateForUpdate(document.getId()))
            .thenReturn(java.util.Optional.of(document));
        when(documentStagingReader.open("staging/" + document.getId()))
            .thenReturn(new DocumentStagingReader.StagedContent(
                new java.io.ByteArrayInputStream(new byte[] {1}),
                1,
                "text/plain"
            ));

        DocumentService service = new DocumentService(
            documentRepository,
            new DocumentContentValidator(),
            java.util.Optional.empty(),
            java.util.Optional.of(documentStagingReader),
            java.util.Optional.of(requestPublisher),
            java.util.Optional.empty(),
            java.util.Optional.empty()
        );

        Document retried = service.retry(document.getId());

        assertEquals(DocumentStatus.PROCESSING, retried.getStatus());
        assertEquals(1L, retried.getFailureCount());
        verify(documentRepository).saveAndFlush(document);
        verify(requestPublisher).publish(new DocumentStorageRequestPublisher.DocumentStorageRequested(
            1,
            document.getId(),
            "documents/" + document.getId(),
            "staging/" + document.getId(),
            4,
            "text/plain"
        ));
    }

    @Test
    void shouldRejectRetryWhenTheDocumentIsNotEligible() {
        Document document = new Document("document.txt", "text/plain", 4);
        when(documentRepository.findRetryCandidateForUpdate(document.getId()))
            .thenReturn(java.util.Optional.empty());
        when(documentRepository.findById(document.getId()))
            .thenReturn(java.util.Optional.of(document));

        DocumentService service = new DocumentService(
            documentRepository,
            new DocumentContentValidator(),
            java.util.Optional.empty(),
            java.util.Optional.of(documentStagingReader),
            java.util.Optional.of(requestPublisher),
            java.util.Optional.empty(),
            java.util.Optional.empty()
        );

        assertThrows(
            com.dockflow.dockflow.document.exception.DocumentRetryNotEligibleException.class,
            () -> service.retry(document.getId())
        );
        verifyNoInteractions(documentStagingReader, requestPublisher);
    }

    @Test
    void shouldRejectRetryWhenTheDocumentIsAlreadyProcessing() {
        Document document = new Document("document.txt", "text/plain", 4);
        document.startProcessing();
        when(documentRepository.findRetryCandidateForUpdate(document.getId()))
            .thenReturn(java.util.Optional.empty());
        when(documentRepository.findById(document.getId()))
            .thenReturn(java.util.Optional.of(document));

        DocumentService service = new DocumentService(
            documentRepository,
            new DocumentContentValidator(),
            java.util.Optional.empty(),
            java.util.Optional.of(documentStagingReader),
            java.util.Optional.of(requestPublisher),
            java.util.Optional.empty(),
            java.util.Optional.empty()
        );

        assertThrows(
            com.dockflow.dockflow.document.exception.DocumentRetryNotEligibleException.class,
            () -> service.retry(document.getId())
        );
        verifyNoInteractions(documentStagingReader, requestPublisher);
    }

    @Test
    void shouldRejectRetryWhenStagingCannotBeOpened() {
        Document document = new Document("document.txt", "text/plain", 4);
        document.markFailed(java.time.Instant.parse("2026-10-27T12:00:00Z"));
        when(documentRepository.findRetryCandidateForUpdate(document.getId()))
            .thenReturn(java.util.Optional.of(document));
        when(documentStagingReader.open("staging/" + document.getId()))
            .thenThrow(new com.dockflow.dockflow.document.storage.DocumentStorageException(
                com.dockflow.dockflow.document.storage.DocumentStorageException.FailureType.NOT_FOUND,
                "staging missing"
            ));

        DocumentService service = new DocumentService(
            documentRepository,
            new DocumentContentValidator(),
            java.util.Optional.empty(),
            java.util.Optional.of(documentStagingReader),
            java.util.Optional.of(requestPublisher),
            java.util.Optional.empty(),
            java.util.Optional.empty()
        );

        assertThrows(
            com.dockflow.dockflow.document.exception.DocumentRetryStorageUnavailableException.class,
            () -> service.retry(document.getId())
        );
        assertEquals(DocumentStatus.FAILED, document.getStatus());
        verifyNoInteractions(requestPublisher);
    }

    @Test
    void shouldLeaveRetryPublicationFailureToTransactionRollback() {
        Document document = new Document("document.txt", "text/plain", 4);
        document.markFailed(java.time.Instant.parse("2026-10-27T12:00:00Z"));
        when(documentRepository.findRetryCandidateForUpdate(document.getId()))
            .thenReturn(java.util.Optional.of(document));
        when(documentStagingReader.open("staging/" + document.getId()))
            .thenReturn(new DocumentStagingReader.StagedContent(
                new java.io.ByteArrayInputStream(new byte[] {1}),
                1,
                "text/plain"
            ));
        org.mockito.Mockito.doThrow(new com.dockflow.dockflow.document.application.DocumentPublicationException(
            document.getId(),
            "publication not confirmed"
        )).when(requestPublisher).publish(org.mockito.ArgumentMatchers.any());

        DocumentService service = new DocumentService(
            documentRepository,
            new DocumentContentValidator(),
            java.util.Optional.empty(),
            java.util.Optional.of(documentStagingReader),
            java.util.Optional.of(requestPublisher),
            java.util.Optional.empty(),
            java.util.Optional.empty()
        );

        assertThrows(
            com.dockflow.dockflow.document.application.DocumentPublicationException.class,
            () -> service.retry(document.getId())
        );
        verify(documentRepository).saveAndFlush(document);
    }

    @ParameterizedTest
    @EnumSource(DocumentStatus.class)
    void shouldDeleteDocumentsInEveryStatus(DocumentStatus status) {
        Document document = documentForStatus(status);
        when(documentRepository.findByIdForUpdate(document.getId())).thenReturn(java.util.Optional.of(document));
        DocumentService service = new DocumentService(
            documentRepository,
            new DocumentContentValidator(),
            java.util.Optional.empty(),
            java.util.Optional.empty(),
            java.util.Optional.of(documentStorage),
            java.util.Optional.of(documentStagingRemover)
        );

        service.delete(document.getId());

        String expectedObjectKey = document.getObjectKey() == null
            ? "documents/" + document.getId()
            : document.getObjectKey();
        verify(documentStorage).delete(expectedObjectKey);
        verify(documentStagingRemover).delete("staging/" + document.getId());
        verify(documentRepository).delete(document);
        verify(documentRepository).flush();
    }

    @Test
    void shouldDeleteFinalObjectStagingObjectAndDatabaseRow() {
        Document document = new Document("document.txt", "text/plain", 4);
        document.startProcessing();
        document.markCompleted("documents/" + document.getId());
        when(documentRepository.findByIdForUpdate(document.getId())).thenReturn(java.util.Optional.of(document));
        DocumentService service = new DocumentService(
            documentRepository,
            new DocumentContentValidator(),
            java.util.Optional.empty(),
            java.util.Optional.empty(),
            java.util.Optional.of(documentStorage),
            java.util.Optional.of(documentStagingRemover)
        );

        service.delete(document.getId());

        verify(documentStorage).delete("documents/" + document.getId());
        verify(documentStagingRemover).delete("staging/" + document.getId());
        verify(documentRepository).delete(document);
        verify(documentRepository).flush();
    }

    @Test
    void shouldEmitOperationalLogAfterDocumentDeletion() {
        Document document = new Document("document.txt", "text/plain", 4);
        when(documentRepository.findByIdForUpdate(document.getId())).thenReturn(java.util.Optional.of(document));
        AtomicReference<ApplicationLogEvent> captured = new AtomicReference<>();
        DocumentService service = new DocumentService(
            documentRepository,
            new DocumentContentValidator(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(documentStorage),
            Optional.of(documentStagingRemover)
        );
        service.configureOperationalLogging(recorder(captured), new ApplicationLogEventFactory());

        service.delete(document.getId());

        assertEquals("document.deleted", captured.get().operation());
        assertEquals(document.getId(), captured.get().documentId());
    }

    @Test
    void shouldNotDeleteDatabaseRowWhenFinalStorageDeletionFails() {
        Document document = new Document("document.txt", "text/plain", 4);
        when(documentRepository.findByIdForUpdate(document.getId())).thenReturn(java.util.Optional.of(document));
        org.mockito.Mockito.doThrow(new com.dockflow.dockflow.document.storage.DocumentStorageException(
            com.dockflow.dockflow.document.storage.DocumentStorageException.FailureType.UNAVAILABLE,
            "storage unavailable"
        )).when(documentStorage).delete(any(String.class));
        DocumentService service = new DocumentService(
            documentRepository,
            new DocumentContentValidator(),
            java.util.Optional.empty(),
            java.util.Optional.empty(),
            java.util.Optional.of(documentStorage),
            java.util.Optional.of(documentStagingRemover)
        );

        assertThrows(
            com.dockflow.dockflow.document.exception.DocumentDeletionStorageUnavailableException.class,
            () -> service.delete(document.getId())
        );

        verifyNoInteractions(documentStagingRemover);
        verify(documentRepository, never()).delete(any(Document.class));
    }

    @Test
    void shouldNotDeleteDatabaseRowWhenStagingDeletionFailsAfterFinalDeletion() {
        Document document = new Document("document.txt", "text/plain", 4);
        document.startProcessing();
        document.markCompleted("documents/" + document.getId());
        when(documentRepository.findByIdForUpdate(document.getId())).thenReturn(java.util.Optional.of(document));
        org.mockito.Mockito.doThrow(new com.dockflow.dockflow.document.storage.DocumentStorageException(
            com.dockflow.dockflow.document.storage.DocumentStorageException.FailureType.UNAVAILABLE,
            "staging unavailable"
        )).when(documentStagingRemover).delete("staging/" + document.getId());
        DocumentService service = new DocumentService(
            documentRepository,
            new DocumentContentValidator(),
            java.util.Optional.empty(),
            java.util.Optional.empty(),
            java.util.Optional.of(documentStorage),
            java.util.Optional.of(documentStagingRemover)
        );

        assertThrows(
            com.dockflow.dockflow.document.exception.DocumentDeletionStorageUnavailableException.class,
            () -> service.delete(document.getId())
        );

        verify(documentStorage).delete("documents/" + document.getId());
        verify(documentRepository, never()).delete(any(Document.class));
    }

    private Document documentForStatus(DocumentStatus status) {
        Document document = new Document("document-" + status + ".txt", "text/plain", 4);
        if (status == DocumentStatus.PROCESSING) {
            document.startProcessing();
        } else if (status == DocumentStatus.COMPLETED) {
            document.startProcessing();
            document.markCompleted("documents/" + document.getId());
        } else if (status == DocumentStatus.FAILED) {
            document.markFailed();
        }
        return document;
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

    private ApplicationLogRecorder recorder(AtomicReference<ApplicationLogEvent> captured) {
        ApplicationLogLocalWriter writer = captured::set;
        return new ApplicationLogRecorder(writer, Optional.empty(), Runnable::run);
    }

}
