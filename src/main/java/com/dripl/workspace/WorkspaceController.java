package com.dripl.workspace;

import com.dripl.auth.JwtUtil;
import com.dripl.common.annotation.UserId;
import com.dripl.workspace.membership.MembershipService;
import com.dripl.workspace.membership.Role;
import com.dripl.workspace.membership.WorkspaceMembership;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final MembershipService membershipService;
    private final JwtUtil jwtUtil;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<WorkspaceDto>> getAllWorkspaces(@UserId UUID userId) {
        List<Workspace> workspaces = workspaceService.listAllByUserId(userId);
        return ResponseEntity.ok(WorkspaceDto.fromEntities(workspaces));
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> provisionWorkspace(
            @UserId UUID userId, @Valid @RequestBody CreateWorkspaceDto dto) {
        Workspace workspace = workspaceService.provisionWorkspace(userId, dto);
        Workspace switched = workspaceService.switchWorkspace(userId, workspace.getId());

        String token = mintWorkspaceToken(userId, switched.getId());

        return ResponseEntity.status(201).body(Map.of(
                "workspace", WorkspaceDto.fromEntity(switched),
                "token", token
        ));
    }

    @PostMapping(value = "/switch", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> switchWorkspace(
            @UserId UUID userId, @Valid @RequestBody SwitchWorkspaceDto dto) {
        Workspace workspace = workspaceService.switchWorkspace(userId, dto.getWorkspaceId());

        String token = mintWorkspaceToken(userId, workspace.getId());

        return ResponseEntity.ok(Map.of(
                "workspace", WorkspaceDto.fromEntity(workspace),
                "token", token
        ));
    }

    private String mintWorkspaceToken(UUID userId, UUID workspaceId) {
        List<String> roles = membershipService.findMembership(userId, workspaceId)
                .map(m -> m.getRoles().stream().map(Role::name).collect(Collectors.toList()))
                .orElse(List.of("READ", "WRITE"));

        return jwtUtil.generateToken(userId, workspaceId, userId.toString(), roles);
    }
}
