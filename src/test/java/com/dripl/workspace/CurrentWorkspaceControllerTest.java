package com.dripl.workspace;

import com.dripl.auth.config.SecurityConfig;
import com.dripl.auth.filter.JwtAuthFilter;
import com.dripl.common.config.WebMvcConfig;
import com.dripl.common.exception.GlobalExceptionHandler;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.workspace.controller.CurrentWorkspaceController;
import com.dripl.workspace.entity.Workspace;
import com.dripl.workspace.enums.WorkspaceStatus;
import com.dripl.workspace.membership.entity.WorkspaceMembership;
import com.dripl.workspace.membership.enums.MembershipStatus;
import com.dripl.workspace.membership.enums.Role;
import com.dripl.workspace.membership.service.MembershipService;
import com.dripl.workspace.service.WorkspaceService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = CurrentWorkspaceController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebMvcConfig.class, GlobalExceptionHandler.class})
class CurrentWorkspaceControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WorkspaceService workspaceService;
    @MockitoBean private MembershipService membershipService;

    private UUID userId;
    private UUID workspaceId;
    private Workspace testWorkspace;
    private WorkspaceMembership testMembership;
    private com.dripl.user.entity.User testUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        testUser = com.dripl.user.entity.User.builder()
                .id(userId).email("connor@test.com")
                .givenName("Connor").familyName("Burge")
                .isActive(true).build();
        testWorkspace = Workspace.builder()
                .id(workspaceId).name("Test Workspace")
                .status(WorkspaceStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        testMembership = WorkspaceMembership.builder()
                .user(testUser).workspace(testWorkspace)
                .roles(Set.of(Role.OWNER, Role.WRITE, Role.READ))
                .status(MembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.now()).build();
    }

    private void authenticate(String... roles) {
        List<String> roleList = roles.length > 0 ? List.of(roles) : List.of("OWNER", "WRITE", "READ");
        Claims claims = new DefaultClaims(Map.of(
                "sub", "connor@test.com",
                "user_id", userId.toString(),
                "workspace_id", workspaceId.toString(),
                "roles", roleList
        ));
        var auth = new UsernamePasswordAuthenticationToken(
                claims, null,
                roleList.stream().map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // --- GET /workspaces/current ---

    @Test
    void getCurrentWorkspace_authenticated_returns200() throws Exception {
        authenticate();
        when(workspaceService.getWorkspace(workspaceId)).thenReturn(testWorkspace);

        mockMvc.perform(get("/api/v1/workspaces/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Workspace"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // --- PATCH /workspaces/current ---

    @Test
    void updateCurrentWorkspace_returns200() throws Exception {
        authenticate();
        when(workspaceService.updateWorkspace(eq(workspaceId), eq(userId), any())).thenReturn(testWorkspace);

        mockMvc.perform(patch("/api/v1/workspaces/current")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Workspace"));
    }

    // --- GET /workspaces/current/members ---

    @Test
    void getMembers_authenticated_returns200() throws Exception {
        authenticate();
        when(workspaceService.listAllMembers(workspaceId)).thenReturn(List.of(testMembership));

        mockMvc.perform(get("/api/v1/workspaces/current/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$[0].workspaceId").value(workspaceId.toString()));
    }

    // --- GET /workspaces/current/members/{userId} ---

    @Test
    void getMember_exists_returns200() throws Exception {
        authenticate();
        when(membershipService.findMembership(userId, workspaceId))
                .thenReturn(Optional.of(testMembership));

        mockMvc.perform(get("/api/v1/workspaces/current/members/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void getMember_notFound_returns404() throws Exception {
        authenticate();
        UUID unknownUserId = UUID.randomUUID();
        when(membershipService.findMembership(unknownUserId, workspaceId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/workspaces/current/members/" + unknownUserId))
                .andExpect(status().isNotFound());
    }

    // --- POST /workspaces/current/members ---

    @Test
    void addMember_returns201() throws Exception {
        authenticate();
        UUID newUserId = UUID.randomUUID();
        com.dripl.user.entity.User newUser = com.dripl.user.entity.User.builder()
                .id(newUserId).email("new@test.com").givenName("New").familyName("User")
                .isActive(true).build();
        WorkspaceMembership newMembership = WorkspaceMembership.builder()
                .user(newUser).workspace(testWorkspace)
                .roles(Set.of(Role.READ)).status(MembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.now()).build();

        when(membershipService.createMembership(eq(newUserId), eq(workspaceId), eq(Set.of(Role.READ))))
                .thenReturn(newMembership);

        mockMvc.perform(post("/api/v1/workspaces/current/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","roles":["READ"]}
                                """.formatted(newUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(newUserId.toString()));
    }

    @Test
    void addMember_missingUserId_returns400() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/v1/workspaces/current/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["READ"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addMember_emptyRoles_returns400() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/v1/workspaces/current/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","roles":[]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    // --- PATCH /workspaces/current/members/{userId} ---

    @Test
    void updateMember_returns200() throws Exception {
        authenticate();
        testMembership.setRoles(Set.of(Role.READ));
        when(membershipService.updateMembership(eq(userId), eq(workspaceId), any()))
                .thenReturn(testMembership);

        mockMvc.perform(patch("/api/v1/workspaces/current/members/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["READ"]}
                                """))
                .andExpect(status().isOk());
    }

    // --- DELETE /workspaces/current/members/{userId} ---

    @Test
    void removeMember_returns204() throws Exception {
        authenticate();
        doNothing().when(membershipService).deleteMembership(userId, workspaceId);

        mockMvc.perform(delete("/api/v1/workspaces/current/members/" + userId))
                .andExpect(status().isNoContent());

        verify(membershipService).deleteMembership(userId, workspaceId);
    }

    @Test
    void removeMember_notFound_returns404() throws Exception {
        authenticate();
        doThrow(new ResourceNotFoundException("Membership not found"))
                .when(membershipService).deleteMembership(userId, workspaceId);

        mockMvc.perform(delete("/api/v1/workspaces/current/members/" + userId))
                .andExpect(status().isNotFound());
    }
}
