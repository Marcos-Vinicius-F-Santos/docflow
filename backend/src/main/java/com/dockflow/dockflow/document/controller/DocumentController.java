package com.dockflow.dockflow.document.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentService;
import com.dockflow.dockflow.document.application.DocumentContent;
import com.dockflow.dockflow.document.dto.DocumentMultipartRegistrationRequest;
import com.dockflow.dockflow.document.dto.DocumentResponse;
import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentErrorCode;
import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentValidationException;
import com.dockflow.dockflow.document.mapper.DocumentMapper;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> registerDocument(
        @RequestPart(value = "file", required = false) MultipartFile file,
        @RequestPart(value = "metadata", required = false) String metadata,
        HttpServletRequest request
    ) {
        if (metadata != null) {
            throw new DocumentContentValidationException(
                DocumentContentErrorCode.DOCUMENT_METADATA_NOT_ALLOWED,
                "Only the file part is accepted"
            );
        }
        rejectUnsupportedParts(request);
        if (file == null) {
            throw new DocumentContentValidationException(
                DocumentContentErrorCode.DOCUMENT_CONTENT_MISSING,
                "The file part is required",
                "file"
            );
        }

        DocumentContent content;
        try {
            content = new DocumentMultipartRegistrationRequest(file).toDocumentContent();
        } catch (IOException exception) {
            throw new DocumentContentValidationException(
                DocumentContentErrorCode.DOCUMENT_CONTENT_READ_FAILED,
                "The file content could not be opened",
                "file"
            );
        }

        Document document = documentService.registerDocument(content);

        DocumentResponse response = DocumentMapper.toResponse(document);

        URI location = URI.create("/documents/" + document.getId());

        return ResponseEntity
            .created(location)
            .body(response);
    }

    private void rejectUnsupportedParts(HttpServletRequest request) {
        if (request.getContentType() == null ||
            !request.getContentType().toLowerCase().startsWith("multipart/")) {
            return;
        }

        try {
            int fileParts = 0;
            for (Part part : request.getParts()) {
                if ("file".equals(part.getName())) {
                    fileParts++;
                } else {
                    throw new DocumentContentValidationException(
                        DocumentContentErrorCode.DOCUMENT_METADATA_NOT_ALLOWED,
                        "Only the file part is accepted"
                    );
                }
            }
            if (fileParts > 1) {
                throw new DocumentContentValidationException(
                    DocumentContentErrorCode.DOCUMENT_METADATA_NOT_ALLOWED,
                    "Only one file part is accepted"
                );
            }
        } catch (IOException | ServletException exception) {
            throw new DocumentContentValidationException(
                DocumentContentErrorCode.DOCUMENT_CONTENT_READ_FAILED,
                "The multipart request could not be inspected",
                exception
            );
        }
    }

    @GetMapping("/{documentId}")
    public DocumentResponse findDocumentById(@PathVariable UUID documentId) {
        Document document = documentService.findById(documentId);
        return DocumentMapper.toResponse(document);
    }


}

