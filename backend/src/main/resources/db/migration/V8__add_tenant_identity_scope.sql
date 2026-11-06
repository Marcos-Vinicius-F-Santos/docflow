-- Fase 4: identity and tenant boundary. Expand-only and reversible by dropping
-- the new objects/columns in a controlled rollback migration if required.
CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    slug VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    auth_subject VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(320),
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_app_users_role CHECK (role IN ('USER', 'OPERATOR', 'ADMIN'))
);

CREATE INDEX idx_app_users_tenant_id ON app_users (tenant_id);

INSERT INTO tenants (id, slug, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'bootstrap', 'DocFlow bootstrap tenant')
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_users (id, tenant_id, auth_subject, email, role)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'bootstrap',
    'bootstrap@docflow.local',
    'ADMIN'
)
ON CONFLICT (auth_subject) DO NOTHING;

ALTER TABLE documents ADD COLUMN tenant_id UUID;
ALTER TABLE documents ADD COLUMN owner_subject VARCHAR(255);
UPDATE documents
   SET tenant_id = '00000000-0000-0000-0000-000000000001',
       owner_subject = 'bootstrap'
 WHERE tenant_id IS NULL OR owner_subject IS NULL;
ALTER TABLE documents ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE documents ALTER COLUMN owner_subject SET NOT NULL;
ALTER TABLE documents ADD CONSTRAINT fk_documents_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants(id);
CREATE INDEX idx_documents_tenant_created
    ON documents (tenant_id, created_at DESC, id DESC);

ALTER TABLE application_logs ADD COLUMN tenant_id UUID;
UPDATE application_logs
   SET tenant_id = '00000000-0000-0000-0000-000000000001'
 WHERE tenant_id IS NULL;
ALTER TABLE application_logs ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE application_logs ADD CONSTRAINT fk_application_logs_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants(id);
CREATE INDEX idx_application_logs_tenant_occurred
    ON application_logs (tenant_id, occurred_at DESC, event_id DESC);
