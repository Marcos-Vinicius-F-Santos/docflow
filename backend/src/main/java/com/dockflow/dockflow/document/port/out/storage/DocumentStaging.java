package com.dockflow.dockflow.document.port.out.storage;

import java.io.InputStream;
import java.util.UUID;

/**
 * Provider-neutral boundary for making received document content durable before
 * asynchronous processing.
 *
 * <p>The implementation owns and closes the supplied stream after staging. The
 * resulting reference is safe to transport after the HTTP request has ended.
 * Reading and conditional removal are exposed by the companion
 * {@link DocumentStagingReader} and {@link DocumentStagingRemover} ports so a
 * consumer can depend only on the capability it needs.</p>
 */
public interface DocumentStaging {

    StagedObject stage(UUID documentId, InputStream content, String contentType);

    record StagedObject(String contentReference, long sizeBytes, String contentType) {
    }
}
