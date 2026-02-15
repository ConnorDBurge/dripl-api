package com.dripl.workspace.dto;

import com.dripl.workspace.entity.Workspace;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class WorkspaceResponse extends WorkspaceDto {
    private String token;

    public static WorkspaceResponse fromEntity(Workspace workspace, String token) {
        return WorkspaceResponse.builder()
                .id(workspace.getId())
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .name(workspace.getName())
                .status(workspace.getStatus().name())
                .token(token)
                .build();
    }
}
