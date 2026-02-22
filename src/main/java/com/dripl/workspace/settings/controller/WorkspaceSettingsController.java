package com.dripl.workspace.settings.controller;

import com.dripl.common.annotation.WorkspaceId;
import com.dripl.workspace.settings.dto.UpdateWorkspaceSettingsDto;
import com.dripl.workspace.settings.dto.WorkspaceSettingsDto;
import com.dripl.workspace.settings.service.WorkspaceSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/workspaces/current/settings")
public class WorkspaceSettingsController {

    private final WorkspaceSettingsService settingsService;

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkspaceSettingsDto> getSettings(@WorkspaceId UUID workspaceId) {
        return ResponseEntity.ok(settingsService.getSettings(workspaceId));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkspaceSettingsDto> updateSettings(
            @WorkspaceId UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceSettingsDto dto) {
        return ResponseEntity.ok(settingsService.updateSettings(workspaceId, dto));
    }
}
