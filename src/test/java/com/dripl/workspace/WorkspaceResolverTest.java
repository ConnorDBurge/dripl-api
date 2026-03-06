package com.dripl.workspace;

import com.dripl.auth.service.TokenService;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.workspace.dto.CreateWorkspaceInput;
import com.dripl.workspace.dto.SwitchWorkspaceInput;
import com.dripl.workspace.dto.UpdateWorkspaceInput;
import com.dripl.workspace.dto.WorkspaceAuthResponse;
import com.dripl.workspace.dto.WorkspaceResponse;
import com.dripl.workspace.entity.Workspace;
import com.dripl.workspace.enums.WorkspaceStatus;
import com.dripl.workspace.mapper.WorkspaceMapper;
import com.dripl.workspace.membership.dto.CreateMembershipInput;
import com.dripl.workspace.membership.dto.UpdateMembershipInput;
import com.dripl.workspace.membership.dto.WorkspaceMembershipResponse;
import com.dripl.workspace.membership.entity.WorkspaceMembership;
import com.dripl.workspace.membership.enums.MembershipStatus;
import com.dripl.workspace.membership.enums.Role;
import com.dripl.workspace.membership.mapper.MembershipMapper;
import com.dripl.workspace.membership.service.MembershipService;
import com.dripl.workspace.resolver.WorkspaceResolver;
import com.dripl.workspace.service.WorkspaceService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceResolverTest {

    @Mock private WorkspaceService workspaceService;
    @Mock private MembershipService membershipService;
    @Mock private TokenService tokenService;
    @Spy private WorkspaceMapper workspaceMapper = Mappers.getMapper(WorkspaceMapper.class);
    @Spy private MembershipMapper membershipMapper = Mappers.getMapper(MembershipMapper.class);
    @InjectMocks private WorkspaceResolver workspaceResolver;

    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();

    @BeforeEach
    void setUpSecurityContext() {
        Claims claims = mock(Claims.class);
        lenient().when(claims.get("user_id", String.class)).thenReturn(userId.toString());
        lenient().when(claims.get("workspace_id", String.class)).thenReturn(workspaceId.toString());
        var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Workspace buildWorkspace(String name) {
        return Workspace.builder()
                .id(workspaceId)
                .name(name)
                .status(WorkspaceStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("test@test.com")
                .build();
    }

    private com.dripl.user.entity.User buildUser() {
        return com.dripl.user.entity.User.builder()
                .id(userId)
                .email("test@test.com")
                .givenName("Test")
                .familyName("User")
                .isActive(true)
                .build();
    }

    private WorkspaceMembership buildMembership(Set<Role> roles) {
        return WorkspaceMembership.builder()
                .user(buildUser())
                .workspace(buildWorkspace("Test Workspace"))
                .roles(roles)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void workspaces_returnsList() {
        when(workspaceService.listAllByUserId(userId))
                .thenReturn(List.of(buildWorkspace("Workspace 1")));

        List<WorkspaceResponse> result = workspaceResolver.workspaces();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Workspace 1");
    }

    @Test
    void workspaces_emptyList() {
        when(workspaceService.listAllByUserId(userId)).thenReturn(List.of());

        List<WorkspaceResponse> result = workspaceResolver.workspaces();

        assertThat(result).isEmpty();
    }

    @Test
    void currentWorkspace_returnsWorkspace() {
        when(workspaceService.getWorkspace(workspaceId)).thenReturn(buildWorkspace("My Workspace"));

        WorkspaceResponse result = workspaceResolver.currentWorkspace();

        assertThat(result.getName()).isEqualTo("My Workspace");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void provisionWorkspace_createsAndReturnsWithToken() {
        Workspace workspace = buildWorkspace("New Workspace");
        when(workspaceService.provisionWorkspace(eq(userId), any(CreateWorkspaceInput.class)))
                .thenReturn(workspace);
        when(workspaceService.switchWorkspace(userId, workspaceId)).thenReturn(workspace);
        when(tokenService.mintToken(userId, workspaceId)).thenReturn("new-token");

        CreateWorkspaceInput input = CreateWorkspaceInput.builder().name("New Workspace").build();
        WorkspaceAuthResponse result = workspaceResolver.provisionWorkspace(input);

        assertThat(result.getName()).isEqualTo("New Workspace");
        assertThat(result.getToken()).isEqualTo("new-token");
    }

    @Test
    void switchWorkspace_switchesAndReturnsWithToken() {
        UUID targetId = UUID.randomUUID();
        Workspace target = Workspace.builder()
                .id(targetId).name("Other Workspace").status(WorkspaceStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .createdBy("test@test.com").build();

        when(workspaceService.switchWorkspace(userId, targetId)).thenReturn(target);
        when(tokenService.mintToken(userId, targetId)).thenReturn("switched-token");

        SwitchWorkspaceInput input = new SwitchWorkspaceInput();
        input.setWorkspaceId(targetId);
        WorkspaceAuthResponse result = workspaceResolver.switchWorkspace(input);

        assertThat(result.getName()).isEqualTo("Other Workspace");
        assertThat(result.getToken()).isEqualTo("switched-token");
    }

    @Test
    void updateCurrentWorkspace_updatesName() {
        Workspace updated = buildWorkspace("Renamed");
        when(workspaceService.updateWorkspace(eq(workspaceId), eq(userId), any(UpdateWorkspaceInput.class)))
                .thenReturn(updated);

        UpdateWorkspaceInput input = UpdateWorkspaceInput.builder().name("Renamed").build();
        WorkspaceResponse result = workspaceResolver.updateCurrentWorkspace(input);

        assertThat(result.getName()).isEqualTo("Renamed");
    }

    @Test
    void workspaceMembers_returnsList() {
        WorkspaceMembership membership = buildMembership(Set.of(Role.OWNER, Role.READ));
        when(workspaceService.listAllMembers(workspaceId)).thenReturn(List.of(membership));

        List<WorkspaceMembershipResponse> result = workspaceResolver.workspaceMembers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("test@test.com");
    }

    @Test
    void workspaceMember_found_returnsMember() {
        WorkspaceMembership membership = buildMembership(Set.of(Role.READ));
        when(membershipService.findMembership(userId, workspaceId))
                .thenReturn(Optional.of(membership));

        WorkspaceMembershipResponse result = workspaceResolver.workspaceMember(userId);

        assertThat(result.getUserId()).isEqualTo(userId);
    }

    @Test
    void workspaceMember_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(membershipService.findMembership(unknownId, workspaceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceResolver.workspaceMember(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Member not found");
    }

    @Test
    void addWorkspaceMember_createsMember() {
        UUID newUserId = UUID.randomUUID();
        com.dripl.user.entity.User newUser = com.dripl.user.entity.User.builder()
                .id(newUserId).email("new@test.com").givenName("New").familyName("User")
                .isActive(true).build();
        WorkspaceMembership membership = WorkspaceMembership.builder()
                .user(newUser).workspace(buildWorkspace("Test Workspace"))
                .roles(Set.of(Role.READ)).status(MembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.now()).build();

        when(membershipService.createMembership(eq(newUserId), eq(workspaceId), eq(Set.of(Role.READ))))
                .thenReturn(membership);

        CreateMembershipInput input = CreateMembershipInput.builder()
                .userId(newUserId).roles(Set.of(Role.READ)).build();
        WorkspaceMembershipResponse result = workspaceResolver.addWorkspaceMember(input);

        assertThat(result.getUserId()).isEqualTo(newUserId);
        assertThat(result.getEmail()).isEqualTo("new@test.com");
    }

    @Test
    void updateWorkspaceMember_updatesRoles() {
        WorkspaceMembership membership = buildMembership(Set.of(Role.READ, Role.WRITE));
        when(membershipService.updateMembership(eq(userId), eq(workspaceId), any(UpdateMembershipInput.class)))
                .thenReturn(membership);

        UpdateMembershipInput input = UpdateMembershipInput.builder()
                .roles(Set.of(Role.READ, Role.WRITE)).build();
        WorkspaceMembershipResponse result = workspaceResolver.updateWorkspaceMember(userId, input);

        assertThat(result.getRoles()).contains(Role.READ, Role.WRITE);
    }

    @Test
    void removeWorkspaceMember_returnsTrue() {
        doNothing().when(membershipService).deleteMembership(userId, workspaceId);

        boolean result = workspaceResolver.removeWorkspaceMember(userId);

        assertThat(result).isTrue();
        verify(membershipService).deleteMembership(userId, workspaceId);
    }
}
