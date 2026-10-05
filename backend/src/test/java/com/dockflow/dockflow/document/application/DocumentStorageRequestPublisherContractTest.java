package com.dockflow.dockflow.document.application;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentStorageRequestPublisherContractTest {

    @Test
    void shouldExposeOnlyDurableMetadataAndTheStagingReference() {
        var request = new DocumentStorageRequestPublisher.DocumentStorageRequested(
            1,
            UUID.randomUUID(),
            "documents/document-id",
            "staging/document-id",
            128,
            "application/pdf"
        );

        assertEquals(1, request.schemaVersion());
        assertTrue(request.contentReference().startsWith("staging/"));
        assertFalse(Arrays.stream(
            DocumentStorageRequestPublisher.DocumentStorageRequested.class.getRecordComponents()
        ).anyMatch(component -> component.getType().equals(InputStream.class)
            || component.getType().equals(byte[].class)));
    }
}
