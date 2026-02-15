package com.dripl.workspace.membership;

import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.user.User;
import com.dripl.user.UserRepository;
import com.dripl.workspace.Workspace;
import com.dripl.workspace.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipService {

    private final WorkspaceMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;

    @Transactional
    public WorkspaceMembership createMembership(UUID userId, UUID workspaceId, Set<Role> roles) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));

        WorkspaceMembership membership = WorkspaceMembership.builder()
                .user(user)
                .workspace(workspace)
                .roles(roles)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

        return membershipRepository.save(membership);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMembership> listAllUserMemberships(UUID userId) {
        return membershipRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Optional<WorkspaceMembership> findMembership(UUID userId, UUID workspaceId) {
        return membershipRepository.findByUserIdAndWorkspaceId(userId, workspaceId);
    }

    @Transactional
    public void deleteMembership(UUID userId, UUID workspaceId) {
        WorkspaceMembership membership = membershipRepository.findByUserIdAndWorkspaceId(userId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));
        membershipRepository.delete(membership);
    }
}
