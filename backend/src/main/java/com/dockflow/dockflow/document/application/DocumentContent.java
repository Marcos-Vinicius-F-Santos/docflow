package com.dockflow.dockflow.document.application;

import java.io.InputStream;

/**
 * Framework-independent document content received for registration.
 *
 * <p>The stream is one-shot and is owned by the component that consumes it
 * last. The staging writer owns and closes the stream supplied for durable
 * staging. It deliberately does not expose Spring MVC types or a byte array.</p>
 */
public record DocumentContent(
    String originalFilename,
    String contentType,
    InputStream content
) {
}
