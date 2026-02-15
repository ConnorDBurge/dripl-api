package com.dripl.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    @Query("SELECT COUNT(w) > 0 FROM Workspace w JOIN WorkspaceMembership m ON m.workspace = w " +
            "WHERE m.user.id = :userId AND LOWER(w.name) = LOWER(:name)")
    boolean existsByUserMembershipAndName(UUID userId, String name);
}
