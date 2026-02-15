package com.dripl.workspace.membership.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class MembershipDeletedEvent {
    private final UUID workspaceId;
    private final String correlationId;
}
