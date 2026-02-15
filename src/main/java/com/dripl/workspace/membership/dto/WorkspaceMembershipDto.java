package com.dripl.workspace.membership.dto;

import com.dripl.workspace.membership.enums.MembershipStatus;
import com.dripl.workspace.membership.enums.Role;
import com.dripl.workspace.membership.entity.WorkspaceMembership;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
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

    public static WorkspaceMembershipDto fromEntity(WorkspaceMembership membership) {
        return WorkspaceMembershipDto.builder()
                .userId(membership.getUser().getId())
                .workspaceId(membership.getWorkspace().getId())
                .roles(membership.getRoles())
                .status(membership.getStatus())
                .joinedAt(membership.getJoinedAt())
                .build();
    }

    public static List<WorkspaceMembershipDto> fromEntities(List<WorkspaceMembership> memberships) {
        return memberships.stream().map(WorkspaceMembershipDto::fromEntity).toList();
    }
}
