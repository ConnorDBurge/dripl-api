package com.dripl.workspace.membership.repository;

import com.dripl.workspace.membership.entity.WorkspaceMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {
    List<WorkspaceMembership> findAllByUserId(UUID userId);
    List<WorkspaceMembership> findAllByWorkspaceId(UUID workspaceId);
    Optional<WorkspaceMembership> findByUserIdAndWorkspaceId(UUID userId, UUID workspaceId);
    boolean existsByUserIdAndWorkspaceNameIgnoreCase(UUID userId, String name);
    long countByWorkspaceId(UUID workspaceId);
}
