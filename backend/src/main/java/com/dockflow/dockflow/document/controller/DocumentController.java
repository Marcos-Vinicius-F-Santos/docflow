package com.dockflow.dockflow.document.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentService;
import com.dockflow.dockflow.document.DocumentVisibility;
import com.dockflow.dockflow.document.application.DocumentContent;
import com.dockflow.dockflow.document.application.DocumentDownload;
import com.dockflow.dockflow.document.dto.DocumentMultipartRegistrationRequest;
import com.dockflow.dockflow.document.dto.DocumentResponse;
import com.dockflow.dockflow.document.dto.DocumentVisibilityUpdateRequest;
import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentErrorCode;
import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentValidationException;
import com.dockflow.dockflow.document.mapper.DocumentMapper;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.net.URI;
import java.util.List;
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
        @RequestPart(value = "visibility", required = false) String visibility,
        HttpServletRequest request
    ) {
        if (metadata != null) {
            throw new DocumentContentValidationException(
                DocumentContentErrorCode.DOCUMENT_METADATA_NOT_ALLOWED,
                "Only the file and visibility parts are accepted"
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

        Document document;
        if (visibility == null || visibility.isBlank()) {
            document = documentService.registerDocument(content);
        } else {
            DocumentVisibility documentVisibility;
            try {
                documentVisibility = DocumentVisibility.from(visibility);
            } catch (IllegalArgumentException exception) {
                throw new DocumentContentValidationException(
                    DocumentContentErrorCode.DOCUMENT_METADATA_NOT_ALLOWED,
                    "Visibility must be PUBLIC or PRIVATE",
                    "visibility"
                );
            }
            document = documentService.registerDocument(content, documentVisibility);
        }

        DocumentResponse response = toResponse(document);

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
            int visibilityParts = 0;
            for (Part part : request.getParts()) {
                if ("file".equals(part.getName())) {
                    fileParts++;
                } else if ("visibility".equals(part.getName())) {
                    visibilityParts++;
                } else {
                    throw new DocumentContentValidationException(
                        DocumentContentErrorCode.DOCUMENT_METADATA_NOT_ALLOWED,
                        "Only the file and visibility parts are accepted"
                    );
                }
            }
            if (fileParts > 1 || visibilityParts > 1) {
                throw new DocumentContentValidationException(
                    DocumentContentErrorCode.DOCUMENT_METADATA_NOT_ALLOWED,
                    "Only one file and one visibility part are accepted"
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
        return toResponse(document);
    }

    @PostMapping("/{documentId}/retry")
    public ResponseEntity<DocumentResponse> retryDocument(@PathVariable UUID documentId) {
        Document document = documentService.retry(documentId);
        return ResponseEntity.accepted().body(toResponse(document));
    }

    @GetMapping
    public List<DocumentResponse> findDocuments() {
        return documentService.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    @PatchMapping("/{documentId}/visibility")
    public DocumentResponse changeVisibility(
        @PathVariable UUID documentId,
        @RequestBody DocumentVisibilityUpdateRequest request
    ) {
        if (request == null || request.visibility() == null) {
            throw new DocumentContentValidationException(
                DocumentContentErrorCode.DOCUMENT_METADATA_NOT_ALLOWED,
                "Visibility must be PUBLIC or PRIVATE",
                "visibility"
            );
        }
        Document document = documentService.changeVisibility(documentId, request.visibility());
        return toResponse(document);
    }

    @GetMapping("/{documentId}/content")
    public ResponseEntity<StreamingResponseBody> downloadDocument(@PathVariable UUID documentId) {
        return streamDocument(documentService.download(documentId), ContentDisposition.attachment());
    }

    @GetMapping("/{documentId}/preview")
    public ResponseEntity<StreamingResponseBody> previewDocument(@PathVariable UUID documentId) {
        return streamDocument(documentService.download(documentId), ContentDisposition.inline());
    }

    private ResponseEntity<StreamingResponseBody> streamDocument(
        DocumentDownload download,
        ContentDisposition.Builder disposition
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        headers.setContentLength(download.sizeBytes());
        headers.setContentDisposition(disposition
            .filename(download.originalFilename())
            .build());
        headers.setCacheControl("no-store");

        StreamingResponseBody body = output -> {
            try (var content = download.content()) {
                content.transferTo(output);
            }
        };
        return ResponseEntity.ok().headers(headers).body(body);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID documentId) {
        documentService.delete(documentId);
        return ResponseEntity.noContent().build();
    }

    private DocumentResponse toResponse(Document document) {
        return DocumentMapper.toResponse(document, documentService.canChangeVisibility(document));
    }


}

