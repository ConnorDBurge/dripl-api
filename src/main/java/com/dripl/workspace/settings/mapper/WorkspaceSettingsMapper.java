package com.dripl.workspace.settings.mapper;

import com.dripl.workspace.settings.dto.WorkspaceSettingsDto;
import com.dripl.workspace.settings.entity.WorkspaceSettings;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WorkspaceSettingsMapper {

    WorkspaceSettingsDto toDto(WorkspaceSettings settings);
}
