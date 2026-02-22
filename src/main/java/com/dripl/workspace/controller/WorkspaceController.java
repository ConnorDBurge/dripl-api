package com.dripl.workspace.controller;

import com.dripl.auth.service.TokenService;
import com.dripl.common.annotation.UserId;
import com.dripl.workspace.service.WorkspaceService;
import com.dripl.workspace.dto.CreateWorkspaceDto;
import com.dripl.workspace.dto.SwitchWorkspaceDto;
import com.dripl.workspace.dto.WorkspaceDto;
import com.dripl.workspace.dto.WorkspaceAuthResponse;
import com.dripl.workspace.entity.Workspace;
import com.dripl.workspace.mapper.WorkspaceMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
    private final WorkspaceMapper workspaceMapper;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<WorkspaceDto>> getAllWorkspaces(@UserId UUID userId) {
        List<Workspace> workspaces = workspaceService.listAllByUserId(userId);
        return ResponseEntity.ok(workspaceMapper.toDtos(workspaces));
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkspaceAuthResponse> provisionWorkspace(
            @UserId UUID userId, @Valid @RequestBody CreateWorkspaceDto dto) {
        Workspace workspace = workspaceService.provisionWorkspace(userId, dto);
        Workspace switched = workspaceService.switchWorkspace(userId, workspace.getId());
        String token = tokenService.mintToken(userId, switched.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(workspaceMapper.toResponse(switched, token));
    }

    @PostMapping(value = "/switch", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkspaceAuthResponse> switchWorkspace(
            @UserId UUID userId, @Valid @RequestBody SwitchWorkspaceDto dto) {
        Workspace workspace = workspaceService.switchWorkspace(userId, dto.getWorkspaceId());
        String token = tokenService.mintToken(userId, workspace.getId());
        return ResponseEntity.ok(workspaceMapper.toResponse(workspace, token));
    }
}
