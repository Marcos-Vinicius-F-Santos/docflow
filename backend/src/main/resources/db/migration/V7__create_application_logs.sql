-- Expand-only migration for the operational log stream. Existing document
-- migrations remain immutable and this table is independent from documents.
CREATE TABLE application_logs (
    event_id UUID PRIMARY KEY,
    schema_version INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    level VARCHAR(16) NOT NULL,
    service VARCHAR(120) NOT NULL,
    component VARCHAR(160) NOT NULL,
    operation VARCHAR(160) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    correlation_id UUID,
    document_id UUID,
    details_json JSONB,
    persisted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_application_logs_schema_version CHECK (schema_version = 1),
    CONSTRAINT chk_application_logs_level CHECK (level IN ('INFO', 'WARN', 'ERROR'))
);

CREATE INDEX idx_application_logs_occurred_at
    ON application_logs (occurred_at DESC, event_id DESC);

CREATE INDEX idx_application_logs_level
    ON application_logs (level, occurred_at DESC, event_id DESC);

CREATE INDEX idx_application_logs_service
    ON application_logs (service, occurred_at DESC, event_id DESC);

CREATE INDEX idx_application_logs_document_id
    ON application_logs (document_id, occurred_at DESC, event_id DESC);

CREATE INDEX idx_application_logs_correlation_id
    ON application_logs (correlation_id, occurred_at DESC, event_id DESC);
