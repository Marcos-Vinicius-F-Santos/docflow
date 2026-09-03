package com.dockflow.dockflow.document.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import com.dockflow.dockflow.document.Document;
import com.dockflow.dockflow.document.DocumentService;
import com.dockflow.dockflow.document.dto.DocumentRegistrationRequest;
import com.dockflow.dockflow.document.dto.DocumentResponse;
import com.dockflow.dockflow.document.mapper.DocumentMapper;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse registerDocument(@Valid @RequestBody DocumentRegistrationRequest request) {
        Document document = documentService.registerDocument(
            request.originalFilename(),
            request.contentType(),
            request.sizeBytes()
        );
        return DocumentMapper.toResponse(document);
    }
    
}
