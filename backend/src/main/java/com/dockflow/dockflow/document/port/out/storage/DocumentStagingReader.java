package com.dockflow.dockflow.document.port.out.storage;

import java.io.InputStream;

/**
 * Provider-neutral read boundary for durable document staging.
 *
 * <p>The caller owns and must close the returned stream. The reference is a
 * logical value such as {@code staging/{documentId}}; provider-specific bucket
 * and SDK details stay in the adapter.</p>
 */
public interface DocumentStagingReader {

    StagedContent open(String contentReference);

    record StagedContent(
        InputStream content,
        long sizeBytes,
        String contentType
    ) implements AutoCloseable {

        @Override
        public void close() throws java.io.IOException {
            content.close();
        }
    }
}
