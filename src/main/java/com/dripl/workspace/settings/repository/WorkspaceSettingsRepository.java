package com.dripl.workspace.settings.repository;

import com.dripl.workspace.settings.entity.WorkspaceSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceSettingsRepository extends JpaRepository<WorkspaceSettings, UUID> {

    Optional<WorkspaceSettings> findByWorkspaceId(UUID workspaceId);
}
