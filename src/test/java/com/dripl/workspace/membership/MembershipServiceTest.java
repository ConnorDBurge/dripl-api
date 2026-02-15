package com.dripl.workspace.membership;

import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.user.entity.User;
import com.dripl.user.repository.UserRepository;
import com.dripl.workspace.entity.Workspace;
import com.dripl.workspace.repository.WorkspaceRepository;
import com.dripl.workspace.enums.WorkspaceStatus;
import com.dripl.workspace.membership.dto.UpdateMembershipDto;
import com.dripl.workspace.membership.entity.WorkspaceMembership;
import com.dripl.workspace.membership.enums.MembershipStatus;
import com.dripl.workspace.membership.enums.Role;
import com.dripl.workspace.membership.event.MembershipDeletedEvent;
import com.dripl.workspace.membership.repository.WorkspaceMembershipRepository;
import com.dripl.workspace.membership.service.MembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock private WorkspaceMembershipRepository membershipRepository;
    @Mock private UserRepository userRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private MembershipService membershipService;

    private UUID userId;
    private UUID workspaceId;
    private User testUser;
    private Workspace testWorkspace;
    private WorkspaceMembership testMembership;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .email("connor@test.com")
                .givenName("Connor")
                .familyName("Burge")
                .isActive(true)
                .build();
        testWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .status(WorkspaceStatus.ACTIVE)
                .build();
        testMembership = WorkspaceMembership.builder()
                .user(testUser)
                .workspace(testWorkspace)
                .roles(Set.of(Role.OWNER, Role.WRITE, Role.READ))
                .status(MembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void listAllWorkspaceMemberships_returnsMemberships() {
        when(membershipRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(testMembership));

        List<WorkspaceMembership> result = membershipService.listAllWorkspaceMemberships(workspaceId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUser().getId()).isEqualTo(userId);
    }

    @Test
    void updateMembership_existing_updatesRoles() {
        Set<Role> newRoles = Set.of(Role.READ);
        UpdateMembershipDto dto = UpdateMembershipDto.builder().roles(newRoles).build();

        when(membershipRepository.findByUserIdAndWorkspaceId(userId, workspaceId))
                .thenReturn(Optional.of(testMembership));
        when(membershipRepository.save(any(WorkspaceMembership.class))).thenReturn(testMembership);

        WorkspaceMembership result = membershipService.updateMembership(userId, workspaceId, dto);

        assertThat(result.getRoles()).isEqualTo(newRoles);
        verify(membershipRepository).save(any(WorkspaceMembership.class));
    }

    @Test
    void updateMembership_notFound_throwsException() {
        UpdateMembershipDto dto = UpdateMembershipDto.builder().roles(Set.of(Role.READ)).build();

        when(membershipRepository.findByUserIdAndWorkspaceId(userId, workspaceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.updateMembership(userId, workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Membership not found");
    }

    @Test
    void deleteMembership_existing_deletes() {
        when(membershipRepository.findByUserIdAndWorkspaceId(userId, workspaceId))
                .thenReturn(Optional.of(testMembership));

        membershipService.deleteMembership(userId, workspaceId);

        verify(membershipRepository).delete(testMembership);
        verify(eventPublisher).publishEvent(any(MembershipDeletedEvent.class));
    }

    @Test
    void deleteMembership_notFound_throwsException() {
        when(membershipRepository.findByUserIdAndWorkspaceId(userId, workspaceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.deleteMembership(userId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Membership not found");
    }

    @Test
    void existsByUserAndWorkspaceName_returnsTrue() {
        when(membershipRepository.existsByUserIdAndWorkspaceNameIgnoreCase(userId, "Test Workspace"))
                .thenReturn(true);

        assertThat(membershipService.existsByUserAndWorkspaceName(userId, "Test Workspace")).isTrue();
    }

    @Test
    void existsByUserAndWorkspaceName_returnsFalse() {
        when(membershipRepository.existsByUserIdAndWorkspaceNameIgnoreCase(userId, "Nonexistent"))
                .thenReturn(false);

        assertThat(membershipService.existsByUserAndWorkspaceName(userId, "Nonexistent")).isFalse();
    }

    @Test
    void createMembership_validUserAndWorkspace_createsMembership() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(membershipRepository.save(any(WorkspaceMembership.class))).thenReturn(testMembership);

        Set<Role> roles = Set.of(Role.OWNER, Role.WRITE, Role.READ);
        WorkspaceMembership result = membershipService.createMembership(userId, workspaceId, roles);

        assertThat(result).isNotNull();
        verify(membershipRepository).save(any(WorkspaceMembership.class));
    }

    @Test
    void createMembership_userNotFound_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.createMembership(userId, workspaceId, Set.of(Role.READ)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void createMembership_workspaceNotFound_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.createMembership(userId, workspaceId, Set.of(Role.READ)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Workspace not found");
    }

    @Test
    void findMembership_exists_returnsOptional() {
        when(membershipRepository.findByUserIdAndWorkspaceId(userId, workspaceId))
                .thenReturn(Optional.of(testMembership));

        Optional<WorkspaceMembership> result = membershipService.findMembership(userId, workspaceId);

        assertThat(result).isPresent();
        assertThat(result.get().getUser().getId()).isEqualTo(userId);
    }

    @Test
    void findMembership_notExists_returnsEmpty() {
        when(membershipRepository.findByUserIdAndWorkspaceId(userId, workspaceId))
                .thenReturn(Optional.empty());

        Optional<WorkspaceMembership> result = membershipService.findMembership(userId, workspaceId);

        assertThat(result).isEmpty();
    }

    @Test
    void listAllUserMemberships_returnsMemberships() {
        when(membershipRepository.findAllByUserId(userId)).thenReturn(List.of(testMembership));

        List<WorkspaceMembership> result = membershipService.listAllUserMemberships(userId);

        assertThat(result).hasSize(1);
    }

    @Test
    void listAllUserMemberships_noMemberships_returnsEmpty() {
        when(membershipRepository.findAllByUserId(userId)).thenReturn(List.of());

        List<WorkspaceMembership> result = membershipService.listAllUserMemberships(userId);

        assertThat(result).isEmpty();
    }
}
