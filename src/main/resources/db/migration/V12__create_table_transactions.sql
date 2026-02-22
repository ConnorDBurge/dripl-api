CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    recurring_item_id UUID REFERENCES recurring_items(id) ON DELETE SET NULL,
    group_id UUID REFERENCES transaction_groups(id) ON DELETE SET NULL,
    split_id UUID REFERENCES transaction_splits(id) ON DELETE SET NULL,
    date TIMESTAMP NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency_code VARCHAR(10) NOT NULL DEFAULT 'USD',
    notes VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    pending_at TIMESTAMP,
    posted_at TIMESTAMP
);

CREATE TABLE transaction_tags (
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (transaction_id, tag_id)
);

CREATE INDEX idx_transactions_workspace_id ON transactions(workspace_id);
CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_merchant_id ON transactions(merchant_id);
CREATE INDEX idx_transactions_category_id ON transactions(category_id);
CREATE INDEX idx_transactions_date ON transactions(workspace_id, date);
CREATE INDEX idx_transactions_status ON transactions(workspace_id, status);
CREATE INDEX idx_transactions_recurring_item_id ON transactions(recurring_item_id);
CREATE INDEX idx_transactions_group_id ON transactions(group_id);
CREATE INDEX idx_transactions_split_id ON transactions(split_id);
