package com.dripl.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountCrudIT extends BaseIntegrationTest {

    private String token;
    private String workspaceId;

    @BeforeEach
    void setUp() {
        String email = "acct-user-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Acct", "User");
        token = (String) bootstrap.get("token");
        workspaceId = (String) bootstrap.get("lastWorkspaceId");
    }

    @Test
    void createAccount_validCashChecking_returns201() {
        var response = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Chase Checking","type":"CASH","subType":"CHECKING"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().get("name")).isEqualTo("Chase Checking");
        assertThat(response.getBody().get("type")).isEqualTo("CASH");
        assertThat(response.getBody().get("subType")).isEqualTo("CHECKING");
        assertThat(response.getBody().get("startingBalance")).isEqualTo(0);
        assertThat(response.getBody().get("balance")).isEqualTo(0);
        assertThat(response.getBody().get("currency")).isEqualTo("USD");
        assertThat(response.getBody().get("source")).isEqualTo("MANUAL");
        assertThat(response.getBody().get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void createAccount_withAllFields_returns201() {
        var response = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {
                            "name":"Amex Gold",
                            "type":"CREDIT",
                            "subType":"CREDIT_CARD",
                            "startingBalance":-1500.00,
                            "currency":"EUR",
                            "institutionName":"American Express",
                            "source":"AUTOMATIC"
                        }
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("name")).isEqualTo("Amex Gold");
        assertThat(response.getBody().get("institutionName")).isEqualTo("American Express");
        assertThat(response.getBody().get("currency")).isEqualTo("EUR");
        assertThat(response.getBody().get("source")).isEqualTo("AUTOMATIC");
    }

    @Test
    void createAccount_duplicateName_returns409() {
        restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Checking","type":"CASH","subType":"CHECKING"}
                        """, authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Checking","type":"CASH","subType":"SAVINGS"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createAccount_invalidTypeSubType_returns400() {
        var response = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Bad","type":"CASH","subType":"CREDIT_CARD"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createAccount_missingName_returns400() {
        var response = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"type":"CASH","subType":"CHECKING"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listAccounts_returnsCreatedAccounts() {
        restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Checking","type":"CASH","subType":"CHECKING"}
                        """, authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Savings","type":"CASH","subType":"SAVINGS"}
                        """, authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void getAccount_returnsAccount() {
        var created = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Checking","type":"CASH","subType":"CHECKING"}
                        """, authHeaders(token)),
                Map.class);
        String id = (String) created.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/accounts/" + id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Checking");
    }

    @Test
    void getAccount_notFound_returns404() {
        var response = restTemplate.exchange(
                "/api/v1/accounts/00000000-0000-0000-0000-000000000000", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateAccount_name_returns200() {
        var created = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Old Name","type":"CASH","subType":"CHECKING"}
                        """, authHeaders(token)),
                Map.class);
        String id = (String) created.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/accounts/" + id, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"name":"New Name"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("New Name");
    }

    @Test
    void updateAccount_close_setsClosedAt() {
        var created = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"To Close","type":"CASH","subType":"CHECKING"}
                        """, authHeaders(token)),
                Map.class);
        String id = (String) created.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/accounts/" + id, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"status":"CLOSED"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("CLOSED");
        assertThat(response.getBody().get("closedAt")).isNotNull();
    }

    @Test
    void deleteAccount_returns204() {
        var created = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"To Delete","type":"CASH","subType":"CHECKING"}
                        """, authHeaders(token)),
                Map.class);
        String id = (String) created.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/accounts/" + id, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify it's gone
        var get = restTemplate.exchange(
                "/api/v1/accounts/" + id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void balanceRecompute_afterTransactionCreate_balanceUpdates() {
        // Create account with starting balance
        var acctResp = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Balance Test","type":"CASH","subType":"CHECKING","startingBalance":5000.00}
                        """, authHeaders(token)),
                Map.class);
        String acctId = (String) acctResp.getBody().get("id");
        assertThat(((Number) acctResp.getBody().get("balance")).doubleValue()).isEqualTo(5000.00);

        // Create a transaction against this account
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"TestStore","date":"2025-07-01T00:00:00","amount":-120.50}
                        """.formatted(acctId), authHeaders(token)),
                Map.class);

        // Check balance was recomputed
        var getResp = restTemplate.exchange(
                "/api/v1/accounts/" + acctId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(((Number) getResp.getBody().get("startingBalance")).doubleValue()).isEqualTo(5000.00);
        assertThat(((Number) getResp.getBody().get("balance")).doubleValue()).isEqualTo(4879.50);
        assertThat(getResp.getBody().get("balanceLastUpdated")).isNotNull();
    }

    @Test
    void balanceRecompute_afterTransactionDelete_balanceRestores() {
        var acctResp = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Del Balance","type":"CASH","subType":"CHECKING","startingBalance":1000.00}
                        """, authHeaders(token)),
                Map.class);
        String acctId = (String) acctResp.getBody().get("id");

        var txnResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Store","date":"2025-07-01T00:00:00","amount":-200.00}
                        """.formatted(acctId), authHeaders(token)),
                Map.class);
        String txnId = (String) txnResp.getBody().get("id");

        // Delete the transaction
        restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Void.class);

        // Balance should restore to starting balance
        var getResp = restTemplate.exchange(
                "/api/v1/accounts/" + acctId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(((Number) getResp.getBody().get("balance")).doubleValue()).isEqualTo(1000.00);
    }

    @Test
    void balanceRecompute_updateStartingBalance_recomputes() {
        var acctResp = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"SB Update","type":"CASH","subType":"CHECKING","startingBalance":3000.00}
                        """, authHeaders(token)),
                Map.class);
        String acctId = (String) acctResp.getBody().get("id");

        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Shop","date":"2025-07-01T00:00:00","amount":-500.00}
                        """.formatted(acctId), authHeaders(token)),
                Map.class);

        // Update starting balance
        var patchResp = restTemplate.exchange(
                "/api/v1/accounts/" + acctId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"startingBalance":4000.00}
                        """, authHeaders(token)),
                Map.class);

        assertThat(((Number) patchResp.getBody().get("startingBalance")).doubleValue()).isEqualTo(4000.00);
        assertThat(((Number) patchResp.getBody().get("balance")).doubleValue()).isEqualTo(3500.00);
    }

    @Test
    void workspaceIsolation_accountsNotVisibleAcrossWorkspaces() {
        // Create an account in the first workspace
        restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Private Account","type":"CASH","subType":"CHECKING"}
                        """, authHeaders(token)),
                Map.class);

        // Bootstrap a different user with their own workspace
        String otherEmail = "acct-other-%s@test.com".formatted(System.nanoTime());
        var otherBootstrap = bootstrapUser(otherEmail, "Other", "User");
        String otherToken = (String) otherBootstrap.get("token");

        // Another user should see no accounts
        var response = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.GET,
                new HttpEntity<>(authHeaders(otherToken)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }
}
