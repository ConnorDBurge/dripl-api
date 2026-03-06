package com.dripl.workspace.mapper;

import com.dripl.workspace.dto.WorkspaceResponse;
import com.dripl.workspace.dto.WorkspaceAuthResponse;
import com.dripl.workspace.entity.Workspace;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WorkspaceMapper {

    WorkspaceResponse toDto(Workspace workspace);

    List<WorkspaceResponse> toDtos(List<Workspace> workspaces);

    @Mapping(target = "token", source = "token")
    WorkspaceAuthResponse toResponse(Workspace workspace, String token);
}
