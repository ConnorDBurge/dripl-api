package com.dripl.common.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DomainEvent(
        String domain,
        String action,
        UUID entityId,
        UUID workspaceId,
        List<FieldChange> changes,
        String performedBy,
        LocalDateTime performedAt
) {
    public String eventType() {
        return domain + "." + action;
    }
}
