package com.dockflow.dockflow.document.dto;

import com.dockflow.dockflow.document.application.DocumentContent;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * HTTP-bound request DTO for the multipart document-registration endpoint.
 * Spring's multipart type stops at this boundary; the application receives
 * {@link DocumentContent} instead.
 */
public record DocumentMultipartRegistrationRequest(MultipartFile file) {

    public DocumentContent toDocumentContent() throws IOException {
        return new DocumentContent(
            file == null ? null : file.getOriginalFilename(),
            file == null ? null : file.getContentType(),
            file == null ? null : file.getInputStream()
        );
    }
}
