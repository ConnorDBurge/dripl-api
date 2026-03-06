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

class MerchantGraphQLIT extends BaseIntegrationTest {

    @Autowired private TokenService tokenService;

    private String token;
    private UUID workspaceId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        String email = "gql-merchant-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Merchant", "User");
        token = (String) bootstrap.get("token");
        workspaceId = UUID.fromString((String) bootstrap.get("lastWorkspaceId"));
        userId = UUID.fromString((String) bootstrap.get("id"));
    }

    private Map<String, Object> graphql(String query) {
        return graphql(query, (Map<String, Object>) null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> graphql(String query, Map<String, Object> variables) {
        Map<String, Object> body = variables != null
                ? Map.of("query", query, "variables", variables)
                : Map.of("query", query);
        var response = restTemplate.exchange(
                "/graphql", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> response) {
        return (Map<String, Object>) response.get("data");
    }

    @Test
    void createMerchant_returnsCreatedMerchant() {
        var result = graphql("""
                mutation {
                    createMerchant(input: { name: "Amazon" }) {
                        id name status workspaceId createdAt createdBy
                    }
                }
                """);

        @SuppressWarnings("unchecked")
        var merchant = (Map<String, Object>) data(result).get("createMerchant");
        assertThat(merchant.get("name")).isEqualTo("Amazon");
        assertThat(merchant.get("status")).isEqualTo("ACTIVE");
        assertThat(merchant.get("workspaceId")).isEqualTo(workspaceId.toString());
        assertThat(merchant.get("createdAt")).isNotNull();
        assertThat(merchant.get("createdBy")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listMerchants_returnsAll() {
        graphql("""
                mutation { createMerchant(input: { name: "Walmart" }) { id } }
                """);
        graphql("""
                mutation { createMerchant(input: { name: "Target" }) { id } }
                """);

        var result = graphql("{ merchants { id name } }");
        var merchants = (List<Map<String, Object>>) data(result).get("merchants");
        assertThat(merchants).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void getMerchant_byId_returnsMerchant() {
        var createResult = graphql("""
                mutation { createMerchant(input: { name: "Costco" }) { id } }
                """);
        @SuppressWarnings("unchecked")
        String merchantId = (String) ((Map<String, Object>) data(createResult).get("createMerchant")).get("id");

        var result = graphql("""
                query($merchantId: ID!) { merchant(merchantId: $merchantId) { id name status } }
                """, Map.of("merchantId", merchantId));

        @SuppressWarnings("unchecked")
        var merchant = (Map<String, Object>) data(result).get("merchant");
        assertThat(merchant.get("name")).isEqualTo("Costco");
        assertThat(merchant.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void updateMerchant_name_returnsUpdated() {
        var createResult = graphql("""
                mutation { createMerchant(input: { name: "Kroger" }) { id } }
                """);
        @SuppressWarnings("unchecked")
        String merchantId = (String) ((Map<String, Object>) data(createResult).get("createMerchant")).get("id");

        var result = graphql("""
                mutation($merchantId: ID!) {
                    updateMerchant(merchantId: $merchantId, input: { name: "Kroger Supermarket" }) {
                        id name status
                    }
                }
                """, Map.of("merchantId", merchantId));

        @SuppressWarnings("unchecked")
        var merchant = (Map<String, Object>) data(result).get("updateMerchant");
        assertThat(merchant.get("name")).isEqualTo("Kroger Supermarket");
        assertThat(merchant.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void updateMerchant_status_returnsUpdated() {
        var createResult = graphql("""
                mutation { createMerchant(input: { name: "Safeway" }) { id } }
                """);
        @SuppressWarnings("unchecked")
        String merchantId = (String) ((Map<String, Object>) data(createResult).get("createMerchant")).get("id");

        var result = graphql("""
                mutation($merchantId: ID!) {
                    updateMerchant(merchantId: $merchantId, input: { status: ARCHIVED }) {
                        id name status
                    }
                }
                """, Map.of("merchantId", merchantId));

        @SuppressWarnings("unchecked")
        var merchant = (Map<String, Object>) data(result).get("updateMerchant");
        assertThat(merchant.get("status")).isEqualTo("ARCHIVED");
        assertThat(merchant.get("name")).isEqualTo("Safeway");
    }

    @Test
    void deleteMerchant_returnsTrue() {
        var createResult = graphql("""
                mutation { createMerchant(input: { name: "Whole Foods" }) { id } }
                """);
        @SuppressWarnings("unchecked")
        String merchantId = (String) ((Map<String, Object>) data(createResult).get("createMerchant")).get("id");

        var result = graphql("""
                mutation($merchantId: ID!) { deleteMerchant(merchantId: $merchantId) }
                """, Map.of("merchantId", merchantId));
        assertThat(data(result).get("deleteMerchant")).isEqualTo(true);

        // Verify it's actually deleted
        var getResult = graphql("""
                query($merchantId: ID!) { merchant(merchantId: $merchantId) { id } }
                """, Map.of("merchantId", merchantId));
        assertThat(getResult.get("errors")).isNotNull();
    }

    @Test
    void workspaceIsolation_cannotAccessOtherWorkspaceMerchants() {
        var createResult = graphql("""
                mutation { createMerchant(input: { name: "Trader Joe's" }) { id } }
                """);
        @SuppressWarnings("unchecked")
        String merchantId = (String) ((Map<String, Object>) data(createResult).get("createMerchant")).get("id");

        // Mint token for a different workspace
        UUID workspace2Id = UUID.randomUUID();
        String token2 = tokenService.mintToken(userId, workspace2Id);

        // Try to access with different workspace token
        Map<String, Object> body = Map.of("query", """
                query($merchantId: ID!) { merchant(merchantId: $merchantId) { id } }
                """, "variables", Map.of("merchantId", merchantId));
        @SuppressWarnings("unchecked")
        var response = restTemplate.exchange(
                "/graphql", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token2)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("errors")).isNotNull();
    }

    @Test
    void createMerchant_duplicateName_returnsError() {
        graphql("""
                mutation { createMerchant(input: { name: "Publix" }) { id } }
                """);

        var result = graphql("""
                mutation { createMerchant(input: { name: "Publix" }) { id } }
                """);
        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void createMerchant_duplicateNameCaseInsensitive_returnsError() {
        graphql("""
                mutation { createMerchant(input: { name: "HEB" }) { id } }
                """);

        var result = graphql("""
                mutation { createMerchant(input: { name: "heb" }) { id } }
                """);
        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void updateMerchant_toDuplicateName_returnsError() {
        graphql("""
                mutation { createMerchant(input: { name: "Aldi" }) { id } }
                """);

        var createResult = graphql("""
                mutation { createMerchant(input: { name: "Lidl" }) { id } }
                """);
        @SuppressWarnings("unchecked")
        String merchantId = (String) ((Map<String, Object>) data(createResult).get("createMerchant")).get("id");

        var result = graphql("""
                mutation($merchantId: ID!) {
                    updateMerchant(merchantId: $merchantId, input: { name: "Aldi" }) { id }
                }
                """, Map.of("merchantId", merchantId));
        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void getMerchant_notFound_returnsError() {
        var result = graphql("""
                query($merchantId: ID!) { merchant(merchantId: $merchantId) { id } }
                """, Map.of("merchantId", UUID.randomUUID().toString()));
        assertThat(result.get("errors")).isNotNull();
    }
}
