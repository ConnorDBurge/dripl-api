package com.dripl.auth;

import com.dripl.auth.config.SecurityConfig;
import com.dripl.auth.controller.AuthController;
import com.dripl.auth.filter.JwtAuthFilter;
import com.dripl.auth.service.GoogleOAuthService;
import com.dripl.auth.service.TokenService;
import com.dripl.common.config.WebMvcConfig;
import com.dripl.common.exception.GlobalExceptionHandler;
import com.dripl.user.entity.User;
import com.dripl.user.service.UserService;
import com.dripl.workspace.membership.entity.WorkspaceMembership;
import com.dripl.workspace.membership.enums.Role;
import com.dripl.workspace.membership.service.MembershipService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebMvcConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GoogleOAuthService googleOAuthService;
    @MockitoBean private UserService userService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private MembershipService membershipService;

    private UUID userId;
    private UUID workspaceId;
    private User testUser;

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
                .build();
    }

    @Test
    void googleLogin_validToken_returns200WithRoles() throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("connor@test.com");
        payload.set("given_name", "Connor");
        payload.set("family_name", "Burge");

        when(googleOAuthService.verifyIdToken("valid-id-token")).thenReturn(payload);
        when(userService.bootstrapUser("connor@test.com", "Connor", "Burge")).thenReturn(testUser);
        when(tokenService.mintToken(userId, workspaceId)).thenReturn("jwt-token");

        WorkspaceMembership membership = WorkspaceMembership.builder()
                .roles(Set.of(Role.OWNER, Role.WRITE, Role.DELETE, Role.READ))
                .build();
        when(membershipService.findMembership(userId, workspaceId))
                .thenReturn(Optional.of(membership));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idToken":"valid-id-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.email").value("connor@test.com"))
                .andExpect(jsonPath("$.givenName").value("Connor"))
                .andExpect(jsonPath("$.familyName").value("Burge"))
                .andExpect(jsonPath("$.roles", hasSize(4)))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("OWNER", "WRITE", "DELETE", "READ")));
    }

    @Test
    void googleLogin_validToken_noMembership_returnsEmptyRoles() throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("new@test.com");
        payload.set("given_name", "New");
        payload.set("family_name", "User");

        when(googleOAuthService.verifyIdToken("new-token")).thenReturn(payload);
        when(userService.bootstrapUser("new@test.com", "New", "User")).thenReturn(testUser);
        when(tokenService.mintToken(userId, workspaceId)).thenReturn("jwt-token");
        when(membershipService.findMembership(userId, workspaceId))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idToken":"new-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasSize(0)));
    }

    @Test
    void googleLogin_invalidToken_returns401() throws Exception {
        when(googleOAuthService.verifyIdToken("bad-token")).thenReturn(null);

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idToken":"bad-token"}
                                """))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).bootstrapUser(any(), any(), any());
    }

    @Test
    void googleLogin_missingIdToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void googleLogin_blankIdToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idToken":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void googleLogin_readOnlyMembership_returnsReadRole() throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("viewer@test.com");
        payload.set("given_name", "View");
        payload.set("family_name", "Only");

        when(googleOAuthService.verifyIdToken("viewer-token")).thenReturn(payload);
        when(userService.bootstrapUser("viewer@test.com", "View", "Only")).thenReturn(testUser);
        when(tokenService.mintToken(userId, workspaceId)).thenReturn("jwt-token");

        WorkspaceMembership membership = WorkspaceMembership.builder()
                .roles(Set.of(Role.READ))
                .build();
        when(membershipService.findMembership(userId, workspaceId))
                .thenReturn(Optional.of(membership));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idToken":"viewer-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasSize(1)))
                .andExpect(jsonPath("$.roles[0]").value("READ"));
    }
}
