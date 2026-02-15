package com.dripl.workspace;

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
import com.dripl.workspace.membership.enums.MembershipStatus;
import com.dripl.workspace.membership.enums.Role;
import com.dripl.workspace.membership.entity.WorkspaceMembership;
import com.dripl.workspace.repository.WorkspaceRepository;
import com.dripl.workspace.service.WorkspaceService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private MembershipService membershipService;
    @Mock private UserRepository userRepository;

    @InjectMocks private WorkspaceService workspaceService;

    private UUID userId;
    private UUID workspaceId;
    private User testUser;
    private Workspace testWorkspace;

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
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        testWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .status(WorkspaceStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getWorkspace_existing_returnsWorkspace() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));

        Workspace result = workspaceService.getWorkspace(workspaceId);

        assertThat(result.getName()).isEqualTo("Test Workspace");
        assertThat(result.getStatus()).isEqualTo(WorkspaceStatus.ACTIVE);
    }

    @Test
    void getWorkspace_nonExistent_throwsException() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.getWorkspace(workspaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Workspace not found");
    }

    @Test
    void listAllByUserId_returnsMemberWorkspaces() {
        WorkspaceMembership membership = WorkspaceMembership.builder()
                .user(testUser)
                .workspace(testWorkspace)
                .roles(Set.of(Role.OWNER))
                .build();

        when(membershipService.listAllUserMemberships(userId)).thenReturn(List.of(membership));

        List<Workspace> result = workspaceService.listAllByUserId(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Test Workspace");
    }

    @Test
    void provisionWorkspace_createsWorkspaceAndMembership() {
        when(membershipService.existsByUserAndWorkspaceName(userId, "New Workspace")).thenReturn(false);
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(testWorkspace);
        when(membershipService.createMembership(eq(userId), eq(workspaceId), any()))
                .thenReturn(WorkspaceMembership.builder().build());

        CreateWorkspaceDto dto = CreateWorkspaceDto.builder().name("New Workspace").build();
        Workspace result = workspaceService.provisionWorkspace(userId, dto);

        assertThat(result).isNotNull();
        verify(workspaceRepository).save(any(Workspace.class));
        verify(membershipService).createMembership(eq(userId), eq(workspaceId),
                eq(Set.of(Role.OWNER, Role.WRITE, Role.READ)));
    }

    @Test
    void provisionWorkspace_duplicateName_throwsConflict() {
        when(membershipService.existsByUserAndWorkspaceName(userId, "Test Workspace")).thenReturn(true);

        CreateWorkspaceDto dto = CreateWorkspaceDto.builder().name("Test Workspace").build();

        assertThatThrownBy(() -> workspaceService.provisionWorkspace(userId, dto))
                .isInstanceOf(ConflictException.class)
                .hasMessage("You already have a workspace named 'Test Workspace'");

        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void switchWorkspace_validMembership_switchesWorkspace() {
        WorkspaceMembership membership = WorkspaceMembership.builder()
                .user(testUser)
                .workspace(testWorkspace)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(membershipService.findMembership(userId, workspaceId)).thenReturn(Optional.of(membership));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        Workspace result = workspaceService.switchWorkspace(userId, workspaceId);

        assertThat(result.getId()).isEqualTo(workspaceId);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void switchWorkspace_noMembership_throwsAccessDenied() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(membershipService.findMembership(userId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.switchWorkspace(userId, workspaceId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("User does not have access to workspace");
    }

    @Test
    void switchWorkspace_userNotFound_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.switchWorkspace(userId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void deleteWorkspace_existing_deletesWorkspace() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));

        workspaceService.deleteWorkspace(workspaceId);

        verify(workspaceRepository).delete(testWorkspace);
    }

    @Test
    void deleteWorkspace_nonExistent_throwsException() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.deleteWorkspace(workspaceId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listAllMembers_returnsMemberships() {
        WorkspaceMembership membership = WorkspaceMembership.builder()
                .user(testUser)
                .workspace(testWorkspace)
                .roles(Set.of(Role.OWNER))
                .status(MembershipStatus.ACTIVE)
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(membershipService.listAllWorkspaceMemberships(workspaceId)).thenReturn(List.of(membership));

        List<WorkspaceMembership> result = workspaceService.listAllMembers(workspaceId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUser().getId()).isEqualTo(userId);
    }

    @Test
    void updateWorkspace_validName_updatesWorkspace() {
        UpdateWorkspaceDto dto = UpdateWorkspaceDto.builder().name("Renamed").build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(membershipService.existsByUserAndWorkspaceName(userId, "Renamed")).thenReturn(false);
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(testWorkspace);

        Workspace result = workspaceService.updateWorkspace(workspaceId, userId, dto);

        assertThat(result).isNotNull();
        verify(workspaceRepository).save(any(Workspace.class));
    }

    @Test
    void updateWorkspace_duplicateName_throwsConflict() {
        UpdateWorkspaceDto dto = UpdateWorkspaceDto.builder().name("Existing Name").build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(membershipService.existsByUserAndWorkspaceName(userId, "Existing Name")).thenReturn(true);

        assertThatThrownBy(() -> workspaceService.updateWorkspace(workspaceId, userId, dto))
                .isInstanceOf(ConflictException.class);

        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void updateWorkspace_nullName_savesWithoutNameChange() {
        UpdateWorkspaceDto dto = UpdateWorkspaceDto.builder().build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(testWorkspace);

        Workspace result = workspaceService.updateWorkspace(workspaceId, userId, dto);

        assertThat(result.getName()).isEqualTo("Test Workspace");
        verify(membershipService, never()).existsByUserAndWorkspaceName(any(), any());
    }

    @Test
    void listAllByUserId_noMemberships_returnsEmptyList() {
        when(membershipService.listAllUserMemberships(userId)).thenReturn(List.of());

        List<Workspace> result = workspaceService.listAllByUserId(userId);

        assertThat(result).isEmpty();
    }

    @Test
    void listAllByUserId_multipleMemberships_returnsAllWorkspaces() {
        UUID workspace2Id = UUID.randomUUID();
        Workspace workspace2 = Workspace.builder()
                .id(workspace2Id)
                .name("Second Workspace")
                .status(WorkspaceStatus.ACTIVE)
                .build();

        WorkspaceMembership m1 = WorkspaceMembership.builder()
                .user(testUser).workspace(testWorkspace).build();
        WorkspaceMembership m2 = WorkspaceMembership.builder()
                .user(testUser).workspace(workspace2).build();

        when(membershipService.listAllUserMemberships(userId)).thenReturn(List.of(m1, m2));

        List<Workspace> result = workspaceService.listAllByUserId(userId);

        assertThat(result).hasSize(2);
    }

    @Test
    void listAllMembers_nonExistentWorkspace_throwsException() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.listAllMembers(workspaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Workspace not found");
    }

    @Test
    void switchWorkspace_updatesLastWorkspaceId() {
        WorkspaceMembership membership = WorkspaceMembership.builder()
                .user(testUser).workspace(testWorkspace).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(membershipService.findMembership(userId, workspaceId)).thenReturn(Optional.of(membership));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        workspaceService.switchWorkspace(userId, workspaceId);

        verify(userRepository).save(argThat(user ->
                user.getLastWorkspaceId().equals(workspaceId)
        ));
    }
}
