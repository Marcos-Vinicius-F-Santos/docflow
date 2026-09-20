package com.dockflow.dockflow.document;
import com.dockflow.dockflow.document.application.DocumentContent;
import com.dockflow.dockflow.document.application.DocumentContentValidator;
import com.dockflow.dockflow.document.application.DocumentPublicationException;
import com.dockflow.dockflow.document.application.DocumentStorageRequestPublisher;
import com.dockflow.dockflow.document.exception.DocumentNotFoundException;
import com.dockflow.dockflow.document.port.out.storage.DocumentStaging;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final DocumentContentValidator contentValidator;
    private final Optional<DocumentStaging> documentStaging;
    private final Optional<DocumentStorageRequestPublisher> requestPublisher;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
        this.contentValidator = new DocumentContentValidator();
        this.documentStaging = Optional.empty();
        this.requestPublisher = Optional.empty();
    }

    @Autowired
    public DocumentService(
        DocumentRepository documentRepository,
        DocumentContentValidator contentValidator,
        Optional<DocumentStaging> documentStaging,
        Optional<DocumentStorageRequestPublisher> requestPublisher
    ) {
        this.documentRepository = documentRepository;
        this.contentValidator = contentValidator;
        this.documentStaging = documentStaging;
        this.requestPublisher = requestPublisher;
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

}
