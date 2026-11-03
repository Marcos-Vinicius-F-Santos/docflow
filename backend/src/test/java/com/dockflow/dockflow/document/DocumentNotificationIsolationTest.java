package com.dockflow.dockflow.document;

import com.dockflow.dockflow.document.application.DocumentContentValidator;
import com.dockflow.dockflow.document.application.DocumentLifecycleEventPublisher;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import com.dockflow.dockflow.document.storage.DocumentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentNotificationIsolationTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentStorage documentStorage;

    @Mock
    private DocumentStagingRemover stagingRemover;

    @Mock
    private DocumentLifecycleEventPublisher lifecycleEventPublisher;

    @Test
    void notificationFailureDoesNotUndoACompletedManualDeletion() {
        Document document = new Document("document.pdf", "application/pdf", 10);
        when(documentRepository.findByIdForUpdate(document.getId())).thenReturn(Optional.of(document));
        doThrow(new RuntimeException("RabbitMQ unavailable"))
            .when(lifecycleEventPublisher).publish(any());

        DocumentService service = new DocumentService(
            documentRepository,
            new DocumentContentValidator(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(documentStorage),
            Optional.of(stagingRemover),
            Optional.of(lifecycleEventPublisher)
        );

        service.delete(document.getId());

        verify(documentRepository).delete(document);
        verify(documentRepository).flush();
    }
}
