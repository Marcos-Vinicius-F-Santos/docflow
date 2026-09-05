package com.dockflow.dockflow.document;
import com.dockflow.dockflow.document.exception.DocumentNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DocumentService {
    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Transactional
    public Document registerDocument(
        String originalFilename,
        String contentType,
        long sizeBytes
    ) {

        Document document = new Document(originalFilename, contentType, sizeBytes);
        return documentRepository.save(document);

    }

    @Transactional(readOnly = true)
    public Document findById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

}
