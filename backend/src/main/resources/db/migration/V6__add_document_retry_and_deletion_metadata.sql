-- Expand-only migration for the Hub retry and delayed-deletion metadata.
-- V1 through V5 remain unchanged. Rollback is operational: deploy code that
-- ignores these additional columns; never remove them directly from the database.

ALTER TABLE documents
    ADD COLUMN failure_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN is_excluded BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN exclusion_scheduled_at TIMESTAMPTZ;

ALTER TABLE documents
    ADD CONSTRAINT chk_failure_count_non_negative
        CHECK (failure_count >= 0),
    ADD CONSTRAINT chk_excluded_requires_three_failures
        CHECK (is_excluded = FALSE OR failure_count >= 3),
    ADD CONSTRAINT chk_exclusion_schedule_requires_excluded
        CHECK (exclusion_scheduled_at IS NULL OR is_excluded = TRUE);

CREATE INDEX idx_documents_exclusion_scheduled_at
    ON documents (exclusion_scheduled_at, id)
    WHERE is_excluded = TRUE
      AND exclusion_scheduled_at IS NOT NULL;
