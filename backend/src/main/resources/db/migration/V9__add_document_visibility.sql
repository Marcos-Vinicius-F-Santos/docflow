-- Public documents are readable by anonymous visitors; existing documents stay private.
ALTER TABLE documents ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE documents ADD CONSTRAINT chk_documents_visibility
    CHECK (visibility IN ('PUBLIC', 'PRIVATE'));
CREATE INDEX idx_documents_visibility_created
    ON documents (visibility, created_at DESC, id DESC);
