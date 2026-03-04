package com.dripl.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountGraphQLIT extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void setUp() {
        String email = "gql-acct-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "GQL", "User");
        token = (String) bootstrap.get("token");
    }

    private Map<String, Object> graphql(String query) {
        return graphql(query, null);
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
    void accounts_emptyList() {
        var result = graphql("{ accounts { id name } }");

        assertThat(result.get("errors")).isNull();
        List<?> accounts = (List<?>) data(result).get("accounts");
        assertThat(accounts).isEmpty();
    }

    @Test
    void createAccount_returnsAccount() {
        var result = graphql("""
                mutation {
                    createAccount(input: {
                        name: "Chase Checking"
                        type: CASH
                        subType: CHECKING
                    }) {
                        id name type subType balance currency source status
                    }
                }
                """);

        assertThat(result.get("errors")).isNull();
        @SuppressWarnings("unchecked")
        var account = (Map<String, Object>) data(result).get("createAccount");
        assertThat(account.get("name")).isEqualTo("Chase Checking");
        assertThat(account.get("type")).isEqualTo("CASH");
        assertThat(account.get("subType")).isEqualTo("CHECKING");
        assertThat(account.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullCrudLifecycle() {
        // Create
        var createResult = graphql("""
                mutation($input: CreateAccountInput!) {
                    createAccount(input: $input) { id name type subType }
                }
                """, Map.of("input", Map.of(
                "name", "Amex Gold",
                "type", "CREDIT",
                "subType", "CREDIT_CARD"
        )));
        assertThat(createResult.get("errors")).isNull();
        var created = (Map<String, Object>) data(createResult).get("createAccount");
        String accountId = (String) created.get("id");
        assertThat(created.get("name")).isEqualTo("Amex Gold");

        // Read single
        var getResult = graphql("""
                query($id: ID!) { account(id: $id) { id name } }
                """, Map.of("id", accountId));
        assertThat(getResult.get("errors")).isNull();
        var fetched = (Map<String, Object>) data(getResult).get("account");
        assertThat(fetched.get("name")).isEqualTo("Amex Gold");

        // Read list
        var listResult = graphql("{ accounts { id name } }");
        assertThat(listResult.get("errors")).isNull();
        var accounts = (List<Map<String, Object>>) data(listResult).get("accounts");
        assertThat(accounts).hasSize(1);

        // Update
        var updateResult = graphql("""
                mutation($id: ID!, $input: UpdateAccountInput!) {
                    updateAccount(id: $id, input: $input) { id name }
                }
                """, Map.of("id", accountId, "input", Map.of("name", "Amex Platinum")));
        assertThat(updateResult.get("errors")).isNull();
        var updated = (Map<String, Object>) data(updateResult).get("updateAccount");
        assertThat(updated.get("name")).isEqualTo("Amex Platinum");

        // Delete
        var deleteResult = graphql("""
                mutation($id: ID!) { deleteAccount(id: $id) }
                """, Map.of("id", accountId));
        assertThat(deleteResult.get("errors")).isNull();
        assertThat(data(deleteResult).get("deleteAccount")).isEqualTo(true);

        // Verify deleted
        var afterDelete = graphql("{ accounts { id } }");
        accounts = (List<Map<String, Object>>) data(afterDelete).get("accounts");
        assertThat(accounts).isEmpty();
    }

    @Test
    void unauthenticated_returns401() {
        var response = restTemplate.exchange(
                "/graphql", HttpMethod.POST,
                new HttpEntity<>(Map.of("query", "{ accounts { id } }"), jsonHeaders()),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
