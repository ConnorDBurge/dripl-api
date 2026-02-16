package com.dripl.workspace.mapper;

import com.dripl.workspace.dto.WorkspaceDto;
import com.dripl.workspace.dto.WorkspaceAuthResponse;
import com.dripl.workspace.entity.Workspace;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WorkspaceMapper {

    WorkspaceDto toDto(Workspace workspace);

    List<WorkspaceDto> toDtos(List<Workspace> workspaces);

    @Mapping(target = "token", source = "token")
    WorkspaceAuthResponse toResponse(Workspace workspace, String token);
}
