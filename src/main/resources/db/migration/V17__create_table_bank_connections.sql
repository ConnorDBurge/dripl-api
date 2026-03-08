CREATE TABLE bank_connections (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id     UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    provider         VARCHAR(20)  NOT NULL,
    access_token     VARCHAR(512) NOT NULL,
    enrollment_id    VARCHAR(255),
    institution_id   VARCHAR(255),
    institution_name VARCHAR(255),
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_synced_at   TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255)
);

CREATE INDEX idx_bank_connections_workspace ON bank_connections(workspace_id);
CREATE INDEX idx_bank_connections_provider ON bank_connections(provider);
