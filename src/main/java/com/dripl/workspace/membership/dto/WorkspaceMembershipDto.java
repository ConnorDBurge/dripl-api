package com.dripl.workspace.membership.dto;

import com.dripl.workspace.membership.enums.MembershipStatus;
import com.dripl.workspace.membership.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceMembershipDto {
    private UUID userId;
    private UUID workspaceId;
    private Set<Role> roles;
    private MembershipStatus status;
    private LocalDateTime joinedAt;
}
