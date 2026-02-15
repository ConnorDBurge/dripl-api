package com.dripl.workspace;

import com.dripl.common.annotation.UserId;
import com.dripl.common.annotation.WorkspaceId;
import com.dripl.user.UserDto;
import com.dripl.workspace.membership.CreateMembershipDto;
import com.dripl.workspace.membership.MembershipService;
import com.dripl.workspace.membership.UpdateMembershipDto;
import com.dripl.workspace.membership.WorkspaceMembership;
import com.dripl.workspace.membership.WorkspaceMembershipDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/workspaces/current")
public class CurrentWorkspaceController {

    private final WorkspaceService workspaceService;
    private final MembershipService membershipService;

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkspaceDto> getCurrentWorkspace(@WorkspaceId UUID workspaceId) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        return ResponseEntity.ok(WorkspaceDto.fromEntity(workspace));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkspaceDto> updateCurrentWorkspace(
            @WorkspaceId UUID workspaceId, @UserId UUID userId,
            @Valid @RequestBody UpdateWorkspaceDto dto) {
        Workspace workspace = workspaceService.updateWorkspace(workspaceId, userId, dto);
        return ResponseEntity.ok(WorkspaceDto.fromEntity(workspace));
    }

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/members", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<WorkspaceMembershipDto>> getMembers(@WorkspaceId UUID workspaceId) {
        List<WorkspaceMembership> memberships = workspaceService.listAllMembers(workspaceId);
        return ResponseEntity.ok(WorkspaceMembershipDto.fromEntities(memberships));
    }

    @PreAuthorize("hasAuthority('OWNER')")
    @PostMapping(value = "/members", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkspaceMembershipDto> addMember(
            @WorkspaceId UUID workspaceId,
            @Valid @RequestBody CreateMembershipDto dto) {
        WorkspaceMembership membership = membershipService
                .createMembership(dto.getUserId(), workspaceId, dto.getRoles());
        return ResponseEntity.status(201).body(WorkspaceMembershipDto.fromEntity(membership));
    }

    @PreAuthorize("hasAuthority('OWNER')")
    @PatchMapping(value = "/members/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkspaceMembershipDto> updateMember(
            @WorkspaceId UUID workspaceId, @PathVariable UUID userId,
            @Valid @RequestBody UpdateMembershipDto dto) {
        WorkspaceMembership membership = membershipService.updateMembership(userId, workspaceId, dto);
        return ResponseEntity.ok(WorkspaceMembershipDto.fromEntity(membership));
    }

    @PreAuthorize("hasAuthority('OWNER')")
    @DeleteMapping(value = "/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @WorkspaceId UUID workspaceId, @PathVariable UUID userId) {
        membershipService.deleteMembership(userId, workspaceId);
        return ResponseEntity.noContent().build();
    }
}
