package com.dripl.common.event;

import java.util.UUID;

/**
 * Implemented by entities that belong to a workspace and can publish domain events.
 */
public interface WorkspaceScoped {
    UUID getId();
    UUID getWorkspaceId();
}
