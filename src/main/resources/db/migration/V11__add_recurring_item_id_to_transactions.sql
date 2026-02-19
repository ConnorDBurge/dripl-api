ALTER TABLE transactions ADD COLUMN recurring_item_id UUID REFERENCES recurring_items(id) ON DELETE SET NULL;

CREATE INDEX idx_transactions_recurring_item_id ON transactions(recurring_item_id);
