package com.dripl.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemberManagementIT extends BaseIntegrationTest {

    private String ownerToken;
    private Map<String, Object> owner;
    private Map<String, Object> member;

    @BeforeEach
    void setUp() {
        owner = bootstrapUser("owner-%s@test.com".formatted(System.nanoTime()), "Owner", "User");
        ownerToken = (String) owner.get("token");

        // Create a second user to add as member
        member = bootstrapUser("member-%s@test.com".formatted(System.nanoTime()), "Member", "User");
    }

    @Test
    void listMembers_afterBootstrap_returnsOwner() {
        var response = restTemplate.exchange(
                "/api/v1/workspaces/current/members", HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void addMember_returnsNewMembership() {
        String memberId = (String) member.get("id");

        var response = restTemplate.exchange(
                "/api/v1/workspaces/current/members", HttpMethod.POST,
                new HttpEntity<>("""
                        {"userId":"%s","roles":["READ"]}
                        """.formatted(memberId), authHeaders(ownerToken)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("userId")).isEqualTo(memberId);

        // Verify member appears in list
        var list = restTemplate.exchange(
                "/api/v1/workspaces/current/members", HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)),
                List.class);
        assertThat(list.getBody()).hasSize(2);
    }

    @Test
    void getMemberById_returnsMembership() {
        String ownerId = (String) owner.get("id");

        var response = restTemplate.exchange(
                "/api/v1/workspaces/current/members/" + ownerId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("userId")).isEqualTo(ownerId);
    }

    @Test
    void getMemberById_nonExistent_returns404() {
        var response = restTemplate.exchange(
                "/api/v1/workspaces/current/members/00000000-0000-0000-0000-000000000000",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateMember_changesRoles() {
        // Add member first
        String memberId = (String) member.get("id");
        restTemplate.exchange(
                "/api/v1/workspaces/current/members", HttpMethod.POST,
                new HttpEntity<>("""
                        {"userId":"%s","roles":["READ"]}
                        """.formatted(memberId), authHeaders(ownerToken)),
                Map.class);

        // Update their roles
        var response = restTemplate.exchange(
                "/api/v1/workspaces/current/members/" + memberId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"roles":["READ","WRITE"]}
                        """, authHeaders(ownerToken)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) response.getBody().get("roles");
        assertThat(roles).contains("READ", "WRITE");
    }

    @Test
    void removeMember_returns204() {
        // Add member first
        String memberId = (String) member.get("id");
        restTemplate.exchange(
                "/api/v1/workspaces/current/members", HttpMethod.POST,
                new HttpEntity<>("""
                        {"userId":"%s","roles":["READ"]}
                        """.formatted(memberId), authHeaders(ownerToken)),
                Map.class);

        // Remove them
        var response = restTemplate.exchange(
                "/api/v1/workspaces/current/members/" + memberId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ownerToken)),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify removed from list
        var list = restTemplate.exchange(
                "/api/v1/workspaces/current/members", HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)),
                List.class);
        assertThat(list.getBody()).hasSize(1);
    }
}
