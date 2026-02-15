package com.dripl.user;

import com.dripl.auth.JwtUtil;
import com.dripl.common.exception.ConflictException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.workspace.CreateWorkspaceDto;
import com.dripl.workspace.Workspace;
import com.dripl.workspace.WorkspaceService;
import com.dripl.workspace.WorkspaceStatus;
import com.dripl.workspace.membership.MembershipService;
import com.dripl.workspace.membership.Role;
import com.dripl.workspace.membership.WorkspaceMembership;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WorkspaceService workspaceService;
    @Mock private MembershipService membershipService;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks private UserService userService;

    private User testUser;
    private UUID userId;
    private UUID workspaceId;

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
                .lastWorkspaceId(workspaceId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getUser_existingUser_returnsUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        User result = userService.getUser(userId);

        assertThat(result.getEmail()).isEqualTo("connor@test.com");
        verify(userRepository).findById(userId);
    }

    @Test
    void getUser_nonExistentUser_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void updateUser_validUpdate_updatesFields() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UpdateUserDto dto = UpdateUserDto.builder()
                .givenName("Updated")
                .familyName("Name")
                .build();

        User result = userService.updateUser(userId, dto);

        assertThat(result).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateUser_duplicateEmail_throwsConflict() {
        User otherUser = User.builder().id(UUID.randomUUID()).email("taken@test.com").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmail("taken@test.com")).thenReturn(Optional.of(otherUser));

        UpdateUserDto dto = UpdateUserDto.builder().email("taken@test.com").build();

        assertThatThrownBy(() -> userService.updateUser(userId, dto))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email already in use");
    }

    @Test
    void updateUser_sameEmailSameUser_succeeds() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmail("connor@test.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UpdateUserDto dto = UpdateUserDto.builder().email("connor@test.com").build();

        User result = userService.updateUser(userId, dto);
        assertThat(result).isNotNull();
    }

    @Test
    void deleteUser_existingUser_deletesUserAndMemberships() {
        WorkspaceMembership membership = WorkspaceMembership.builder()
                .user(testUser)
                .workspace(Workspace.builder().id(workspaceId).build())
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(membershipService.listAllUserMemberships(userId)).thenReturn(List.of(membership));

        userService.deleteUser(userId);

        verify(membershipService).deleteMembership(userId, workspaceId);
        verify(userRepository).delete(testUser);
    }

    @Test
    void bootstrapUser_newUser_createsUserAndWorkspace() {
        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .name("Connor's Workspace")
                .status(WorkspaceStatus.ACTIVE)
                .build();

        WorkspaceMembership membership = WorkspaceMembership.builder()
                .roles(Set.of(Role.OWNER, Role.READ, Role.WRITE))
                .build();

        when(userRepository.findByEmail("connor@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> {
                    User u = inv.getArgument(0);
                    u.setId(userId);
                    u.setCreatedAt(LocalDateTime.now());
                    u.setUpdatedAt(LocalDateTime.now());
                    return u;
                });
        when(workspaceService.provisionWorkspace(eq(userId), any(CreateWorkspaceDto.class)))
                .thenReturn(workspace);
        when(membershipService.findMembership(userId, workspaceId))
                .thenReturn(Optional.of(membership));
        when(jwtUtil.generateToken(eq(userId), eq(workspaceId), any(), anyList()))
                .thenReturn("test-jwt-token");

        UserService.BootstrapResponse response = userService.bootstrapUser(
                "connor@test.com", "Connor", "Burge");

        assertThat(response.user().getEmail()).isEqualTo("connor@test.com");
        assertThat(response.token()).isEqualTo("test-jwt-token");
        verify(workspaceService).provisionWorkspace(eq(userId), any(CreateWorkspaceDto.class));
    }

    @Test
    void bootstrapUser_existingUserWithWorkspace_returnsExistingUser() {
        WorkspaceMembership membership = WorkspaceMembership.builder()
                .roles(Set.of(Role.READ, Role.WRITE))
                .build();

        when(userRepository.findByEmail("connor@test.com")).thenReturn(Optional.of(testUser));
        when(membershipService.findMembership(userId, workspaceId))
                .thenReturn(Optional.of(membership));
        when(jwtUtil.generateToken(eq(userId), eq(workspaceId), any(), anyList()))
                .thenReturn("test-jwt-token");

        UserService.BootstrapResponse response = userService.bootstrapUser(
                "connor@test.com", "Connor", "Burge");

        assertThat(response.user().getEmail()).isEqualTo("connor@test.com");
        verify(workspaceService, never()).provisionWorkspace(any(), any());
    }
}
