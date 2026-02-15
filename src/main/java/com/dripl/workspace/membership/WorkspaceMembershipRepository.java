package com.dripl.workspace.membership;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {
    List<WorkspaceMembership> findAllByUserId(UUID userId);
    Optional<WorkspaceMembership> findByUserIdAndWorkspaceId(UUID userId, UUID workspaceId);
}
