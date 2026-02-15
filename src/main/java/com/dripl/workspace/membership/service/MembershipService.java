package com.dripl.workspace.membership.service;

import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.user.entity.User;
import com.dripl.user.repository.UserRepository;
import com.dripl.workspace.entity.Workspace;
import com.dripl.workspace.repository.WorkspaceRepository;
import com.dripl.workspace.membership.entity.WorkspaceMembership;
import com.dripl.workspace.membership.repository.WorkspaceMembershipRepository;
import com.dripl.workspace.membership.dto.UpdateMembershipDto;
import com.dripl.workspace.membership.enums.MembershipStatus;
import com.dripl.workspace.membership.enums.Role;
import com.dripl.workspace.membership.event.MembershipDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

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
                .createdBy(user.getEmail())
                .build();

        return membershipRepository.save(membership);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMembership> listAllUserMemberships(UUID userId) {
        return membershipRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMembership> listAllWorkspaceMemberships(UUID workspaceId) {
        return membershipRepository.findAllByWorkspaceId(workspaceId);
    }

    @Transactional(readOnly = true)
    public Optional<WorkspaceMembership> findMembership(UUID userId, UUID workspaceId) {
        return membershipRepository.findByUserIdAndWorkspaceId(userId, workspaceId);
    }

    @Transactional(readOnly = true)
    public boolean existsByUserAndWorkspaceName(UUID userId, String workspaceName) {
        return membershipRepository.existsByUserIdAndWorkspaceNameIgnoreCase(userId, workspaceName);
    }

    @Transactional
    public WorkspaceMembership updateMembership(UUID userId, UUID workspaceId, UpdateMembershipDto dto) {
        WorkspaceMembership membership = membershipRepository.findByUserIdAndWorkspaceId(userId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));
        membership.setRoles(dto.getRoles());
        return membershipRepository.save(membership);
    }

    @Transactional
    public void deleteMembership(UUID userId, UUID workspaceId) {
        WorkspaceMembership membership = membershipRepository.findByUserIdAndWorkspaceId(userId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));
        membershipRepository.delete(membership);
        eventPublisher.publishEvent(new MembershipDeletedEvent(workspaceId, MDC.get("correlationId")));
    }
}
