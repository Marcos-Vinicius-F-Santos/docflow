package com.dockflow.dockflow.document.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import jakarta.validation.Valid;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentService;
import com.dockflow.dockflow.document.dto.DocumentRegistrationRequest;
import com.dockflow.dockflow.document.dto.DocumentResponse;
import com.dockflow.dockflow.document.mapper.DocumentMapper;


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
        @Valid @RequestBody DocumentRegistrationRequest request
    ) {
        Document document = documentService.registerDocument(
            request.originalFilename(),
            request.contentType(),
            request.sizeBytes()
        );

        DocumentResponse response = DocumentMapper.toResponse(document);

        URI location = URI.create("/documents/" + document.getId());

        return ResponseEntity
            .created(location)
            .body(response);
    }

    @GetMapping("/{documentId}")
    public DocumentResponse findDocumentById(@PathVariable UUID documentId) {
        Document document = documentService.findById(documentId);
        return DocumentMapper.toResponse(document);
    }


}

