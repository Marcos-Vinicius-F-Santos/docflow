package com.dockflow.dockflow.document;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentTest {

    @Test
    void startsWithNoFailuresAndIsNotExcluded() {
        Document document = new Document("document.txt", "text/plain", 10);

        assertEquals(0, document.getFailureCount());
        assertFalse(document.isExcluded());
        assertNull(document.getExclusionScheduledAt());
    }

    @Test
    void countsTheInitialFailureAndSchedulesTheThirdFailureForDeletion() {
        Instant firstFailure = Instant.parse("2026-10-27T12:00:00Z");
        Document document = new Document("document.txt", "text/plain", 10);

        document.markFailed(firstFailure);
        assertEquals(1, document.getFailureCount());
        assertFalse(document.isExcluded());
        assertNull(document.getExclusionScheduledAt());

        document.startRetryProcessing();
        document.markFailed(firstFailure.plusSeconds(30));
        assertEquals(2, document.getFailureCount());
        assertFalse(document.isExcluded());

        document.startRetryProcessing();
        Instant thirdFailure = firstFailure.plusSeconds(60);
        document.markFailed(thirdFailure);

        assertEquals(DocumentStatus.FAILED, document.getStatus());
        assertEquals(3, document.getFailureCount());
        assertTrue(document.isExcluded());
        assertEquals(thirdFailure.plus(Document.EXCLUSION_DELAY), document.getExclusionScheduledAt());
    }

    @Test
    void successfulRetryResetsFailureCycleAndExclusionMetadata() {
        Document document = new Document("document.txt", "text/plain", 10);
        document.markFailed(Instant.parse("2026-10-27T12:00:00Z"));
        document.startRetryProcessing();

        document.markCompleted("documents/document");

        assertEquals(DocumentStatus.COMPLETED, document.getStatus());
        assertEquals(0, document.getFailureCount());
        assertFalse(document.isExcluded());
        assertNull(document.getExclusionScheduledAt());
    }

    @Test
    void retryIsOnlyAllowedForFailedDocumentsBelowTheLimit() {
        Document pending = new Document("pending.txt", "text/plain", 1);
        assertThrows(IllegalStateException.class, pending::startRetryProcessing);

        Document exhausted = new Document("exhausted.txt", "text/plain", 1);
        exhausted.markFailed(Instant.parse("2026-10-27T12:00:00Z"));
        exhausted.startRetryProcessing();
        exhausted.markFailed(Instant.parse("2026-10-27T12:01:00Z"));
        exhausted.startRetryProcessing();
        exhausted.markFailed(Instant.parse("2026-10-27T12:02:00Z"));

        assertThrows(IllegalStateException.class, exhausted::startRetryProcessing);
    }
}
