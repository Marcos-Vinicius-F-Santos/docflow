package com.dockflow.dockflow.document.port.out.storage;

/**
 * Provider-neutral cleanup boundary for staged document content.
 *
 * <p>The application may call this port only after the final storage operation
 * has been confirmed, according to FR-014.</p>
 */
public interface DocumentStagingRemover {

    void delete(String contentReference);
}
