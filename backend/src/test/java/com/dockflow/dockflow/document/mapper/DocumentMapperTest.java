package com.dockflow.dockflow.document.mapper;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentStatus;
import com.dockflow.dockflow.document.dto.DocumentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentMapperTest {

    @Test
    void shouldPreserveAllApprovedListFields() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-19T20:00:00Z");
        Instant updatedAt = Instant.parse("2026-09-20T08:30:00Z");
        Document document = new Document(id, "document.pdf", "application/pdf", 2048);
        document.startProcessing();
        document.markCompleted("documents/" + id);
        ReflectionTestUtils.setField(document, "createdAt", createdAt);
        ReflectionTestUtils.setField(document, "updatedAt", updatedAt);

        DocumentResponse response = DocumentMapper.toResponse(document);

        assertEquals(id, response.id());
        assertEquals("document.pdf", response.originalFilename());
        assertEquals("application/pdf", response.contentType());
        assertEquals(2048, response.sizeBytes());
        assertEquals(DocumentStatus.COMPLETED, response.status());
        assertEquals(createdAt, response.createdAt());
        assertEquals(updatedAt, response.updatedAt());
    }
}
