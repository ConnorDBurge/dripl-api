package com.dripl.workspace.membership;

import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.user.User;
import com.dripl.user.UserRepository;
import com.dripl.workspace.Workspace;
import com.dripl.workspace.WorkspaceRepository;
import com.dripl.workspace.WorkspaceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
