package com.dockflow.dockflow.document;
import com.dockflow.dockflow.IntegrationTestBase;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;

@SpringBootTest
class DocumentServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        documentRepository.deleteAll();
    }

    @Test
    void shouldPersistDocumentInDatabase() {

        String originalFilename = "integration_test_document.pdf";
        String contentType = "application/pdf";
        long sizeBytes = 2048;

        Document registeredDocument = documentService.registerDocument(originalFilename, contentType, sizeBytes);

        Document persistedDocument = documentRepository.findById(registeredDocument.getId()).orElseThrow();

        assertEquals(originalFilename, persistedDocument.getOriginalFilename());
        assertEquals(contentType, persistedDocument.getContentType());
        assertEquals(sizeBytes, persistedDocument.getSizeBytes());
        assertEquals(DocumentStatus.PENDING, persistedDocument.getStatus());
        assertNull(persistedDocument.getObjectKey());
        assertEquals(0L, persistedDocument.getVersion());
        assertNotNull(persistedDocument.getId());
        assertNotNull(persistedDocument.getCreatedAt());
        assertNotNull(persistedDocument.getUpdatedAt());

    }

    @Test
    void shouldRejectCompletedDocumentWithoutObjectReference() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
            "INSERT INTO documents (id, status, original_filename, content_type, size_bytes, created_at, updated_at) "
                + "VALUES (gen_random_uuid(), 'COMPLETED', 'invalid.pdf', 'application/pdf', 1, now(), now())"));
    }

    @Test
    void shouldRejectDuplicateObjectReference() {
        Document first = documentService.registerDocument("first.pdf", "application/pdf", 1);
        first.startProcessing();
        first.markCompleted("documents/shared/content");
        documentRepository.saveAndFlush(first);

        Document second = documentService.registerDocument("second.pdf", "application/pdf", 1);
        second.startProcessing();
        second.markCompleted("documents/shared/content");

        assertThrows(DataIntegrityViolationException.class, () -> documentRepository.saveAndFlush(second));
    }

    @Test
    void shouldRejectStaleConcurrentStateTransition() {
        Document created = documentService.registerDocument("concurrent.pdf", "application/pdf", 1);

        Document first = documentRepository.findById(created.getId()).orElseThrow();
        Document second = documentRepository.findById(created.getId()).orElseThrow();

        first.startProcessing();
        documentRepository.saveAndFlush(first);

        second.startProcessing();
        assertThrows(ObjectOptimisticLockingFailureException.class,
            () -> documentRepository.saveAndFlush(second));
    }

}
