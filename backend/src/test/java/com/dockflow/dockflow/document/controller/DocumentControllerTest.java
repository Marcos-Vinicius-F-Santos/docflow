package com.dockflow.dockflow.document.controller;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentService;
import com.dockflow.dockflow.document.application.DocumentContent;
import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentErrorCode;
import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentValidationException;
import com.dockflow.dockflow.document.exception.DocumentNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
