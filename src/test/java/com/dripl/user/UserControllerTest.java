package com.dripl.user;

import com.dripl.auth.config.SecurityConfig;
import com.dripl.auth.filter.JwtAuthFilter;
import com.dripl.auth.service.TokenService;
import com.dripl.common.config.WebMvcConfig;
import com.dripl.common.exception.ConflictException;
import com.dripl.common.exception.GlobalExceptionHandler;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.user.controller.UserController;
import com.dripl.user.entity.User;
import com.dripl.user.service.UserService;
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

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebMvcConfig.class, GlobalExceptionHandler.class, com.dripl.user.mapper.UserMapperImpl.class})
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserService userService;
    @MockitoBean private TokenService tokenService;

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
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void authenticate() {
        Claims claims = new DefaultClaims(Map.of(
                "sub", "connor@test.com",
                "user_id", userId.toString(),
                "workspace_id", workspaceId.toString(),
                "roles", List.of("OWNER", "WRITE", "DELETE", "READ")
        ));
        var auth = new UsernamePasswordAuthenticationToken(
                claims, null,
                List.of(new SimpleGrantedAuthority("OWNER"),
                        new SimpleGrantedAuthority("WRITE"),
                        new SimpleGrantedAuthority("READ")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // --- Bootstrap (public endpoint) ---

    @Test
    void bootstrap_validRequest_returns200() throws Exception {
        when(userService.bootstrapUser("connor@test.com", "Connor", "Burge"))
                .thenReturn(testUser);
        when(tokenService.mintToken(userId, workspaceId)).thenReturn("jwt-token");

        mockMvc.perform(post("/api/v1/users/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"connor@test.com","givenName":"Connor","familyName":"Burge"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("connor@test.com"))
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void bootstrap_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"givenName":"Connor","familyName":"Burge"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void bootstrap_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","givenName":"Connor","familyName":"Burge"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bootstrap_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","givenName":"Connor","familyName":"Burge"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bootstrap_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // --- GET /self ---

    @Test
    void getSelf_authenticated_returns200() throws Exception {
        authenticate();
        when(userService.getUser(userId)).thenReturn(testUser);

        mockMvc.perform(get("/api/v1/users/self"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("connor@test.com"))
                .andExpect(jsonPath("$.givenName").value("Connor"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    // --- PATCH /self ---

    @Test
    void updateSelf_validUpdate_returns200WithToken() throws Exception {
        authenticate();
        User updatedUser = User.builder()
                .id(userId).email("new@test.com").givenName("Connor").familyName("Burge")
                .isActive(true).lastWorkspaceId(workspaceId)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        when(userService.updateUser(eq(userId), any())).thenReturn(updatedUser);
        when(tokenService.mintToken(userId, workspaceId)).thenReturn("new-jwt");

        mockMvc.perform(patch("/api/v1/users/self")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@test.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@test.com"))
                .andExpect(jsonPath("$.token").value("new-jwt"));
    }

    @Test
    void updateSelf_emptyEmail_returns400() throws Exception {
        authenticate();

        mockMvc.perform(patch("/api/v1/users/self")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void updateSelf_invalidEmail_returns400() throws Exception {
        authenticate();

        mockMvc.perform(patch("/api/v1/users/self")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-valid"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSelf_duplicateEmail_returns409() throws Exception {
        authenticate();
        when(userService.updateUser(eq(userId), any()))
                .thenThrow(new ConflictException("Email already in use"));

        mockMvc.perform(patch("/api/v1/users/self")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"taken@test.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email already in use"));
    }

    // --- DELETE /self ---

    @Test
    void deleteSelf_authenticated_returns204() throws Exception {
        authenticate();
        doNothing().when(userService).deleteUser(userId);

        mockMvc.perform(delete("/api/v1/users/self"))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(userId);
    }

    @Test
    void deleteSelf_userNotFound_returns404() throws Exception {
        authenticate();
        doThrow(new ResourceNotFoundException("User not found")).when(userService).deleteUser(userId);

        mockMvc.perform(delete("/api/v1/users/self"))
                .andExpect(status().isNotFound());
    }
}
