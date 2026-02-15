package com.dripl.workspace.controller;

import com.dripl.auth.service.TokenService;
import com.dripl.common.annotation.UserId;
import com.dripl.workspace.service.WorkspaceService;
import com.dripl.workspace.dto.CreateWorkspaceDto;
import com.dripl.workspace.dto.SwitchWorkspaceDto;
import com.dripl.workspace.dto.WorkspaceDto;
import com.dripl.workspace.dto.WorkspaceResponse;
import com.dripl.workspace.entity.Workspace;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final TokenService tokenService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<WorkspaceDto>> getAllWorkspaces(@UserId UUID userId) {
        List<Workspace> workspaces = workspaceService.listAllByUserId(userId);
        return ResponseEntity.ok(WorkspaceDto.fromEntities(workspaces));
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkspaceResponse> provisionWorkspace(
            @UserId UUID userId, @Valid @RequestBody CreateWorkspaceDto dto) {
        Workspace workspace = workspaceService.provisionWorkspace(userId, dto);
        Workspace switched = workspaceService.switchWorkspace(userId, workspace.getId());
        String token = tokenService.mintToken(userId, switched.getId());
        return ResponseEntity.status(201).body(WorkspaceResponse.fromEntity(switched, token));
    }

    @PostMapping(value = "/switch", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkspaceResponse> switchWorkspace(
            @UserId UUID userId, @Valid @RequestBody SwitchWorkspaceDto dto) {
        Workspace workspace = workspaceService.switchWorkspace(userId, dto.getWorkspaceId());
        String token = tokenService.mintToken(userId, workspace.getId());
        return ResponseEntity.ok(WorkspaceResponse.fromEntity(workspace, token));
    }
}
