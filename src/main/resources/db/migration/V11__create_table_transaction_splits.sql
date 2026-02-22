CREATE TABLE transaction_splits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    total_amount NUMERIC(19, 4) NOT NULL,
    currency_code VARCHAR(10) NOT NULL,
    date TIMESTAMP NOT NULL
);

CREATE INDEX idx_transaction_splits_workspace_id ON transaction_splits(workspace_id);
