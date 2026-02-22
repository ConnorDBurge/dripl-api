CREATE TABLE budget_period_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    budget_id UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    expected_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    UNIQUE(budget_id, category_id, period_start)
);

CREATE INDEX idx_budget_period_entries_workspace_id ON budget_period_entries(workspace_id);
CREATE INDEX idx_budget_period_entries_budget_id ON budget_period_entries(budget_id);
CREATE INDEX idx_budget_period_entries_budget_lookup ON budget_period_entries(budget_id, period_start);
