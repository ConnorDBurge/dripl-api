package com.dripl.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceCrudIT extends BaseIntegrationTest {

    private String token;
    private Map<String, Object> user;

    @BeforeEach
    void setUp() {
        user = bootstrapUser("ws-user-%s@test.com".formatted(System.nanoTime()), "WS", "User");
        token = (String) user.get("token");
    }

    @Test
    void listWorkspaces_afterBootstrap_returnsDefaultWorkspace() {
        var response = restTemplate.exchange(
                "/api/v1/workspaces", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void provisionWorkspace_createsAndSwitches() {
        var response = restTemplate.exchange(
                "/api/v1/workspaces", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Second Workspace"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("name")).isEqualTo("Second Workspace");
        assertThat(response.getBody().get("token")).isNotNull();

        // Should now have 2 workspaces
        String newToken = (String) response.getBody().get("token");
        var list = restTemplate.exchange(
                "/api/v1/workspaces", HttpMethod.GET,
                new HttpEntity<>(authHeaders(newToken)),
                List.class);
        assertThat(list.getBody()).hasSize(2);
    }

    @Test
    void provisionWorkspace_duplicateName_returns409() {
        var response = restTemplate.exchange(
                "/api/v1/workspaces", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"WS's Workspace"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void provisionWorkspace_blankName_returns400() {
        var response = restTemplate.exchange(
                "/api/v1/workspaces", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":""}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void switchWorkspace_validWorkspace_returnsNewToken() {
        // Create a second workspace
        var provision = restTemplate.exchange(
                "/api/v1/workspaces", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Switch Target"}
                        """, authHeaders(token)),
                Map.class);
        String newToken = (String) provision.getBody().get("token");
        String newWorkspaceId = (String) provision.getBody().get("id");

        // Switch back to original
        String originalWorkspaceId = (String) user.get("lastWorkspaceId");
        var switchResponse = restTemplate.exchange(
                "/api/v1/workspaces/switch", HttpMethod.POST,
                new HttpEntity<>("""
                        {"workspaceId":"%s"}
                        """.formatted(originalWorkspaceId), authHeaders(newToken)),
                Map.class);

        assertThat(switchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(switchResponse.getBody().get("id")).isEqualTo(originalWorkspaceId);
        assertThat(switchResponse.getBody().get("token")).isNotNull();
    }

    @Test
    void getCurrentWorkspace_returnsWorkspace() {
        var response = restTemplate.exchange(
                "/api/v1/workspaces/current", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("WS's Workspace");
        assertThat(response.getBody().get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void updateCurrentWorkspace_validName_returns200() {
        var response = restTemplate.exchange(
                "/api/v1/workspaces/current", HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"name":"Renamed Workspace"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Renamed Workspace");
    }
}
