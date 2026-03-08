ALTER TABLE transactions
    ADD COLUMN external_id VARCHAR(255);

CREATE UNIQUE INDEX idx_transactions_external_id
    ON transactions(external_id, workspace_id)
    WHERE external_id IS NOT NULL;
