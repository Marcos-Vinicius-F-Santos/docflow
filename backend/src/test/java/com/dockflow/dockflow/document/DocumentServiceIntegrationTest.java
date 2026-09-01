package com.dockflow.dockflow.document;
import com.dockflow.dockflow.IntegrationTestBase;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;

@SpringBootTest
class DocumentServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @BeforeEach
    void cleanDatabase() {
        documentRepository.deleteAll();
    }

    @Test
    void shouldPersistDocumentInDatabase() {

        String originalFilename = "integration_test_document.txt";
        String contentType = "application/pdf";
        long sizeBytes = 2048;

        Document registeredDocument = documentService.registerDocument(originalFilename, contentType, sizeBytes);

        Document persistedDocument = documentRepository.findById(registeredDocument.getId()).orElseThrow();

        assertEquals(originalFilename, persistedDocument.getOriginalFilename());
        assertEquals(contentType, persistedDocument.getContentType());
        assertEquals(sizeBytes, persistedDocument.getSizeBytes());
        assertEquals(DocumentStatus.PENDING, persistedDocument.getStatus());
        assertNotNull(persistedDocument.getId());
        assertNotNull(persistedDocument.getCreatedAt());
        assertNotNull(persistedDocument.getUpdatedAt());

    }

}