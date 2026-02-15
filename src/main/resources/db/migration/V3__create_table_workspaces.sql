CREATE TABLE workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by VARCHAR(255),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_by VARCHAR(255),
    name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'
);

ALTER TABLE users ADD CONSTRAINT fk_users_last_workspace
    FOREIGN KEY (last_workspace_id) REFERENCES workspaces(id) ON DELETE SET NULL;
