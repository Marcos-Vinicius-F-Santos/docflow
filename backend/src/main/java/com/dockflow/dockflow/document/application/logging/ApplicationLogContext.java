package com.dockflow.dockflow.document.application.logging;

import java.util.UUID;

/** Optional identifiers used to relate operational events without tracing payloads. */
public record ApplicationLogContext(UUID correlationId, UUID documentId) {

    public static ApplicationLogContext empty() {
        return new ApplicationLogContext(null, null);
    }
}
