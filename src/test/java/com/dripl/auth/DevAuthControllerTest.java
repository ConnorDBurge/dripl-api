package com.dripl.auth;

import com.dripl.auth.controller.DevAuthController;
import com.dripl.auth.dto.AuthResponse;
import com.dripl.auth.service.TokenService;
import com.dripl.user.entity.User;
import com.dripl.user.service.UserService;
import com.dripl.workspace.membership.entity.WorkspaceMembership;
import com.dripl.workspace.membership.enums.Role;
import com.dripl.workspace.membership.service.MembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevAuthControllerTest {

    @Mock private UserService userService;
    @Mock private TokenService tokenService;
    @Mock private MembershipService membershipService;

    @InjectMocks private DevAuthController devAuthController;

    private UUID userId;
    private UUID workspaceId;
    private User testUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .email("dev@test.com")
                .givenName("Dev")
                .familyName("User")
                .isActive(true)
                .lastWorkspaceId(workspaceId)
                .build();
    }

    @Test
    void devToken_returnsTokenWithRoles() {
        when(userService.bootstrapUser("dev@test.com", null, null)).thenReturn(testUser);
        when(tokenService.mintToken(userId, workspaceId)).thenReturn("dev-jwt");

        WorkspaceMembership membership = WorkspaceMembership.builder()
                .roles(Set.of(Role.OWNER, Role.WRITE, Role.DELETE, Role.READ))
                .build();
        when(membershipService.findMembership(userId, workspaceId))
                .thenReturn(Optional.of(membership));

        ResponseEntity<AuthResponse> response = devAuthController.devToken("dev@test.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getToken()).isEqualTo("dev-jwt");
        assertThat(body.getUserId()).isEqualTo(userId);
        assertThat(body.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(body.getEmail()).isEqualTo("dev@test.com");
        assertThat(body.getGivenName()).isEqualTo("Dev");
        assertThat(body.getFamilyName()).isEqualTo("User");
        assertThat(body.getRoles()).containsExactlyInAnyOrder("OWNER", "WRITE", "DELETE", "READ");
    }

    @Test
    void devToken_noMembership_returnsEmptyRoles() {
        when(userService.bootstrapUser("new@test.com", null, null)).thenReturn(testUser);
        when(tokenService.mintToken(userId, workspaceId)).thenReturn("dev-jwt");
        when(membershipService.findMembership(userId, workspaceId))
                .thenReturn(Optional.empty());

        ResponseEntity<AuthResponse> response = devAuthController.devToken("new@test.com");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRoles()).isEmpty();
    }

    @Test
    void devToken_readOnlyMembership_returnsReadRole() {
        when(userService.bootstrapUser("viewer@test.com", null, null)).thenReturn(testUser);
        when(tokenService.mintToken(userId, workspaceId)).thenReturn("dev-jwt");

        WorkspaceMembership membership = WorkspaceMembership.builder()
                .roles(Set.of(Role.READ))
                .build();
        when(membershipService.findMembership(userId, workspaceId))
                .thenReturn(Optional.of(membership));

        ResponseEntity<AuthResponse> response = devAuthController.devToken("viewer@test.com");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRoles()).containsExactly("READ");
    }

    @Test
    void devToken_passesNullGivenNameAndFamilyName() {
        when(userService.bootstrapUser("any@test.com", null, null)).thenReturn(testUser);
        when(tokenService.mintToken(userId, workspaceId)).thenReturn("dev-jwt");
        when(membershipService.findMembership(userId, workspaceId))
                .thenReturn(Optional.empty());

        ResponseEntity<AuthResponse> response = devAuthController.devToken("any@test.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // givenName/familyName come from the bootstrapped user, not the request
        assertThat(response.getBody().getGivenName()).isEqualTo("Dev");
        assertThat(response.getBody().getFamilyName()).isEqualTo("User");
    }
}
