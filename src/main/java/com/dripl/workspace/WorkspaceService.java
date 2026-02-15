package com.dripl.workspace;

import com.dripl.common.exception.AccessDeniedException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.user.User;
import com.dripl.user.UserRepository;
import com.dripl.workspace.membership.MembershipService;
import com.dripl.workspace.membership.Role;
import com.dripl.workspace.membership.WorkspaceMembership;
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

    @Transactional
    public Workspace provisionWorkspace(UUID userId, CreateWorkspaceDto dto) {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(dto.getName())
                .status(WorkspaceStatus.ACTIVE)
                .build());

        membershipService.createMembership(userId, workspace.getId(),
                Set.of(Role.OWNER, Role.WRITE, Role.READ));

        log.info("Provisioned workspace {} for user {}", workspace.getId(), userId);
        return workspace;
    }

    @Transactional
    public Workspace switchWorkspace(UUID userId, UUID workspaceId) {
        log.info("User {} switching to workspace {}", userId, workspaceId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        membershipService.findMembership(userId, workspaceId)
                .orElseThrow(() -> new AccessDeniedException("User does not have access to workspace"));

        Workspace workspace = getWorkspace(workspaceId);
        user.setLastWorkspaceId(workspace.getId());
        userRepository.save(user);

        log.info("Successfully switched user {} to workspace {}", userId, workspace.getId());
        return workspace;
    }

    @Transactional
    public void deleteWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        log.info("Deleting workspace {}", workspaceId);
        workspaceRepository.delete(workspace);
    }
}
