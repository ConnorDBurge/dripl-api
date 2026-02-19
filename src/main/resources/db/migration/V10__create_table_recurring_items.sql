CREATE TABLE recurring_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    description VARCHAR(255),
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency_code VARCHAR(10) NOT NULL DEFAULT 'USD',
    notes VARCHAR(500),
    frequency_granularity VARCHAR(20) NOT NULL,
    frequency_quantity INT NOT NULL DEFAULT 1,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE recurring_item_anchor_dates (
    recurring_item_id UUID NOT NULL REFERENCES recurring_items(id) ON DELETE CASCADE,
    anchor_date TIMESTAMP NOT NULL,
    anchor_dates_order INT NOT NULL DEFAULT 0
);

CREATE TABLE recurring_item_tags (
    recurring_item_id UUID NOT NULL REFERENCES recurring_items(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (recurring_item_id, tag_id)
);

CREATE INDEX idx_recurring_items_workspace_id ON recurring_items(workspace_id);
CREATE INDEX idx_recurring_items_account_id ON recurring_items(account_id);
CREATE INDEX idx_recurring_items_merchant_id ON recurring_items(merchant_id);
CREATE INDEX idx_recurring_items_status ON recurring_items(workspace_id, status);
