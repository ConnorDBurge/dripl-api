ALTER TABLE transactions ADD COLUMN group_id UUID REFERENCES transaction_groups(id) ON DELETE SET NULL;

CREATE INDEX idx_transactions_group_id ON transactions(group_id);
