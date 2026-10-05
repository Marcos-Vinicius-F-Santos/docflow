package com.dockflow.dockflow.document.controller;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentService;
import com.dockflow.dockflow.document.application.DocumentContent;
import com.dockflow.dockflow.document.application.DocumentDownload;
import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentErrorCode;
import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentValidationException;
import com.dockflow.dockflow.document.exception.DocumentNotFoundException;
import com.dockflow.dockflow.document.exception.DocumentContentNotAvailableException;
import com.dockflow.dockflow.document.exception.DocumentContentNotFoundException;
import com.dockflow.dockflow.document.exception.DocumentContentStorageUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @Test
    void shouldRegisterMultipartDocumentAndTranslateTheFilePart() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] documentBytes = "document content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            documentBytes
        );
        Document mockDocument = new Document("test.txt", "text/plain", documentBytes.length);
        ReflectionTestUtils.setField(mockDocument, "id", id);

        when(documentService.registerDocument(any(DocumentContent.class))).thenReturn(mockDocument);

        mockMvc.perform(multipart("/documents").file(file))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.originalFilename").value("test.txt"))
            .andExpect(jsonPath("$.contentType").value("text/plain"))
            .andExpect(jsonPath("$.sizeBytes").value(documentBytes.length))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(header().string("Location", "/documents/" + id))
            .andExpect(jsonPath("$.id").value(id.toString()));

        var contentCaptor = org.mockito.ArgumentCaptor.forClass(DocumentContent.class);
        verify(documentService).registerDocument(contentCaptor.capture());
        assertEquals("test.txt", contentCaptor.getValue().originalFilename());
        assertEquals("text/plain", contentCaptor.getValue().contentType());
        assertArrayEquals(documentBytes, contentCaptor.getValue().content().readAllBytes());
    }

    @Test
    void shouldRejectRequestWithoutTheFilePartAsProblemDetail() throws Exception {
        mockMvc.perform(post("/documents"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errorCode").value("DOCUMENT_CONTENT_MISSING"))
            .andExpect(jsonPath("$.invalidField").value("file"));

        verify(documentService, never()).registerDocument(any(DocumentContent.class));
    }

    @Test
    void shouldRejectThePreviousJsonContract() throws Exception {
        mockMvc.perform(post("/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "originalFilename": "test.txt",
                        "contentType": "text/plain",
                        "sizeBytes": 1234
                    }
                    """))
            .andExpect(status().isBadRequest());

        verify(documentService, never()).registerDocument(any(DocumentContent.class));
    }

    @Test
    void shouldRejectAnAdditionalMetadataPart() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            "document content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/documents")
                .file(file)
                .file(new MockMultipartFile(
                    "metadata",
                    "metadata.json",
                    MediaType.APPLICATION_JSON_VALUE,
                    "{\"contentType\":\"text/plain\"}".getBytes(StandardCharsets.UTF_8)
                )))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errorCode").value("DOCUMENT_METADATA_NOT_ALLOWED"))
            .andExpect(jsonPath("$.invalidField").value("metadata"));

        verify(documentService, never()).registerDocument(any(DocumentContent.class));
    }

    @Test
    void shouldMapMissingFilenameToProblemDetail() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "",
            "text/plain",
            "document content".getBytes(StandardCharsets.UTF_8)
        );
        when(documentService.registerDocument(any(DocumentContent.class)))
            .thenThrow(new DocumentContentValidationException(
                DocumentContentErrorCode.DOCUMENT_FILENAME_MISSING,
                "The file filename is required"
            ));

        mockMvc.perform(multipart("/documents").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errorCode").value("DOCUMENT_FILENAME_MISSING"))
            .andExpect(jsonPath("$.invalidField").value("file"));
    }

    @Test
    void shouldMapMissingContentTypeToProblemDetail() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            null,
            "document content".getBytes(StandardCharsets.UTF_8)
        );
        when(documentService.registerDocument(any(DocumentContent.class)))
            .thenThrow(new DocumentContentValidationException(
                DocumentContentErrorCode.DOCUMENT_CONTENT_TYPE_MISSING,
                "The file Content-Type is required"
            ));

        mockMvc.perform(multipart("/documents").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("DOCUMENT_CONTENT_TYPE_MISSING"))
            .andExpect(jsonPath("$.invalidField").value("file"));
    }

    @Test
    void shouldMapEmptyContentToProblemDetail() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        when(documentService.registerDocument(any(DocumentContent.class)))
            .thenThrow(new DocumentContentValidationException(
                DocumentContentErrorCode.DOCUMENT_CONTENT_EMPTY,
                "The file content cannot be empty"
            ));

        mockMvc.perform(multipart("/documents").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("DOCUMENT_CONTENT_EMPTY"))
            .andExpect(jsonPath("$.invalidField").value("file"));
    }

    @Test
    void shouldMapUnknownSignatureToProblemDetail() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "document.bin",
            "application/octet-stream",
            new byte[] {0, 1, 2, 3}
        );
        when(documentService.registerDocument(any(DocumentContent.class)))
            .thenThrow(new DocumentContentValidationException(
                DocumentContentErrorCode.DOCUMENT_CONTENT_TYPE_UNKNOWN,
                "The file signature could not be identified"
            ));

        mockMvc.perform(multipart("/documents").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("DOCUMENT_CONTENT_TYPE_UNKNOWN"))
            .andExpect(jsonPath("$.invalidField").value("file"));
    }

    @Test
    void shouldMapOversizedContentToProblemDetail() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "large.pdf",
            "application/pdf",
            new byte[] {1}
        );
        when(documentService.registerDocument(any(DocumentContent.class)))
            .thenThrow(new DocumentContentValidationException(
                DocumentContentErrorCode.DOCUMENT_CONTENT_TOO_LARGE,
                "The file exceeds the maximum size"
            ));

        mockMvc.perform(multipart("/documents").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("DOCUMENT_CONTENT_TOO_LARGE"))
            .andExpect(jsonPath("$.invalidField").value("file"));
    }

    @Test
    void shouldMapContentValidationFailureToProblemDetail() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.pdf",
            "text/plain",
            "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)
        );
        when(documentService.registerDocument(any(DocumentContent.class)))
            .thenThrow(new DocumentContentValidationException(
                DocumentContentErrorCode.DOCUMENT_CONTENT_TYPE_MISMATCH,
                "The declared Content-Type does not match the file signature",
                "file"
            ));

        mockMvc.perform(multipart("/documents").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errorCode").value("DOCUMENT_CONTENT_TYPE_MISMATCH"))
            .andExpect(jsonPath("$.invalidField").value("file"));
    }

    @Test
    void shouldFindDocumentById() throws Exception {
        UUID id = UUID.randomUUID();
        Document mockDocument = new Document("test_document.pdf", "application/pdf", 1024);

        when(documentService.findById(id)).thenReturn(mockDocument);

        mockMvc.perform(get("/documents/{documentId}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.originalFilename").value("test_document.pdf"))
            .andExpect(jsonPath("$.contentType").value("application/pdf"))
            .andExpect(jsonPath("$.sizeBytes").value(1024))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldReturnNotFoundWhenDocumentDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();

        when(documentService.findById(id)).thenThrow(new DocumentNotFoundException(id));

        mockMvc.perform(get("/documents/{documentId}", id))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldListDocumentsWithAllStatusesAndSevenFields() throws Exception {
        Document pending = new Document("pending.txt", "text/plain", 1);
        Document processing = new Document("processing.txt", "text/plain", 2);
        Document completed = new Document("completed.txt", "text/plain", 3);
        Document failed = new Document("failed.txt", "text/plain", 4);
        processing.startProcessing();
        completed.startProcessing();
        completed.markCompleted("documents/" + completed.getId());
        failed.markFailed();
        when(documentService.findAll()).thenReturn(List.of(pending, processing, completed, failed));

        mockMvc.perform(get("/documents"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(4)))
            .andExpect(jsonPath("$[0].id").value(pending.getId().toString()))
            .andExpect(jsonPath("$[0].originalFilename").value("pending.txt"))
            .andExpect(jsonPath("$[0].contentType").value("text/plain"))
            .andExpect(jsonPath("$[0].sizeBytes").value(1))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
            .andExpect(jsonPath("$[0].createdAt").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$[0].updatedAt").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$[1].status").value("PROCESSING"))
            .andExpect(jsonPath("$[2].status").value("COMPLETED"))
            .andExpect(jsonPath("$[3].status").value("FAILED"));
    }

    @Test
    void shouldReturnEmptyDocumentList() throws Exception {
        when(documentService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/documents"))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void shouldStreamCompletedDocumentWithApprovedHeaders() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] bytes = "document content".getBytes(StandardCharsets.UTF_8);
        when(documentService.download(id)).thenReturn(new DocumentDownload(
            new ByteArrayInputStream(bytes),
            bytes.length,
            "text/plain",
            "document.txt"
        ));

        mockMvc.perform(get("/documents/{documentId}/content", id))
            .andExpect(status().isOk())
            .andExpect(content().bytes(bytes))
            .andExpect(header().string("Content-Type", "text/plain"))
            .andExpect(header().string("Content-Length", String.valueOf(bytes.length)))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"document.txt\""))
            .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void shouldMapDownloadContentNotFoundToTheDownloadContract() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.download(id)).thenThrow(new DocumentContentNotFoundException(id));

        mockMvc.perform(get("/documents/{documentId}/content", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void shouldMapUnavailableDownloadStorageToTheDownloadContract() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.download(id)).thenThrow(
            new DocumentContentStorageUnavailableException(id, new IllegalStateException("offline"))
        );

        mockMvc.perform(get("/documents/{documentId}/content", id))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("DOCUMENT_STORAGE_UNAVAILABLE"));
    }

    @Test
    void shouldMapUnavailableDocumentStatusToConflict() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.download(id)).thenThrow(
            new DocumentContentNotAvailableException(id, com.dockflow.dockflow.document.DocumentStatus.PENDING)
        );

        mockMvc.perform(get("/documents/{documentId}/content", id))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("DOCUMENT_CONTENT_NOT_AVAILABLE"));
    }

    @Test
    void shouldDeleteDocumentAndReturnNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/documents/{documentId}", id))
            .andExpect(status().isNoContent());

        verify(documentService).delete(id);
    }
}
