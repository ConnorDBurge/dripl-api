package com.dripl.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserCrudIT extends BaseIntegrationTest {

    private String token;
    private Map<String, Object> user;

    @BeforeEach
    void setUp() {
        user = bootstrapUser("crud-user-%s@test.com".formatted(System.nanoTime()), "Crud", "User");
        token = (String) user.get("token");
    }

    @Test
    void getSelf_returnsCurrentUser() {
        var response = restTemplate.exchange(
                "/api/v1/users/self", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("id")).isEqualTo(user.get("id"));
        assertThat(response.getBody().get("givenName")).isEqualTo("Crud");
    }

    @Test
    void patchSelf_updatesName_returnsNewToken() {
        var response = restTemplate.exchange(
                "/api/v1/users/self", HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"givenName":"Updated"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("givenName")).isEqualTo("Updated");
        assertThat(response.getBody().get("token")).isNotNull();

        // Verify new token works
        String newToken = (String) response.getBody().get("token");
        var verify = restTemplate.exchange(
                "/api/v1/users/self", HttpMethod.GET,
                new HttpEntity<>(authHeaders(newToken)),
                Map.class);
        assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verify.getBody().get("givenName")).isEqualTo("Updated");
    }

    @Test
    void patchSelf_duplicateEmail_returns409() {
        bootstrapUser("existing@test.com", "Existing", "User");

        var response = restTemplate.exchange(
                "/api/v1/users/self", HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"email":"existing@test.com"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void patchSelf_emptyEmail_returns400() {
        var response = restTemplate.exchange(
                "/api/v1/users/self", HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"email":""}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteSelf_removesUser() {
        var response = restTemplate.exchange(
                "/api/v1/users/self", HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Token should still parse but user shouldn't exist
        var verify = restTemplate.exchange(
                "/api/v1/users/self", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
