CREATE TABLE recurring_item_overrides (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    recurring_item_id UUID NOT NULL REFERENCES recurring_items(id) ON DELETE CASCADE,
    occurrence_date DATE NOT NULL,
    amount          DECIMAL(19, 4),
    notes           VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_by      VARCHAR(255),
    UNIQUE(recurring_item_id, occurrence_date)
);

CREATE INDEX idx_ri_overrides_workspace ON recurring_item_overrides(workspace_id);
CREATE INDEX idx_ri_overrides_ri_id ON recurring_item_overrides(recurring_item_id);
