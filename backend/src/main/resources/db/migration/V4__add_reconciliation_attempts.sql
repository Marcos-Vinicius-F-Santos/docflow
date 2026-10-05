-- Expand-only migration. Existing documents start with zero reconciliation attempts.
-- The counter is persisted for the RESULT_UNKNOWN reconciliation policy; a full
-- attempt history is outside the scope of this migration.
ALTER TABLE documents
    ADD COLUMN reconciliation_attempts BIGINT NOT NULL DEFAULT 0;
