package com.dripl.integration;

import com.dripl.auth.service.TokenService;
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

class TagCrudIT extends BaseIntegrationTest {

    @Autowired private TokenService tokenService;

    private String token;
    private UUID workspaceId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        String email = "tag-user-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Tag", "User");
        token = (String) bootstrap.get("token");
        workspaceId = UUID.fromString((String) bootstrap.get("lastWorkspaceId"));
        userId = UUID.fromString((String) bootstrap.get("id"));
    }

    @Test
    void createTag_withNameOnly_returns201() {
        var response = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"groceries"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("groceries");
        assertThat(response.getBody().get("status")).isEqualTo("ACTIVE");
        assertThat(response.getBody().get("description")).isNull();
        assertThat(response.getBody().get("workspaceId")).isEqualTo(workspaceId.toString());
    }

    @Test
    void createTag_withDescription_returns201() {
        var response = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"travel","description":"Trips and vacations"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("name")).isEqualTo("travel");
        assertThat(response.getBody().get("description")).isEqualTo("Trips and vacations");
    }

    @Test
    void listTags_returnsAllWorkspaceTags() {
        restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"food"}
                        """, authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"entertainment"}
                        """, authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void getTag_byId_returns200() {
        var createResponse = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"coffee"}
                        """, authHeaders(token)),
                Map.class);
        String tagId = (String) createResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/tags/" + tagId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("coffee");
    }

    @Test
    void updateTag_name_returns200() {
        var createResponse = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"lunch"}
                        """, authHeaders(token)),
                Map.class);
        String tagId = (String) createResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/tags/" + tagId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"name":"lunch-out"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("lunch-out");
        assertThat(response.getBody().get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void updateTag_description_returns200() {
        var createResponse = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"subscriptions"}
                        """, authHeaders(token)),
                Map.class);
        String tagId = (String) createResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/tags/" + tagId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"description":"Monthly recurring charges"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("description")).isEqualTo("Monthly recurring charges");
        assertThat(response.getBody().get("name")).isEqualTo("subscriptions");
    }

    @Test
    void updateTag_status_returns200() {
        var createResponse = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"old-tag"}
                        """, authHeaders(token)),
                Map.class);
        String tagId = (String) createResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/tags/" + tagId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"status":"ARCHIVED"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("ARCHIVED");
        assertThat(response.getBody().get("name")).isEqualTo("old-tag");
    }

    @Test
    void deleteTag_returns204() {
        var createResponse = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"delete-me"}
                        """, authHeaders(token)),
                Map.class);
        String tagId = (String) createResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/tags/" + tagId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var getResponse = restTemplate.exchange(
                "/api/v1/tags/" + tagId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void workspaceIsolation_cannotAccessOtherWorkspaceTags() {
        var createResponse = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"isolated-tag"}
                        """, authHeaders(token)),
                Map.class);
        String tagId = (String) createResponse.getBody().get("id");

        UUID workspace2Id = UUID.randomUUID();
        String token2 = tokenService.mintToken(userId, workspace2Id);

        var response = restTemplate.exchange(
                "/api/v1/tags/" + tagId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token2)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createTag_duplicateName_returns409() {
        restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"duplicate"}
                        """, authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"duplicate"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createTag_duplicateNameCaseInsensitive_returns409() {
        restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries"}
                        """, authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"groceries"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateTag_toDuplicateName_returns409() {
        restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"existing"}
                        """, authHeaders(token)),
                Map.class);
        var createResponse = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"other"}
                        """, authHeaders(token)),
                Map.class);
        String tagId = (String) createResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/tags/" + tagId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"name":"existing"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getTag_notFound_returns404() {
        UUID randomId = UUID.randomUUID();

        var response = restTemplate.exchange(
                "/api/v1/tags/" + randomId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("detail")).isEqualTo("Tag not found");
    }
}
