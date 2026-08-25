CREATE TABLE documents (
	id UUID PRIMARY KEY,
	status VARCHAR(50) NOT NULL,
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    CONSTRAINT chk_size_bytes CHECK (size_bytes >= 0),

	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL



);
