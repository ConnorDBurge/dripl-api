package com.dripl.integration;

import com.dripl.auth.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoleBasedAccessIT extends BaseIntegrationTest {

    @Autowired
    private JwtUtil jwtUtil;

    private String ownerToken;
    private String readOnlyToken;
    private Map<String, Object> owner;

    @BeforeEach
    void setUp() {
        owner = bootstrapUser("rbac-owner-%s@test.com".formatted(System.nanoTime()), "RBAC", "Owner");
        ownerToken = (String) owner.get("token");

        // Create a second user and add as READ-only member
        Map<String, Object> reader = bootstrapUser(
                "rbac-reader-%s@test.com".formatted(System.nanoTime()), "RBAC", "Reader");
        String readerId = (String) reader.get("id");
        String ownerWorkspaceId = (String) owner.get("lastWorkspaceId");

        // Add reader to owner's workspace
        restTemplate.exchange(
                "/api/v1/workspaces/current/members", HttpMethod.POST,
                new HttpEntity<>("""
                        {"userId":"%s","roles":["READ"]}
                        """.formatted(readerId), authHeaders(ownerToken)),
                Map.class);

        // Mint a token for the reader in the owner's workspace
        readOnlyToken = jwtUtil.generateToken(
                UUID.fromString(readerId),
                UUID.fromString(ownerWorkspaceId),
                "rbac-reader@test.com",
                List.of("READ"));
    }

    @Test
    void readOnly_canGetCurrentWorkspace() {
        var response = restTemplate.exchange(
                "/api/v1/workspaces/current", HttpMethod.GET,
                new HttpEntity<>(authHeaders(readOnlyToken)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void readOnly_canGetMembers() {
        var response = restTemplate.exchange(
                "/api/v1/workspaces/current/members", HttpMethod.GET,
                new HttpEntity<>(authHeaders(readOnlyToken)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void readOnly_cannotUpdateWorkspace() {
        var response = restTemplate.exchange(
                "/api/v1/workspaces/current", HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"name":"Hijacked"}
                        """, authHeaders(readOnlyToken)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void readOnly_cannotAddMembers() {
        var response = restTemplate.exchange(
                "/api/v1/workspaces/current/members", HttpMethod.POST,
                new HttpEntity<>("""
                        {"userId":"%s","roles":["READ"]}
                        """.formatted(UUID.randomUUID()), authHeaders(readOnlyToken)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void readOnly_cannotUpdateMembers() {
        String ownerId = (String) owner.get("id");

        var response = restTemplate.exchange(
                "/api/v1/workspaces/current/members/" + ownerId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"roles":["READ"]}
                        """, authHeaders(readOnlyToken)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void readOnly_cannotRemoveMembers() {
        String ownerId = (String) owner.get("id");

        var response = restTemplate.exchange(
                "/api/v1/workspaces/current/members/" + ownerId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(readOnlyToken)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void owner_canDoEverything() {
        // GET current
        assertThat(restTemplate.exchange(
                "/api/v1/workspaces/current", HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        // PATCH current
        assertThat(restTemplate.exchange(
                "/api/v1/workspaces/current", HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"name":"Owner Renamed"}
                        """, authHeaders(ownerToken)), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        // GET members
        assertThat(restTemplate.exchange(
                "/api/v1/workspaces/current/members", HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)), List.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
