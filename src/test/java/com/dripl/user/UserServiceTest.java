package com.dripl.user;

import com.dripl.auth.service.TokenService;
import com.dripl.common.exception.ConflictException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.user.dto.UpdateUserDto;
import com.dripl.user.entity.User;
import com.dripl.user.repository.UserRepository;
import com.dripl.user.service.UserService;
import com.dripl.user.dto.UserResponse;
import com.dripl.workspace.dto.CreateWorkspaceDto;
import com.dripl.workspace.entity.Workspace;
import com.dripl.workspace.service.WorkspaceService;
import com.dripl.workspace.enums.WorkspaceStatus;
import com.dripl.workspace.membership.service.MembershipService;
import com.dripl.workspace.membership.entity.WorkspaceMembership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WorkspaceService workspaceService;
    @Mock private MembershipService membershipService;
    @Mock private TokenService tokenService;

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
        when(tokenService.mintToken(userId, workspaceId))
                .thenReturn("test-jwt-token");

        UserResponse response = userService.bootstrapUser(
                "connor@test.com", "Connor", "Burge");

        assertThat(response.getEmail()).isEqualTo("connor@test.com");
        assertThat(response.getToken()).isEqualTo("test-jwt-token");
        verify(workspaceService).provisionWorkspace(eq(userId), any(CreateWorkspaceDto.class));
    }

    @Test
    void bootstrapUser_existingUserWithWorkspace_returnsExistingUser() {
        when(userRepository.findByEmail("connor@test.com")).thenReturn(Optional.of(testUser));
        when(tokenService.mintToken(userId, workspaceId))
                .thenReturn("test-jwt-token");

        UserResponse response = userService.bootstrapUser(
                "connor@test.com", "Connor", "Burge");

        assertThat(response.getEmail()).isEqualTo("connor@test.com");
        verify(workspaceService, never()).provisionWorkspace(any(), any());
    }

    @Test
    void bootstrapUser_existingUserNoWorkspace_createsWorkspace() {
        testUser.setLastWorkspaceId(null);

        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .name("Connor's Workspace")
                .status(WorkspaceStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail("connor@test.com")).thenReturn(Optional.of(testUser));
        when(workspaceService.provisionWorkspace(eq(userId), any(CreateWorkspaceDto.class)))
                .thenReturn(workspace);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(tokenService.mintToken(userId, workspaceId))
                .thenReturn("test-jwt-token");

        UserResponse response = userService.bootstrapUser("connor@test.com", "Connor", "Burge");

        assertThat(response.getToken()).isEqualTo("test-jwt-token");
        verify(workspaceService).provisionWorkspace(eq(userId), any(CreateWorkspaceDto.class));
    }

    @Test
    void bootstrapUser_raceCondition_recoversExistingUser() {
        when(userRepository.findByEmail("connor@test.com"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(tokenService.mintToken(userId, workspaceId))
                .thenReturn("test-jwt-token");

        UserResponse response = userService.bootstrapUser("connor@test.com", "Connor", "Burge");

        assertThat(response.getEmail()).isEqualTo("connor@test.com");
    }

    @Test
    void bootstrapUser_nullNames_defaultsToEmail() {
        when(userRepository.findByEmail("connor@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> {
                    User u = inv.getArgument(0);
                    u.setId(userId);
                    u.setLastWorkspaceId(workspaceId);
                    return u;
                });
        when(tokenService.mintToken(userId, workspaceId))
                .thenReturn("test-jwt-token");

        UserResponse response = userService.bootstrapUser("connor@test.com", null, null);

        assertThat(response.getToken()).isEqualTo("test-jwt-token");
        verify(userRepository).save(argThat(user ->
                user.getGivenName().equals("connor@test.com") && user.getFamilyName().equals("")
        ));
    }

    @Test
    void updateUser_onlyGivenName_updatesOnlyGivenName() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserDto dto = UpdateUserDto.builder().givenName("NewFirst").build();

        User result = userService.updateUser(userId, dto);

        assertThat(result.getGivenName()).isEqualTo("NewFirst");
        assertThat(result.getFamilyName()).isEqualTo("Burge");
        assertThat(result.getEmail()).isEqualTo("connor@test.com");
    }

    @Test
    void updateUser_onlyEmail_updatesOnlyEmail() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserDto dto = UpdateUserDto.builder().email("new@test.com").build();

        User result = userService.updateUser(userId, dto);

        assertThat(result.getEmail()).isEqualTo("new@test.com");
        assertThat(result.getGivenName()).isEqualTo("Connor");
    }

    @Test
    void deleteUser_noMemberships_deletesUserOnly() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(membershipService.listAllUserMemberships(userId)).thenReturn(List.of());

        userService.deleteUser(userId);

        verify(membershipService, never()).deleteMembership(any(), any());
        verify(userRepository).delete(testUser);
    }

    @Test
    void deleteUser_multipleMemberships_deletesAll() {
        UUID workspace2Id = UUID.randomUUID();
        WorkspaceMembership m1 = WorkspaceMembership.builder()
                .user(testUser)
                .workspace(Workspace.builder().id(workspaceId).build())
                .build();
        WorkspaceMembership m2 = WorkspaceMembership.builder()
                .user(testUser)
                .workspace(Workspace.builder().id(workspace2Id).build())
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(membershipService.listAllUserMemberships(userId)).thenReturn(List.of(m1, m2));

        userService.deleteUser(userId);

        verify(membershipService).deleteMembership(userId, workspaceId);
        verify(membershipService).deleteMembership(userId, workspace2Id);
        verify(userRepository).delete(testUser);
    }
}
