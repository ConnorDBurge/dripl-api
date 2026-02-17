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

class MerchantCrudIT extends BaseIntegrationTest {

    @Autowired private TokenService tokenService;

    private String token;
    private UUID workspaceId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        String email = "merchant-user-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Merchant", "User");
        token = (String) bootstrap.get("token");
        workspaceId = UUID.fromString((String) bootstrap.get("lastWorkspaceId"));
        userId = UUID.fromString((String) bootstrap.get("id"));
    }

    @Test
    void createMerchant_withMinimalFields_returns201() {
        var response = restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Amazon"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("Amazon");
        assertThat(response.getBody().get("status")).isEqualTo("ACTIVE");
        assertThat(response.getBody().get("workspaceId")).isEqualTo(workspaceId.toString());
    }

    @Test
    void listMerchants_returnsAllWorkspaceMerchants() {
        // Create two merchants
        restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Walmart"}
                        """, authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Target"}
                        """, authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void getMerchant_byId_returns200() {
        var createResponse = restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Costco"}
                        """, authHeaders(token)),
                Map.class);
        String merchantId = (String) createResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/merchants/" + merchantId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Costco");
    }

    @Test
    void updateMerchant_partialUpdate_returns200() {
        var createResponse = restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Kroger"}
                        """, authHeaders(token)),
                Map.class);
        String merchantId = (String) createResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/merchants/" + merchantId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"name":"Kroger Supermarket"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Kroger Supermarket");
        assertThat(response.getBody().get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void updateMerchant_changeStatus_returns200() {
        var createResponse = restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Safeway"}
                        """, authHeaders(token)),
                Map.class);
        String merchantId = (String) createResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/merchants/" + merchantId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"status":"ARCHIVED"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("ARCHIVED");
        assertThat(response.getBody().get("name")).isEqualTo("Safeway");
    }

    @Test
    void deleteMerchant_returns204() {
        var createResponse = restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Whole Foods"}
                        """, authHeaders(token)),
                Map.class);
        String merchantId = (String) createResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/merchants/" + merchantId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify it's actually deleted
        var getResponse = restTemplate.exchange(
                "/api/v1/merchants/" + merchantId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void workspaceIsolation_cannotAccessOtherWorkspaceMerchants() {
        // Create a merchant in workspace 1
        var createResponse = restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trader Joe's"}
                        """, authHeaders(token)),
                Map.class);
        String merchantId = (String) createResponse.getBody().get("id");

        // Switch to workspace 2
        UUID workspace2Id = UUID.randomUUID();
        String token2 = tokenService.mintToken(userId, workspace2Id);

        // Try to access merchant from workspace 1
        var response = restTemplate.exchange(
                "/api/v1/merchants/" + merchantId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token2)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createMerchant_duplicateName_returns409() {
        restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Publix"}
                        """, authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Publix"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("detail")).toString()
                .contains("A merchant named 'Publix' already exists");
    }

    @Test
    void createMerchant_duplicateNameCaseInsensitive_returns409() {
        restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"HEB"}
                        """, authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"heb"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateMerchant_toDuplicateName_returns409() {
        restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Aldi"}
                        """, authHeaders(token)),
                Map.class);
        var createResponse = restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Lidl"}
                        """, authHeaders(token)),
                Map.class);
        String merchantId = (String) createResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/merchants/" + merchantId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"name":"Aldi"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("detail")).toString()
                .contains("A merchant named 'Aldi' already exists");
    }

    @Test
    void getMerchant_notFound_returns404() {
        UUID randomId = UUID.randomUUID();

        var response = restTemplate.exchange(
                "/api/v1/merchants/" + randomId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("detail")).isEqualTo("Merchant not found");
    }
}
