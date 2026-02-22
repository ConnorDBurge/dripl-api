CREATE TABLE budget_category_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    budget_id UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    rollover_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
    UNIQUE(budget_id, category_id)
);

CREATE INDEX idx_budget_category_configs_workspace_id ON budget_category_configs(workspace_id);
CREATE INDEX idx_budget_category_configs_budget_id ON budget_category_configs(budget_id);
