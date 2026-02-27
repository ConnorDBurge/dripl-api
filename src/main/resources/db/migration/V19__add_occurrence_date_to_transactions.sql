ALTER TABLE transactions ADD COLUMN occurrence_date DATE;

CREATE INDEX idx_transactions_occurrence_date ON transactions(occurrence_date);
