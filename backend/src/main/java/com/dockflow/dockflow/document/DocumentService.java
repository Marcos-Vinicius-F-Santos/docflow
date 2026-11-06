package com.dockflow.dockflow.document;
import com.dockflow.dockflow.document.application.DocumentContent;
import com.dockflow.dockflow.document.application.DocumentContentValidator;
import com.dockflow.dockflow.document.application.DocumentDownload;
import com.dockflow.dockflow.document.application.DocumentLifecycleEventPublisher;
import com.dockflow.dockflow.document.application.DocumentPublicationException;
import com.dockflow.dockflow.document.application.DocumentStorageRequestPublisher;
import com.dockflow.dockflow.document.application.logging.ApplicationLogContext;
import com.dockflow.dockflow.document.application.logging.ApplicationLogEventFactory;
import com.dockflow.dockflow.document.application.logging.ApplicationLogLevel;
import com.dockflow.dockflow.document.application.logging.ApplicationLogRecorder;
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
import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEventMapper;
import com.dockflow.dockflow.document.observability.CorrelationIdContext;
import com.dockflow.dockflow.document.observability.DocumentMetrics;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import com.dockflow.dockflow.document.security.TenantIdentity;
import com.dockflow.dockflow.document.security.TenantIdentityService;

@Service
public class DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentContentValidator contentValidator;
    private final Optional<DocumentStaging> documentStaging;
    private final Optional<DocumentStagingReader> documentStagingReader;
    private final Optional<DocumentStorageRequestPublisher> requestPublisher;
    private final Optional<DocumentStorage> documentStorage;
    private final Optional<DocumentStagingRemover> documentStagingRemover;
    private final Optional<DocumentLifecycleEventPublisher> lifecycleEventPublisher;
    private final DocumentLifecycleEventMapper lifecycleEventMapper;
    private Optional<ApplicationLogRecorder> operationalLogRecorder = Optional.empty();
    private ApplicationLogEventFactory operationalLogEventFactory = new ApplicationLogEventFactory();
    private Optional<DocumentMetrics> metrics = Optional.empty();
    private TenantIdentityService tenantIdentityService = null;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
        this.contentValidator = new DocumentContentValidator();
        this.documentStaging = Optional.empty();
        this.documentStagingReader = Optional.empty();
        this.requestPublisher = Optional.empty();
        this.documentStorage = Optional.empty();
        this.documentStagingRemover = Optional.empty();
        this.lifecycleEventPublisher = Optional.empty();
        this.lifecycleEventMapper = new DocumentLifecycleEventMapper();
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
            documentStagingRemover,
            Optional.empty()
        );
    }

    public DocumentService(
        DocumentRepository documentRepository,
        DocumentContentValidator contentValidator,
        Optional<DocumentStaging> documentStaging,
        Optional<DocumentStagingReader> documentStagingReader,
        Optional<DocumentStorageRequestPublisher> requestPublisher,
        Optional<DocumentStorage> documentStorage,
        Optional<DocumentStagingRemover> documentStagingRemover
    ) {
        this(
            documentRepository,
            contentValidator,
            documentStaging,
            documentStagingReader,
            requestPublisher,
            documentStorage,
            documentStagingRemover,
            Optional.empty()
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
        Optional<DocumentStagingRemover> documentStagingRemover,
        Optional<DocumentLifecycleEventPublisher> lifecycleEventPublisher
    ) {
        this.documentRepository = documentRepository;
        this.contentValidator = contentValidator;
        this.documentStaging = documentStaging;
        this.documentStagingReader = documentStagingReader;
        this.requestPublisher = requestPublisher;
        this.documentStorage = documentStorage;
        this.documentStagingRemover = documentStagingRemover;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
        this.lifecycleEventMapper = new DocumentLifecycleEventMapper();
    }

    @Autowired
    void configureOperationalLogging(
        ApplicationLogRecorder recorder,
        ApplicationLogEventFactory eventFactory
    ) {
        this.operationalLogRecorder = Optional.of(recorder);
        this.operationalLogEventFactory = eventFactory;
    }

    @Autowired
    void configureMetrics(DocumentMetrics documentMetrics) {
        this.metrics = Optional.of(documentMetrics);
    }

    @Autowired
    void configureTenantIdentity(TenantIdentityService tenantIdentityService) {
        this.tenantIdentityService = tenantIdentityService;
    }

    private TenantIdentity currentIdentity() {
        if (tenantIdentityService == null) {
            return new TenantIdentity(
                TenantIdentityService.BOOTSTRAP_TENANT_ID,
                "bootstrap",
                TenantIdentity.Role.ADMIN
            );
        }
        return tenantIdentityService.current();
    }

    private java.util.Optional<Document> findScoped(UUID id) {
        if (tenantIdentityService != null && tenantIdentityService.isEnforced()) {
            return documentRepository.findByIdAndTenantId(id, currentIdentity().tenantId());
        }
        return documentRepository.findById(id);
    }

    @Transactional
    public Document registerDocument(
        String originalFilename,
        String contentType,
        long sizeBytes
    ) {

        Document document = new Document(originalFilename, contentType, sizeBytes);
        TenantIdentity identity = currentIdentity();
        document.assignTenant(identity.tenantId(), identity.subject());
        Document saved = documentRepository.save(document);
        record(
            ApplicationLogLevel.INFO,
            "document.accepted",
            "Document accepted for processing",
            saved.getId(),
            Map.of("contentType", String.valueOf(contentType), "sizeBytes", sizeBytes)
        );
        return saved;

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
        DocumentStaging.StagedObject staged;
        try {
            staged = staging.stage(
                documentId,
                validatedContent.content(),
                validatedContent.contentType()
            );
            metrics.ifPresent(value -> value.storage("staging_upload", "success"));
        } catch (DocumentStorageException exception) {
            metrics.ifPresent(value -> value.storage("staging_upload", exception.getFailureType().name().toLowerCase()));
            metrics.ifPresent(value -> value.upload("failed"));
            throw exception;
        }

        Document document = new Document(
            documentId,
            validatedContent.originalFilename(),
            validatedContent.contentType(),
            staged.sizeBytes()
        );
        TenantIdentity identity = currentIdentity();
        document.assignTenant(identity.tenantId(), identity.subject());
        Document saved = documentRepository.save(document);
        documentRepository.flush();
        record(
            ApplicationLogLevel.INFO,
            "document.accepted",
            "Document upload accepted",
            documentId,
            Map.of("contentType", String.valueOf(validatedContent.contentType()), "sizeBytes", staged.sizeBytes())
        );

        publisher.publish(new DocumentStorageRequestPublisher.DocumentStorageRequested(
            1,
            documentId,
            "documents/" + documentId,
            staged.contentReference(),
            staged.sizeBytes(),
            staged.contentType()
        ));

        metrics.ifPresent(value -> value.upload("accepted"));

        return saved;
    }

    @Transactional(readOnly = true)
    public Document findById(UUID id) {
        return findScoped(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Document> findAll() {
        if (tenantIdentityService != null && tenantIdentityService.isEnforced()) {
            return documentRepository.findAllByTenantIdOrderByCreatedAtDesc(currentIdentity().tenantId());
        }
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

        TenantIdentity identity = currentIdentity();
        if (tenantIdentityService != null && tenantIdentityService.isEnforced()) {
            tenantIdentityService.requireOperator();
        }
        java.util.Optional<Document> candidate = tenantIdentityService != null && tenantIdentityService.isEnforced()
            ? documentRepository.findRetryCandidateForUpdateInTenant(
                id, identity.tenantId(), DocumentStatus.FAILED, Document.MAX_PROCESSING_FAILURES
            )
            : documentRepository.findRetryCandidateForUpdate(id);
        Document document = candidate
            .orElseGet(() -> findScoped(id)
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
        record(
            ApplicationLogLevel.INFO,
            "document.retry",
            "Document retry requested",
            id,
            Map.of("failureCount", document.getFailureCount())
        );
        publishProcessingStarted(document);

        String objectKey = document.getObjectKey() == null || document.getObjectKey().isBlank()
            ? "documents/" + id
            : document.getObjectKey();
        try {
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
            metrics.ifPresent(value -> value.retry("accepted"));
        } catch (RuntimeException exception) {
            metrics.ifPresent(value -> value.retry("failed"));
            throw exception;
        }

        return document;
    }

    private void publishProcessingStarted(Document document) {
        lifecycleEventPublisher.ifPresent(publisher -> {
            try {
                publisher.publish(lifecycleEventMapper.processingStarted(document, java.time.Instant.now()));
            } catch (RuntimeException exception) {
                log.atWarn().addKeyValue("documentId", document.getId()).setCause(exception)
                    .log("document processing started notification could not be published");
            }
        });
    }

    @Transactional(readOnly = true)
    public DocumentDownload download(UUID id) {
        Document document = findScoped(id)
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
            metrics.ifPresent(value -> value.storage("final_read", "success"));
            return new DocumentDownload(
                stored.content(),
                document.getSizeBytes(),
                document.getContentType(),
                document.getOriginalFilename()
            );
        } catch (DocumentStorageException exception) {
            metrics.ifPresent(value -> value.storage("final_read", exception.getFailureType().name().toLowerCase()));
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
        if (tenantIdentityService != null && tenantIdentityService.isEnforced()) {
            tenantIdentityService.requireOperator();
        }
        java.util.Optional<Document> scopedDocument = tenantIdentityService != null && tenantIdentityService.isEnforced()
            ? documentRepository.findByIdForUpdateInTenant(id, currentIdentity().tenantId())
            : documentRepository.findByIdForUpdate(id);
        Document document = scopedDocument
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
            metrics.ifPresent(value -> value.storage("final_delete", exception.getFailureType().name().toLowerCase()));
            record(
                ApplicationLogLevel.ERROR,
                "document.delete",
                "Document deletion failed in storage",
                id,
                Map.of("failureType", exception.getFailureType().name())
            );
            if (exception.getFailureType() == DocumentStorageException.FailureType.UNAVAILABLE
                || exception.getFailureType() == DocumentStorageException.FailureType.RESULT_UNKNOWN) {
                throw new DocumentDeletionStorageUnavailableException(id, exception);
            }
            throw new DocumentDeletionException(id, exception);
        }

        documentRepository.delete(document);
        documentRepository.flush();
        record(ApplicationLogLevel.INFO, "document.deleted", "Document deleted", id, Map.of());
        publishDeleted(document.getId());
    }

    private void publishDeleted(UUID documentId) {
        lifecycleEventPublisher.ifPresent(publisher -> {
            try {
                publisher.publish(lifecycleEventMapper.deleted(documentId, java.time.Instant.now()));
            } catch (RuntimeException exception) {
                log.atWarn().addKeyValue("documentId", documentId).setCause(exception)
                    .log("document deleted notification could not be published");
            }
        });
    }

    private void record(
        ApplicationLogLevel level,
        String operation,
        String message,
        UUID documentId,
        Map<String, ?> details
    ) {
        operationalLogRecorder.ifPresent(recorder -> recorder.record(operationalLogEventFactory.create(
            level,
            "document-api",
            "document-service",
            operation,
            message,
            new ApplicationLogContext(CorrelationIdContext.currentUuid(), documentId),
            details
        )));
    }

}
