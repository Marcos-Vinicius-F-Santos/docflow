package com.dockflow.dockflow.document.application;

import java.io.InputStream;

/** Content stream and persisted metadata exposed by the application boundary. */
public record DocumentDownload(
    InputStream content,
    long sizeBytes,
    String contentType,
    String originalFilename
) {
}
