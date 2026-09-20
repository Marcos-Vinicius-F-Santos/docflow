-- Expand-only migration for the persisted RESULT_UNKNOWN reconciliation schedule.
-- V1 through V4 remain unchanged. Rollback is operational: deploy code compatible
-- with the additional nullable column; do not remove it directly from the database.

ALTER TABLE documents
    ADD COLUMN reconciliation_next_attempt_at TIMESTAMPTZ;

-- A PROCESSING row with five or more recorded reconciliation attempts is already
-- inconsistent with the approved five-attempt policy. Stop explicitly so an operator
-- can inspect it instead of silently marking it completed or failed during migration.
DO $$
DECLARE
    exhausted_processing_count BIGINT;
BEGIN
    SELECT COUNT(*)
      INTO exhausted_processing_count
      FROM documents
     WHERE status = 'PROCESSING'
       AND reconciliation_attempts >= 5;

    IF exhausted_processing_count > 0 THEN
        RAISE EXCEPTION
            'V5 aborted: % PROCESSING document(s) already have five or more reconciliation attempts; review before retrying the migration',
            exhausted_processing_count;
    END IF;
END
$$;

-- The final object key is deterministic by document UUID. Legacy PROCESSING rows may
-- have reached the storage attempt before object_key was persisted by the application.
UPDATE documents
   SET object_key = 'documents/' || id::text
 WHERE status = 'PROCESSING'
   AND object_key IS NULL;

-- Legacy PROCESSING rows without a schedule are eligible for one recovery pass after
-- the application starts. New processing attempts will schedule this column explicitly.
UPDATE documents
   SET reconciliation_next_attempt_at = CURRENT_TIMESTAMP
 WHERE status = 'PROCESSING'
   AND reconciliation_next_attempt_at IS NULL
   AND reconciliation_attempts < 5;

ALTER TABLE documents
    ADD CONSTRAINT chk_reconciliation_next_attempt_requires_object_key
        CHECK (
            reconciliation_next_attempt_at IS NULL
            OR object_key IS NOT NULL
        );

CREATE INDEX idx_documents_reconciliation_due
    ON documents (reconciliation_next_attempt_at, id)
    WHERE status = 'PROCESSING'
      AND reconciliation_next_attempt_at IS NOT NULL;
