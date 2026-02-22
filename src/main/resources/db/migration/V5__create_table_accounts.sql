CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by VARCHAR(255),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_by VARCHAR(255),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(50) NOT NULL,
    sub_type VARCHAR(50) NOT NULL,
    balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
    starting_balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    institution_name VARCHAR(120),
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    balance_last_updated TIMESTAMP,
    closed_at TIMESTAMP,
    external_id VARCHAR(255)
);

CREATE INDEX idx_accounts_workspace_id ON accounts(workspace_id);
