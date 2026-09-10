-- Expand-only migration. Existing documents remain readable with a null object_key.
-- There is intentionally no destructive down migration: rollback is performed by
-- deploying code compatible with these nullable additions. Forward recovery must
-- reconcile PROCESSING records and any confirmed but unassociated object.
ALTER TABLE documents
    ADD COLUMN object_key TEXT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE documents
    ADD CONSTRAINT chk_object_key_not_blank
        CHECK (object_key IS NULL OR btrim(object_key) <> ''),
    ADD CONSTRAINT chk_completed_requires_object_key
        CHECK (status <> 'COMPLETED' OR object_key IS NOT NULL);

CREATE UNIQUE INDEX uq_documents_object_key
    ON documents (object_key)
    WHERE object_key IS NOT NULL;
