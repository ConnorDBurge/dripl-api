package com.dripl.workspace;

import com.dripl.auth.config.SecurityConfig;
import com.dripl.auth.filter.JwtAuthFilter;
import com.dripl.auth.service.TokenService;
import com.dripl.common.config.WebMvcConfig;
import com.dripl.common.exception.AccessDeniedException;
import com.dripl.common.exception.ConflictException;
import com.dripl.common.exception.GlobalExceptionHandler;
import com.dripl.workspace.controller.WorkspaceController;
import com.dripl.workspace.entity.Workspace;
import com.dripl.workspace.enums.WorkspaceStatus;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = WorkspaceController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebMvcConfig.class, GlobalExceptionHandler.class})
class WorkspaceControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WorkspaceService workspaceService;
    @MockitoBean private TokenService tokenService;

    private UUID userId;
    private UUID workspaceId;
    private Workspace testWorkspace;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        testWorkspace = Workspace.builder()
                .id(workspaceId)
                .name("Test Workspace")
                .status(WorkspaceStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void authenticate() {
        Claims claims = new DefaultClaims(Map.of(
                "sub", "connor@test.com",
                "user_id", userId.toString(),
                "workspace_id", workspaceId.toString(),
                "roles", List.of("OWNER", "WRITE", "READ")
        ));
        var auth = new UsernamePasswordAuthenticationToken(
                claims, null,
                List.of(new SimpleGrantedAuthority("OWNER"),
                        new SimpleGrantedAuthority("WRITE"),
                        new SimpleGrantedAuthority("READ")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // --- GET /workspaces ---

    @Test
    void getAllWorkspaces_authenticated_returns200() throws Exception {
        authenticate();
        when(workspaceService.listAllByUserId(userId)).thenReturn(List.of(testWorkspace));

        mockMvc.perform(get("/api/v1/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Workspace"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void getAllWorkspaces_empty_returnsEmptyArray() throws Exception {
        authenticate();
        when(workspaceService.listAllByUserId(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- POST /workspaces ---

    @Test
    void provisionWorkspace_valid_returns201WithToken() throws Exception {
        authenticate();
        when(workspaceService.provisionWorkspace(eq(userId), any())).thenReturn(testWorkspace);
        when(workspaceService.switchWorkspace(userId, workspaceId)).thenReturn(testWorkspace);
        when(tokenService.mintToken(userId, workspaceId)).thenReturn("new-token");

        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test Workspace"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Workspace"))
                .andExpect(jsonPath("$.token").value("new-token"));
    }

    @Test
    void provisionWorkspace_blankName_returns400() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void provisionWorkspace_missingName_returns400() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void provisionWorkspace_duplicateName_returns409() throws Exception {
        authenticate();
        when(workspaceService.provisionWorkspace(eq(userId), any()))
                .thenThrow(new ConflictException("You already have a workspace named 'Test Workspace'"));

        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test Workspace"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You already have a workspace named 'Test Workspace'"));
    }

    // --- POST /workspaces/switch ---

    @Test
    void switchWorkspace_valid_returns200WithToken() throws Exception {
        authenticate();
        UUID targetId = UUID.randomUUID();
        Workspace target = Workspace.builder()
                .id(targetId).name("Other").status(WorkspaceStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        when(workspaceService.switchWorkspace(userId, targetId)).thenReturn(target);
        when(tokenService.mintToken(userId, targetId)).thenReturn("switched-token");

        mockMvc.perform(post("/api/v1/workspaces/switch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId":"%s"}
                                """.formatted(targetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Other"))
                .andExpect(jsonPath("$.token").value("switched-token"));
    }

    @Test
    void switchWorkspace_missingWorkspaceId_returns400() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/v1/workspaces/switch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void switchWorkspace_noAccess_returns403() throws Exception {
        authenticate();
        UUID targetId = UUID.randomUUID();
        when(workspaceService.switchWorkspace(userId, targetId))
                .thenThrow(new AccessDeniedException("User does not have access to workspace"));

        mockMvc.perform(post("/api/v1/workspaces/switch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId":"%s"}
                                """.formatted(targetId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("User does not have access to workspace"));
    }
}
