package com.dockflow.dockflow.document.dto;

import com.dockflow.dockflow.document.application.DocumentContent;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DocumentMultipartRegistrationRequestTest {

    @Test
    void shouldMapFilePartToInternalDocumentContent() throws Exception {
        byte[] bytes = "content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "document.txt",
            "text/plain",
            bytes
        );

        DocumentContent content = new DocumentMultipartRegistrationRequest(file)
            .toDocumentContent();

        assertEquals("document.txt", content.originalFilename());
        assertEquals("text/plain", content.contentType());
        assertInstanceOf(InputStream.class, content.content());
        assertArrayEquals(bytes, content.content().readAllBytes());
    }
}
