CREATE TABLE transaction_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    workspace_id    UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    event_type      VARCHAR(50) NOT NULL,
    changes         JSONB NOT NULL DEFAULT '{}',
    performed_by    VARCHAR(255),
    performed_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transaction_events_transaction_id ON transaction_events(transaction_id);
CREATE INDEX idx_transaction_events_workspace_id ON transaction_events(workspace_id);
