CREATE TABLE budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    anchor_day_1 INTEGER,
    anchor_day_2 INTEGER,
    interval_days INTEGER,
    anchor_date DATE,
    UNIQUE(workspace_id, name)
);

CREATE TABLE budget_accounts (
    budget_id UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    PRIMARY KEY (budget_id, account_id)
);

CREATE INDEX idx_budgets_workspace_id ON budgets(workspace_id);
CREATE INDEX idx_budget_accounts_budget_id ON budget_accounts(budget_id);
