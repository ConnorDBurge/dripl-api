ALTER TABLE transactions ADD COLUMN split_id UUID REFERENCES transaction_splits(id) ON DELETE SET NULL;

CREATE INDEX idx_transactions_split_id ON transactions(split_id);
