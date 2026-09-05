package com.dockflow.dockflow.document.controller;
import com.dockflow.dockflow.document.exception.DocumentNotFoundException;
import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.UUID;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @Test
    void shouldRegisterDocument() throws Exception {

        UUID id = UUID.randomUUID();
        
        Document mockDocument = new Document("test.txt", "text/plain", 1234L);
        when(documentService.registerDocument("test.txt", "text/plain", 1234L)).thenReturn(mockDocument);

        ReflectionTestUtils.setField(mockDocument, "id", id);

        mockMvc.perform(post("/documents")
                .contentType("application/json")
                .content("""
                {
                    "originalFilename": "test.txt",
                    "contentType": "text/plain",
                    "sizeBytes": 1234
                }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("test.txt"))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.sizeBytes").value(1234))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(header().string(
                    "Location",
                    "/documents/" + id
                ))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        """
        {
            "originalFilename": "",
            "contentType": "application/pdf",
            "sizeBytes": 1024
        }
        """,
        """
        {
            "originalFilename": "test.pdf",
            "contentType": "",
            "sizeBytes": 1024
        }
        """,
        """
        {
            "originalFilename": "test.pdf",
            "contentType": "application/pdf",
            "sizeBytes": -1
        }
        """
    })
    void shouldRejectInvalidDocumentRequest(String requestBody) throws Exception {
        mockMvc.perform(post("/documents")
                .contentType("application/json")
                .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(documentService, never()).registerDocument(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyLong()
        );
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

        when(documentService.findById(id))
            .thenThrow(new DocumentNotFoundException(id));

        mockMvc.perform(get("/documents/{documentId}", id))
            .andExpect(status().isNotFound());
    }


}