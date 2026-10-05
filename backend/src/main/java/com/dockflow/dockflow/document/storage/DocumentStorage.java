package com.dockflow.dockflow.document.storage;

import java.io.InputStream;

public interface DocumentStorage {

    StoredObject store(
        String objectKey,
        InputStream content,
        long size,
        String contentType
    );

    StoredContent open(String objectKey);

    boolean exists(String objectKey);

    void delete(String objectKey);

    record StoredObject(String objectKey, long sizeBytes, String contentType) {
    }

    record StoredContent(InputStream content, long sizeBytes, String contentType) {
    }
}
