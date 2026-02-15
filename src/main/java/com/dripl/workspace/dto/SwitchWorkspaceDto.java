package com.dripl.workspace.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SwitchWorkspaceDto {

    @NotNull(message = "Workspace ID must be provided")
    private UUID workspaceId;
}
