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

class TransactionGroupCrudIT extends BaseIntegrationTest {

    @Autowired private TokenService tokenService;

    private String token;
    private UUID workspaceId;
    private String accountId;
    private String txn1Id;
    private String txn2Id;
    private String txn3Id;
    private String categoryId;
    private String tagId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        String email = "group-user-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Group", "User");
        token = (String) bootstrap.get("token");
        workspaceId = UUID.fromString((String) bootstrap.get("lastWorkspaceId"));

        // Create account
        var accountResp = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Checking","type":"CASH","subType":"CHECKING","balance":1000}
                        """, authHeaders(token)),
                Map.class);
        accountId = (String) accountResp.getBody().get("id");

        // Create category
        var categoryResp = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Vacation"}
                        """, authHeaders(token)),
                Map.class);
        categoryId = (String) categoryResp.getBody().get("id");

        // Create tag
        var tagResp = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"beach-trip"}
                        """, authHeaders(token)),
                Map.class);
        tagId = (String) tagResp.getBody().get("id");

        // Create 3 transactions
        txn1Id = createTransaction("Restaurant A", -50.00);
        txn2Id = createTransaction("Gas Station", -30.00);
        txn3Id = createTransaction("Hotel", -200.00);
    }

    private String createTransaction(String merchantName, double amount) {
        var resp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"%s","date":"2025-07-01T00:00:00","amount":%s}
                        """.formatted(accountId, merchantName, amount), authHeaders(token)),
                Map.class);
        return (String) resp.getBody().get("id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createTransactionGroup_returns201() {
        var response = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Beach Vacation","categoryId":"%s","tagIds":["%s"],"transactionIds":["%s","%s"]}
                        """.formatted(categoryId, tagId, txn1Id, txn2Id), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = response.getBody();
        assertThat(body.get("name")).isEqualTo("Beach Vacation");
        assertThat(body.get("categoryId")).isEqualTo(categoryId);
        assertThat((List<String>) body.get("tagIds")).containsExactly(tagId);
        assertThat(body.get("totalAmount")).isEqualTo(-80.0);
        assertThat((List<?>) body.get("transactionIds")).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactionGroups_returns200() {
        // Create a group
        restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s"]}
                        """.formatted(txn1Id, txn2Id), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getTransactionGroup_returns200() {
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s"]}
                        """.formatted(txn1Id, txn2Id), authHeaders(token)),
                Map.class);
        String groupId = (String) createResp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/transaction-groups/" + groupId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Trip");
        assertThat((List<?>) response.getBody().get("transactionIds")).hasSize(2);
    }

    @Test
    void updateTransactionGroup_returns200() {
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s"]}
                        """.formatted(txn1Id, txn2Id), authHeaders(token)),
                Map.class);
        String groupId = (String) createResp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/transaction-groups/" + groupId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"name":"Beach Vacation 2025","categoryId":"%s"}
                        """.formatted(categoryId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Beach Vacation 2025");
        assertThat(response.getBody().get("categoryId")).isEqualTo(categoryId);
    }

    @Test
    void addTransactionToGroup_viaTransactionIds() {
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s"]}
                        """.formatted(txn1Id, txn2Id), authHeaders(token)),
                Map.class);
        String groupId = (String) createResp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/transaction-groups/" + groupId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"transactionIds":["%s","%s","%s"]}
                        """.formatted(txn1Id, txn2Id, txn3Id), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("transactionIds")).hasSize(3);
        assertThat(response.getBody().get("totalAmount")).isEqualTo(-280.0);
    }

    @Test
    void removeTransactionFromGroup_viaTransactionIds() {
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s","%s"]}
                        """.formatted(txn1Id, txn2Id, txn3Id), authHeaders(token)),
                Map.class);
        String groupId = (String) createResp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/transaction-groups/" + groupId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"transactionIds":["%s","%s"]}
                        """.formatted(txn1Id, txn2Id), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("transactionIds")).hasSize(2);
    }

    @Test
    void updateGroupTransactionIds_belowMinimum_returns400() {
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s"]}
                        """.formatted(txn1Id, txn2Id), authHeaders(token)),
                Map.class);
        String groupId = (String) createResp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/transaction-groups/" + groupId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"transactionIds":["%s"]}
                        """.formatted(txn1Id), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteTransactionGroup_dissolvesGroup() {
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s"]}
                        """.formatted(txn1Id, txn2Id), authHeaders(token)),
                Map.class);
        String groupId = (String) createResp.getBody().get("id");

        // Delete the group
        var deleteResp = restTemplate.exchange(
                "/api/v1/transaction-groups/" + groupId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Void.class);
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify group is gone
        var getResp = restTemplate.exchange(
                "/api/v1/transaction-groups/" + groupId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Verify transactions still exist and groupId is null
        var txnResp = restTemplate.exchange(
                "/api/v1/transactions/" + txn1Id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(txnResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(txnResp.getBody().get("groupId")).isNull();
    }

    @Test
    void transactionAlreadyInGroup_returns400() {
        // Create first group with txn1 and txn2
        restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip 1","transactionIds":["%s","%s"]}
                        """.formatted(txn1Id, txn2Id), authHeaders(token)),
                Map.class);

        // Try to create another group with txn1 (already grouped)
        var response = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip 2","transactionIds":["%s","%s"]}
                        """.formatted(txn1Id, txn3Id), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void transactionShowsGroupId() {
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s"]}
                        """.formatted(txn1Id, txn2Id), authHeaders(token)),
                Map.class);
        String groupId = (String) createResp.getBody().get("id");

        // Verify transaction shows groupId
        var txnResp = restTemplate.exchange(
                "/api/v1/transactions/" + txn1Id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(txnResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(txnResp.getBody().get("groupId")).isEqualTo(groupId);
    }

    @Test
    void createGroup_lessThan2Transactions_returns400() {
        var response = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Solo","transactionIds":["%s"]}
                        """.formatted(txn1Id), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createGroup_overridesCategoryTagsNotesOnTransactions() {
        // Create group with category, tags, and notes
        restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","categoryId":"%s","tagIds":["%s"],"notes":"group notes","transactionIds":["%s","%s"]}
                        """.formatted(categoryId, tagId, txn1Id, txn2Id), authHeaders(token)),
                Map.class);

        // Verify transactions inherited group's category, tags, and notes
        var txn1Resp = restTemplate.exchange(
                "/api/v1/transactions/" + txn1Id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(txn1Resp.getBody().get("categoryId")).isEqualTo(categoryId);
        assertThat((List<String>) txn1Resp.getBody().get("tagIds")).containsExactly(tagId);
        assertThat(txn1Resp.getBody().get("notes")).isEqualTo("group notes");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateGroup_overridesCategoryTagsOnTransactions() {
        // Create group without overrides
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s"]}
                        """.formatted(txn1Id, txn2Id), authHeaders(token)),
                Map.class);
        String groupId = (String) createResp.getBody().get("id");

        // Update group with category and tags
        restTemplate.exchange(
                "/api/v1/transaction-groups/" + groupId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"categoryId":"%s","tagIds":["%s"],"notes":"updated notes"}
                        """.formatted(categoryId, tagId), authHeaders(token)),
                Map.class);

        // Verify transactions got the overrides
        var txn1Resp = restTemplate.exchange(
                "/api/v1/transactions/" + txn1Id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(txn1Resp.getBody().get("categoryId")).isEqualTo(categoryId);
        assertThat((List<String>) txn1Resp.getBody().get("tagIds")).containsExactly(tagId);
        assertThat(txn1Resp.getBody().get("notes")).isEqualTo("updated notes");
    }

    @Test
    @SuppressWarnings("unchecked")
    void addTransaction_inheritsGroupOverrides() {
        // Create group with category
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","categoryId":"%s","tagIds":["%s"],"transactionIds":["%s","%s"]}
                        """.formatted(categoryId, tagId, txn1Id, txn2Id), authHeaders(token)),
                Map.class);
        String groupId = (String) createResp.getBody().get("id");

        // Add txn3 via transactionIds
        restTemplate.exchange(
                "/api/v1/transaction-groups/" + groupId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"transactionIds":["%s","%s","%s"]}
                        """.formatted(txn1Id, txn2Id, txn3Id), authHeaders(token)),
                Map.class);

        // Verify txn3 inherited group overrides
        var txn3Resp = restTemplate.exchange(
                "/api/v1/transactions/" + txn3Id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(txn3Resp.getBody().get("categoryId")).isEqualTo(categoryId);
        assertThat((List<String>) txn3Resp.getBody().get("tagIds")).containsExactly(tagId);
    }

    @Test
    void removeTransactionFromGroup_clearsGroupIdOnRemovedTxn() {
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s","%s"]}
                        """.formatted(txn1Id, txn2Id, txn3Id), authHeaders(token)),
                Map.class);
        String groupId = (String) createResp.getBody().get("id");

        // Remove txn3 via transactionIds
        restTemplate.exchange(
                "/api/v1/transaction-groups/" + groupId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"transactionIds":["%s","%s"]}
                        """.formatted(txn1Id, txn2Id), authHeaders(token)),
                Map.class);

        // Verify txn3 no longer has groupId
        var txn3Resp = restTemplate.exchange(
                "/api/v1/transactions/" + txn3Id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(txn3Resp.getBody().get("groupId")).isNull();

        // Verify txn1 still in group
        var txn1Resp = restTemplate.exchange(
                "/api/v1/transactions/" + txn1Id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(txn1Resp.getBody().get("groupId")).isEqualTo(groupId);
    }

    @Test
    void addRecurringLinkedTransaction_viaTransactionIds_returns400() {
        // Create group
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s"]}
                        """.formatted(txn1Id, txn2Id), authHeaders(token)),
                Map.class);
        String groupId = (String) createResp.getBody().get("id");

        // Create a recurring item
        var riResp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Netflix","amount":-15.99,"frequencyGranularity":"MONTH","anchorDates":["2025-01-15T00:00:00"],"startDate":"2025-01-01T00:00:00"}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String riId = (String) riResp.getBody().get("id");

        // Create RI-linked transaction
        var riTxnResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"recurringItemId":"%s","date":"2025-07-01T00:00:00"}
                        """.formatted(riId), authHeaders(token)),
                Map.class);
        String riTxnId = (String) riTxnResp.getBody().get("id");

        // Try to add RI-linked txn to group via PATCH — should fail
        var response = restTemplate.exchange(
                "/api/v1/transaction-groups/" + groupId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"transactionIds":["%s","%s","%s"]}
                        """.formatted(txn1Id, txn2Id, riTxnId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteGroup_clearsGroupIdOnAllTransactions() {
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s"]}
                        """.formatted(txn1Id, txn2Id), authHeaders(token)),
                Map.class);
        String groupId = (String) createResp.getBody().get("id");

        // Delete group
        restTemplate.exchange(
                "/api/v1/transaction-groups/" + groupId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Void.class);

        // Both transactions should have null groupId
        var txn1Resp = restTemplate.exchange(
                "/api/v1/transactions/" + txn1Id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        var txn2Resp = restTemplate.exchange(
                "/api/v1/transactions/" + txn2Id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(txn1Resp.getBody().get("groupId")).isNull();
        assertThat(txn2Resp.getBody().get("groupId")).isNull();
    }
}
