package com.dockflow.dockflow.document;
import com.dockflow.dockflow.document.application.DocumentContent;
import com.dockflow.dockflow.document.application.DocumentContentValidator;
import com.dockflow.dockflow.document.application.DocumentDownload;
import com.dockflow.dockflow.document.application.DocumentPublicationException;
import com.dockflow.dockflow.document.application.DocumentStorageRequestPublisher;
import com.dockflow.dockflow.document.exception.DocumentContentNotAvailableException;
import com.dockflow.dockflow.document.exception.DocumentContentNotFoundException;
import com.dockflow.dockflow.document.exception.DocumentContentStorageUnavailableException;
import com.dockflow.dockflow.document.exception.DocumentDeletionException;
import com.dockflow.dockflow.document.exception.DocumentDeletionStorageUnavailableException;
import com.dockflow.dockflow.document.exception.DocumentNotFoundException;
import com.dockflow.dockflow.document.exception.DocumentRetryStorageUnavailableException;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import com.dockflow.dockflow.document.port.out.storage.DocumentStaging;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingReader;
import com.dockflow.dockflow.document.storage.DocumentStorage;
import com.dockflow.dockflow.document.storage.DocumentStorageException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final DocumentContentValidator contentValidator;
    private final Optional<DocumentStaging> documentStaging;
    private final Optional<DocumentStagingReader> documentStagingReader;
    private final Optional<DocumentStorageRequestPublisher> requestPublisher;
    private final Optional<DocumentStorage> documentStorage;
    private final Optional<DocumentStagingRemover> documentStagingRemover;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
        this.contentValidator = new DocumentContentValidator();
        this.documentStaging = Optional.empty();
        this.documentStagingReader = Optional.empty();
        this.requestPublisher = Optional.empty();
        this.documentStorage = Optional.empty();
        this.documentStagingRemover = Optional.empty();
    }

    public DocumentService(
        DocumentRepository documentRepository,
        DocumentContentValidator contentValidator,
        Optional<DocumentStaging> documentStaging,
        Optional<DocumentStorageRequestPublisher> requestPublisher
    ) {
        this(
            documentRepository,
            contentValidator,
            documentStaging,
            Optional.empty(),
            requestPublisher,
            Optional.empty(),
            Optional.empty()
        );
    }

    public DocumentService(
        DocumentRepository documentRepository,
        DocumentContentValidator contentValidator,
        Optional<DocumentStaging> documentStaging,
        Optional<DocumentStorageRequestPublisher> requestPublisher,
        Optional<DocumentStorage> documentStorage,
        Optional<DocumentStagingRemover> documentStagingRemover
    ) {
        this(
            documentRepository,
            contentValidator,
            documentStaging,
            Optional.empty(),
            requestPublisher,
            documentStorage,
            documentStagingRemover
        );
    }

    @Autowired
    public DocumentService(
        DocumentRepository documentRepository,
        DocumentContentValidator contentValidator,
        Optional<DocumentStaging> documentStaging,
        Optional<DocumentStagingReader> documentStagingReader,
        Optional<DocumentStorageRequestPublisher> requestPublisher,
        Optional<DocumentStorage> documentStorage,
        Optional<DocumentStagingRemover> documentStagingRemover
    ) {
        this.documentRepository = documentRepository;
        this.contentValidator = contentValidator;
        this.documentStaging = documentStaging;
        this.documentStagingReader = documentStagingReader;
        this.requestPublisher = requestPublisher;
        this.documentStorage = documentStorage;
        this.documentStagingRemover = documentStagingRemover;
    }

    @Transactional
    public Document registerDocument(
        String originalFilename,
        String contentType,
        long sizeBytes
    ) {

        Document document = new Document(originalFilename, contentType, sizeBytes);
        return documentRepository.save(document);

    }

    /**
     * Registers received content after making it durable for asynchronous
     * processing. The publisher receives only the staging reference and
     * metadata, never the request stream.
     */
    @Transactional(noRollbackFor = DocumentPublicationException.class)
    public Document registerDocument(DocumentContent content) {
        DocumentStaging staging = documentStaging.orElseThrow(
            () -> new IllegalStateException("Document staging is not configured")
        );
        DocumentStorageRequestPublisher publisher = requestPublisher.orElseThrow(
            () -> new IllegalStateException("Document storage request publisher is not configured")
        );

        DocumentContent validatedContent = contentValidator.validate(content);
        UUID documentId = UUID.randomUUID();
        DocumentStaging.StagedObject staged = staging.stage(
            documentId,
            validatedContent.content(),
            validatedContent.contentType()
        );

        Document document = new Document(
            documentId,
            validatedContent.originalFilename(),
            validatedContent.contentType(),
            staged.sizeBytes()
        );
        Document saved = documentRepository.save(document);
        documentRepository.flush();

        publisher.publish(new DocumentStorageRequestPublisher.DocumentStorageRequested(
            1,
            documentId,
            "documents/" + documentId,
            staged.contentReference(),
            staged.sizeBytes(),
            staged.contentType()
        ));

        return saved;
    }

    @Transactional(readOnly = true)
    public Document findById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Document> findAll() {
        return documentRepository.findAll();
    }

    /**
     * Reuses the deterministic staging reference for one explicit retry. The
     * row lock and transaction make the state transition and publisher-confirm
     * failure atomic from PostgreSQL's point of view.
     */
    @Transactional
    public Document retry(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("documentId cannot be null");
        }

        Document document = documentRepository.findRetryCandidateForUpdate(id)
            .orElseGet(() -> documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id)));

        if (document.getStatus() != DocumentStatus.FAILED
            || document.getFailureCount() >= Document.MAX_PROCESSING_FAILURES
            || document.isExcluded()) {
            throw new com.dockflow.dockflow.document.exception.DocumentRetryNotEligibleException(id);
        }

        String contentReference = "staging/" + id;
        try (DocumentStagingReader.StagedContent ignored = documentStagingReader
            .orElseThrow(() -> new DocumentRetryStorageUnavailableException(id, null))
            .open(contentReference)) {
            // Opening the provider-neutral staging object is the existence check.
        } catch (DocumentStorageException exception) {
            throw new DocumentRetryStorageUnavailableException(id, exception);
        } catch (java.io.IOException exception) {
            throw new DocumentRetryStorageUnavailableException(id, exception);
        }

        document.startRetryProcessing();
        documentRepository.saveAndFlush(document);

        String objectKey = document.getObjectKey() == null || document.getObjectKey().isBlank()
            ? "documents/" + id
            : document.getObjectKey();
        requestPublisher
            .orElseThrow(() -> new IllegalStateException("Document storage request publisher is not configured"))
            .publish(new DocumentStorageRequestPublisher.DocumentStorageRequested(
                1,
                id,
                objectKey,
                contentReference,
                document.getSizeBytes(),
                document.getContentType()
            ));

        return document;
    }

    @Transactional(readOnly = true)
    public DocumentDownload download(UUID id) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new DocumentContentNotFoundException(id));
        if (document.getStatus() != DocumentStatus.COMPLETED) {
            throw new DocumentContentNotAvailableException(id, document.getStatus());
        }

        String objectKey = document.getObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            throw new DocumentContentNotFoundException(id);
        }

        try {
            DocumentStorage.StoredContent stored = documentStorage
                .orElseThrow(() -> new IllegalStateException("Document storage is not configured"))
                .open(objectKey);
            return new DocumentDownload(
                stored.content(),
                document.getSizeBytes(),
                document.getContentType(),
                document.getOriginalFilename()
            );
        } catch (DocumentStorageException exception) {
            if (exception.getFailureType() == DocumentStorageException.FailureType.NOT_FOUND) {
                throw new DocumentContentNotFoundException(id);
            }
            if (exception.getFailureType() == DocumentStorageException.FailureType.UNAVAILABLE
                || exception.getFailureType() == DocumentStorageException.FailureType.RESULT_UNKNOWN) {
                throw new DocumentContentStorageUnavailableException(id, exception);
            }
            throw exception;
        }
    }

    @Transactional
    public void delete(UUID id) {
        Document document = documentRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new DocumentNotFoundException(id));
        String finalObjectKey = document.getObjectKey() == null || document.getObjectKey().isBlank()
            ? "documents/" + id
            : document.getObjectKey();

        try {
            documentStorage
                .orElseThrow(() -> new IllegalStateException("Document storage is not configured"))
                .delete(finalObjectKey);
            documentStagingRemover
                .orElseThrow(() -> new IllegalStateException("Document staging remover is not configured"))
                .delete("staging/" + id);
        } catch (DocumentStorageException exception) {
            if (exception.getFailureType() == DocumentStorageException.FailureType.UNAVAILABLE
                || exception.getFailureType() == DocumentStorageException.FailureType.RESULT_UNKNOWN) {
                throw new DocumentDeletionStorageUnavailableException(id, exception);
            }
            throw new DocumentDeletionException(id, exception);
        }

        documentRepository.delete(document);
        documentRepository.flush();
    }

}
