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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class TransactionEventIT extends BaseIntegrationTest {

    @Autowired private TokenService tokenService;

    private String token;
    private UUID workspaceId;
    private String accountId;
    private String categoryId;
    private String categoryId2;

    @BeforeEach
    void setUp() {
        String email = "event-user-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Event", "User");
        token = (String) bootstrap.get("token");
        workspaceId = UUID.fromString((String) bootstrap.get("lastWorkspaceId"));

        // Create an account
        var accountResp = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Checking","type":"CASH","subType":"CHECKING","startingBalance":1000}
                        """, authHeaders(token)),
                Map.class);
        accountId = (String) accountResp.getBody().get("id");

        // Create categories (expense)
        var categoryResp = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries"}
                        """, authHeaders(token)),
                Map.class);
        categoryId = (String) categoryResp.getBody().get("id");

        var categoryResp2 = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Dining"}
                        """, authHeaders(token)),
                Map.class);
        categoryId2 = (String) categoryResp2.getBody().get("id");
    }

    private String createTransaction(String amount, String categoryId) {
        var resp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"TestMerchant","amount":%s,"date":"2025-07-01","categoryId":"%s"}
                        """.formatted(accountId, amount, categoryId), authHeaders(token)),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resp.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getEvents(String transactionId) {
        var resp = restTemplate.exchange(
                "/api/v1/transactions/%s/events".formatted(transactionId), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> awaitEvents(String transactionId, int expectedCount) {
        return await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> getEvents(transactionId), events -> events.size() >= expectedCount);
    }

    @Test
    void createTransaction_producesCreatedEvent() {
        String txnId = createTransaction("-42.50", categoryId);

        var events = awaitEvents(txnId, 1);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("eventType")).isEqualTo("transaction.created");
        assertThat(events.get(0).get("performedBy")).isNotNull();

        @SuppressWarnings("unchecked")
        var changes = (List<Map<String, Object>>) events.get(0).get("changes");
        assertThat(changes).isNotEmpty();
        assertThat(changes.stream().anyMatch(c -> "amount".equals(c.get("field")))).isTrue();
    }

    @Test
    void updateTransaction_producesUpdatedEventWithDiff() {
        String txnId = createTransaction("-42.50", categoryId);
        awaitEvents(txnId, 1);

        // Update amount
        restTemplate.exchange(
                "/api/v1/transactions/%s".formatted(txnId), HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"amount":-99.99}
                        """, authHeaders(token)),
                Map.class);

        var events = awaitEvents(txnId, 2);
        assertThat(events).hasSize(2);
        // Events ordered desc — newest first
        assertThat(events.get(0).get("eventType")).isEqualTo("transaction.updated");

        @SuppressWarnings("unchecked")
        var changes = (List<Map<String, Object>>) events.get(0).get("changes");
        var amountChange = changes.stream().filter(c -> "amount".equals(c.get("field"))).findFirst().orElse(null);
        assertThat(amountChange).isNotNull();
        assertThat(amountChange.get("oldValue").toString()).contains("42.5");
        assertThat(amountChange.get("newValue").toString()).contains("99.99");
    }

    @Test
    void updateTransaction_categoryChange_producesEvent() {
        String txnId = createTransaction("-42.50", categoryId);
        awaitEvents(txnId, 1);

        restTemplate.exchange(
                "/api/v1/transactions/%s".formatted(txnId), HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"categoryId":"%s"}
                        """.formatted(categoryId2), authHeaders(token)),
                Map.class);

        var events = awaitEvents(txnId, 2);
        assertThat(events.get(0).get("eventType")).isEqualTo("transaction.updated");

        @SuppressWarnings("unchecked")
        var changes = (List<Map<String, Object>>) events.get(0).get("changes");
        assertThat(changes.stream().anyMatch(c -> "category".equals(c.get("field")))).isTrue();
    }

    @Test
    void updateTransaction_noActualChanges_noEvent() {
        String txnId = createTransaction("-42.50", categoryId);
        awaitEvents(txnId, 1);

        // Update with same values — should not produce event
        restTemplate.exchange(
                "/api/v1/transactions/%s".formatted(txnId), HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"amount":-42.50}
                        """, authHeaders(token)),
                Map.class);

        // Wait a bit and verify no new event
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        var events = getEvents(txnId);
        assertThat(events).hasSize(1); // Only the created event
    }

    @Test
    void groupTransaction_producesGroupedEvents() {
        String txnId1 = createTransaction("-10.00", categoryId);
        String txnId2 = createTransaction("-20.00", categoryId);
        awaitEvents(txnId1, 1);
        awaitEvents(txnId2, 1);

        // Create group
        restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Test Group","transactionIds":["%s","%s"]}
                        """.formatted(txnId1, txnId2), authHeaders(token)),
                Map.class);

        var events1 = awaitEvents(txnId1, 2);
        assertThat(events1.stream().anyMatch(e -> "transaction.grouped".equals(e.get("eventType")))).isTrue();

        var events2 = awaitEvents(txnId2, 2);
        assertThat(events2.stream().anyMatch(e -> "transaction.grouped".equals(e.get("eventType")))).isTrue();
    }

    @Test
    void dissolveGroup_producesUngroupedEvents() {
        String txnId1 = createTransaction("-10.00", categoryId);
        String txnId2 = createTransaction("-20.00", categoryId);
        awaitEvents(txnId1, 1);
        awaitEvents(txnId2, 1);

        var groupResp = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Test Group","transactionIds":["%s","%s"]}
                        """.formatted(txnId1, txnId2), authHeaders(token)),
                Map.class);
        String groupId = (String) groupResp.getBody().get("id");
        awaitEvents(txnId1, 2);

        // Dissolve group
        restTemplate.exchange(
                "/api/v1/transaction-groups/%s".formatted(groupId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Void.class);

        var events = awaitEvents(txnId1, 3);
        assertThat(events.stream().anyMatch(e -> "transaction.ungrouped".equals(e.get("eventType")))).isTrue();
    }

    @Test
    void splitTransaction_producesEvents() {
        String txnId = createTransaction("-100.00", categoryId);
        awaitEvents(txnId, 1);

        var splitResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[{"amount":-60.00},{"amount":-40.00}]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        assertThat(splitResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String splitId = (String) splitResp.getBody().get("id");

        // Get the children
        @SuppressWarnings("unchecked")
        var childIds = (List<String>) splitResp.getBody().get("transactionIds");
        assertThat(childIds).hasSize(2);

        // Each child should have a "transaction.split" event
        for (String childId : childIds) {
            var events = awaitEvents(childId, 1);
            assertThat(events.stream().anyMatch(e -> "transaction.split".equals(e.get("eventType")))).isTrue();
        }
    }

    @Test
    void dissolveSplit_producesUnsplitEvents() {
        String txnId = createTransaction("-100.00", categoryId);
        awaitEvents(txnId, 1);

        var splitResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[{"amount":-60.00},{"amount":-40.00}]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        String splitId = (String) splitResp.getBody().get("id");

        @SuppressWarnings("unchecked")
        var childIds = (List<String>) splitResp.getBody().get("transactionIds");
        awaitEvents(childIds.get(0), 1);

        // Dissolve split
        restTemplate.exchange(
                "/api/v1/transaction-splits/%s".formatted(splitId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Void.class);

        var events = awaitEvents(childIds.get(0), 2);
        assertThat(events.stream().anyMatch(e -> "transaction.unsplit".equals(e.get("eventType")))).isTrue();
    }

    @Test
    void eventsEndpoint_nonexistentTransaction_returns404() {
        var resp = restTemplate.exchange(
                "/api/v1/transactions/%s/events".formatted(UUID.randomUUID()), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteTransaction_cascadesDeleteEvents() {
        String txnId = createTransaction("-42.50", categoryId);
        awaitEvents(txnId, 1);

        // Delete the transaction
        restTemplate.exchange(
                "/api/v1/transactions/%s".formatted(txnId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Void.class);

        // Events should be gone (cascade delete) — transaction itself 404
        var resp = restTemplate.exchange(
                "/api/v1/transactions/%s/events".formatted(txnId), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
