package com.dripl.workspace.membership.event;

import java.util.UUID;

public record MembershipDeletedEvent(UUID workspaceId, String correlationId) {
}
