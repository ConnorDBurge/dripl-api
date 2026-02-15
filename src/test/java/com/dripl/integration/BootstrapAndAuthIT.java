package com.dripl.integration;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BootstrapAndAuthIT extends BaseIntegrationTest {

    @Test
    @Order(1)
    void bootstrap_newUser_returnsUserWithToken() {
        var response = restTemplate.postForEntity(
                "/api/v1/users/bootstrap",
                new HttpEntity<>("""
                        {"email":"it-user@test.com","givenName":"IT","familyName":"User"}
                        """, jsonHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("email", "token", "id", "lastWorkspaceId");
        assertThat(response.getBody().get("email")).isEqualTo("it-user@test.com");
        assertThat(response.getBody().get("token")).isNotNull();
    }

    @Test
    @Order(2)
    void bootstrap_existingUser_returnsIdempotent() {
        var first = bootstrapUser("idempotent@test.com", "First", "Call");
        var second = bootstrapUser("idempotent@test.com", "First", "Call");

        assertThat(first.get("id")).isEqualTo(second.get("id"));
        assertThat(second.get("token")).isNotNull();
    }

    @Test
    @Order(3)
    void bootstrap_missingEmail_returns400() {
        var response = restTemplate.postForEntity(
                "/api/v1/users/bootstrap",
                new HttpEntity<>("""
                        {"givenName":"No","familyName":"Email"}
                        """, jsonHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(4)
    void authenticatedEndpoint_withValidToken_returns200() {
        var bootstrap = bootstrapUser("auth-test@test.com", "Auth", "Test");
        String token = (String) bootstrap.get("token");

        var response = restTemplate.exchange(
                "/api/v1/users/self", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("email")).isEqualTo("auth-test@test.com");
    }

    @Test
    @Order(5)
    void authenticatedEndpoint_withNoToken_returns403() {
        var response = restTemplate.exchange(
                "/api/v1/users/self", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(6)
    void authenticatedEndpoint_withInvalidToken_returns403() {
        var response = restTemplate.exchange(
                "/api/v1/users/self", HttpMethod.GET,
                new HttpEntity<>(authHeaders("not.a.valid.token")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
