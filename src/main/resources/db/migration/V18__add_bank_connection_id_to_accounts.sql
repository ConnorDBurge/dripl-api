ALTER TABLE accounts
    ADD COLUMN bank_connection_id UUID REFERENCES bank_connections(id) ON DELETE SET NULL;

CREATE INDEX idx_accounts_bank_connection ON accounts(bank_connection_id);
