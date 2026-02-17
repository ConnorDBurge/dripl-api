package com.dripl.workspace.service;

import com.dripl.common.exception.AccessDeniedException;
import com.dripl.common.exception.ConflictException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.user.entity.User;
import com.dripl.user.repository.UserRepository;
import com.dripl.workspace.dto.CreateWorkspaceDto;
import com.dripl.workspace.dto.UpdateWorkspaceDto;
import com.dripl.workspace.entity.Workspace;
import com.dripl.workspace.enums.WorkspaceStatus;
import com.dripl.workspace.membership.service.MembershipService;
import com.dripl.workspace.membership.enums.Role;
import com.dripl.workspace.membership.entity.WorkspaceMembership;
import com.dripl.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final MembershipService membershipService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Workspace getWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
    }

    @Transactional(readOnly = true)
    public List<Workspace> listAllByUserId(UUID userId) {
        return membershipService.listAllUserMemberships(userId)
                .stream().map(WorkspaceMembership::getWorkspace).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMembership> listAllMembers(UUID workspaceId) {
        getWorkspace(workspaceId);
        return membershipService.listAllWorkspaceMemberships(workspaceId);
    }

    @Transactional
    public Workspace updateWorkspace(UUID workspaceId, UUID userId, UpdateWorkspaceDto dto) {
        Workspace workspace = getWorkspace(workspaceId);

        if (dto.getName() != null) {
            if (membershipService.existsByUserAndWorkspaceName(userId, dto.getName())) {
                throw new ConflictException("You already have a workspace named '" + dto.getName() + "'");
            }
            workspace.setName(dto.getName());
        }

        log.info("Updating workspace '{}' ({})", workspace.getName(), workspaceId);
        return workspaceRepository.save(workspace);
    }

    @Transactional
    public Workspace provisionWorkspace(UUID userId, CreateWorkspaceDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (membershipService.existsByUserAndWorkspaceName(userId, dto.getName())) {
            throw new ConflictException("You already have a workspace named '" + dto.getName() + "'");
        }

        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(dto.getName())
                .status(WorkspaceStatus.ACTIVE)
                .createdBy(user.getEmail())
                .build());

        membershipService.createMembership(userId, workspace.getId(),
                Set.of(Role.OWNER, Role.WRITE, Role.DELETE, Role.READ));

        log.info("Provisioned workspace '{}' ({}) for user {} ({})", workspace.getName(), workspace.getId(), user.getEmail(), userId);
        return workspace;
    }

    @Transactional
    public Workspace switchWorkspace(UUID userId, UUID workspaceId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        membershipService.findMembership(userId, workspaceId)
                .orElseThrow(() -> new AccessDeniedException("User does not have access to workspace"));

        Workspace workspace = getWorkspace(workspaceId);
        user.setLastWorkspaceId(workspace.getId());
        userRepository.save(user);

        log.info("User {} ({}) switching to workspace '{}' ({})", user.getEmail(), userId, workspace.getName(), workspace.getId());
        return workspace;
    }

    @Transactional
    public void deleteWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        log.info("Deleting workspace '{}' ({})", workspace.getName(), workspaceId);
        workspaceRepository.delete(workspace);
    }
}
