package com.dripl.workspace.membership.mapper;

import com.dripl.workspace.membership.dto.WorkspaceMembershipDto;
import com.dripl.workspace.membership.entity.WorkspaceMembership;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MembershipMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "workspaceId", source = "workspace.id")
    WorkspaceMembershipDto toDto(WorkspaceMembership membership);

    List<WorkspaceMembershipDto> toDtos(List<WorkspaceMembership> memberships);
}
