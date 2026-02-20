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

class TransactionSplitCrudIT extends BaseIntegrationTest {

    @Autowired private TokenService tokenService;

    private String token;
    private UUID workspaceId;
    private String accountId;
    private String categoryId;
    private String category2Id;
    private String tagId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        String email = "split-user-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Split", "User");
        token = (String) bootstrap.get("token");
        workspaceId = UUID.fromString((String) bootstrap.get("lastWorkspaceId"));

        // Create account
        var accountResp = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Checking","type":"CASH","subType":"CHECKING","balance":5000}
                        """, authHeaders(token)),
                Map.class);
        accountId = (String) accountResp.getBody().get("id");

        // Create categories
        var catResp = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries"}
                        """, authHeaders(token)),
                Map.class);
        categoryId = (String) catResp.getBody().get("id");

        var cat2Resp = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Electronics"}
                        """, authHeaders(token)),
                Map.class);
        category2Id = (String) cat2Resp.getBody().get("id");

        // Create tag
        var tagResp = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"split-tag"}
                        """, authHeaders(token)),
                Map.class);
        tagId = (String) tagResp.getBody().get("id");
    }

    private String createTransaction(String merchantName, double amount) {
        var resp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"%s","date":"2025-07-14T00:00:00","amount":%s}
                        """.formatted(accountId, merchantName, amount), authHeaders(token)),
                Map.class);
        return (String) resp.getBody().get("id");
    }

    // --- CRUD ---

    @Test
    @SuppressWarnings("unchecked")
    void createSplit_returns201() {
        String txnId = createTransaction("Target", -100.00);

        var response = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-60.00,"categoryId":"%s"},
                            {"amount":-40.00,"categoryId":"%s","notes":"USB cable"}
                        ]}
                        """.formatted(txnId, categoryId, category2Id), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = response.getBody();
        assertThat(body.get("totalAmount")).isEqualTo(-100.0);
        assertThat(body.get("accountId")).isEqualTo(accountId);
        assertThat((List<?>) body.get("transactionIds")).hasSize(2);

        // Original transaction should be deleted
        var txnResp = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(txnResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSplits_returns200() {
        String txnId = createTransaction("Publix", -80.00);
        restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-50.00},{"amount":-30.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSplit_returns200() {
        String txnId = createTransaction("Walmart", -120.00);
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-70.00},{"amount":-50.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        String splitId = (String) createResp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/transaction-splits/" + splitId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalAmount")).isEqualTo(-120.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateSplit_updateChildren_returns200() {
        String txnId = createTransaction("Target", -100.00);
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-60.00},{"amount":-40.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        String splitId = (String) createResp.getBody().get("id");
        List<String> childIds = (List<String>) createResp.getBody().get("transactionIds");

        // Update amounts
        var response = restTemplate.exchange(
                "/api/v1/transaction-splits/" + splitId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"children":[
                            {"id":"%s","amount":-70.00},
                            {"id":"%s","amount":-30.00}
                        ]}
                        """.formatted(childIds.get(0), childIds.get(1)), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateSplit_addNewChild_removeExisting() {
        String txnId = createTransaction("Target", -100.00);
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-60.00},{"amount":-40.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        String splitId = (String) createResp.getBody().get("id");
        List<String> childIds = (List<String>) createResp.getBody().get("transactionIds");

        // Keep first child, remove second, add new
        var response = restTemplate.exchange(
                "/api/v1/transaction-splits/" + splitId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"children":[
                            {"id":"%s","amount":-50.00},
                            {"amount":-50.00,"merchantName":"Walmart"}
                        ]}
                        """.formatted(childIds.get(0)), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("transactionIds")).hasSize(2);
    }

    @Test
    void deleteSplit_dissolves() {
        String txnId = createTransaction("Target", -100.00);
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-60.00},{"amount":-40.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        String splitId = (String) createResp.getBody().get("id");
        List<String> childIds = (List<String>) createResp.getBody().get("transactionIds");

        // Dissolve
        var deleteResp = restTemplate.exchange(
                "/api/v1/transaction-splits/" + splitId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Void.class);
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Split is gone
        var getResp = restTemplate.exchange(
                "/api/v1/transaction-splits/" + splitId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Children still exist with splitId = null
        var txnResp = restTemplate.exchange(
                "/api/v1/transactions/" + childIds.get(0), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(txnResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(txnResp.getBody().get("splitId")).isNull();
    }

    // --- Amount Validation ---

    @Test
    void createSplit_amountMismatch_returns400() {
        String txnId = createTransaction("Target", -100.00);

        var response = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-60.00},{"amount":-30.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateSplit_amountMismatch_returns400() {
        String txnId = createTransaction("Target", -100.00);
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-60.00},{"amount":-40.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        String splitId = (String) createResp.getBody().get("id");
        List<String> childIds = (List<String>) createResp.getBody().get("transactionIds");

        var response = restTemplate.exchange(
                "/api/v1/transaction-splits/" + splitId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"children":[
                            {"id":"%s","amount":-70.00},
                            {"id":"%s","amount":-40.00}
                        ]}
                        """.formatted(childIds.get(0), childIds.get(1)), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- Split Locked Fields ---

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_rejectAccountIdChange() {
        String txnId = createTransaction("Target", -100.00);
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-60.00},{"amount":-40.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        List<String> childIds = (List<String>) createResp.getBody().get("transactionIds");

        var response = restTemplate.exchange(
                "/api/v1/transactions/" + childIds.get(0), HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"accountId":"%s"}
                        """.formatted(UUID.randomUUID()), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_rejectAmountChange() {
        String txnId = createTransaction("Target", -100.00);
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-60.00},{"amount":-40.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        List<String> childIds = (List<String>) createResp.getBody().get("transactionIds");

        var response = restTemplate.exchange(
                "/api/v1/transactions/" + childIds.get(0), HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"amount":-99.99}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_rejectDateChange() {
        String txnId = createTransaction("Target", -100.00);
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-60.00},{"amount":-40.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        List<String> childIds = (List<String>) createResp.getBody().get("transactionIds");

        var response = restTemplate.exchange(
                "/api/v1/transactions/" + childIds.get(0), HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"date":"2025-12-01T00:00:00"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_allowCategoryChange() {
        String txnId = createTransaction("Target", -100.00);
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-60.00},{"amount":-40.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        List<String> childIds = (List<String>) createResp.getBody().get("transactionIds");

        var response = restTemplate.exchange(
                "/api/v1/transactions/" + childIds.get(0), HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"categoryId":"%s"}
                        """.formatted(category2Id), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("categoryId")).isEqualTo(category2Id);
    }

    // --- Mutual Exclusivity ---

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_cannotBeGrouped() {
        String txnId = createTransaction("Target", -100.00);
        String txnId2 = createTransaction("Walmart", -50.00);
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-60.00},{"amount":-40.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        List<String> childIds = (List<String>) createResp.getBody().get("transactionIds");

        // Try to create group with a split child
        var response = restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Group","transactionIds":["%s","%s"]}
                        """.formatted(childIds.get(0), txnId2), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void groupedTransaction_cannotBeSplit() {
        String txnId = createTransaction("Target", -100.00);
        String txnId2 = createTransaction("Walmart", -50.00);

        // Create group
        restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Group","transactionIds":["%s","%s"]}
                        """.formatted(txnId, txnId2), authHeaders(token)),
                Map.class);

        // Try to split a grouped transaction
        var response = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-60.00},{"amount":-40.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- Split + RI linking ---

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_canBeRILinked() {
        String txnId = createTransaction("Netflix", -30.00);
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-15.00},{"amount":-15.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        List<String> childIds = (List<String>) createResp.getBody().get("transactionIds");

        // Create a recurring item with same account
        var riResp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"Netflix","accountId":"%s","merchantName":"Netflix",
                         "amount":-15.00,"currencyCode":"USD",
                         "frequencyGranularity":"MONTH","frequencyQuantity":1,
                         "anchorDates":["2025-07-01T00:00:00"],"startDate":"2025-01-01T00:00:00"}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String riId = (String) riResp.getBody().get("id");

        // Link RI to split child
        var response = restTemplate.exchange(
                "/api/v1/transactions/" + childIds.get(0), HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"recurringItemId":"%s"}
                        """.formatted(riId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("recurringItemId")).isEqualTo(riId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_RIMismatchAccount_returns400() {
        String txnId = createTransaction("Netflix", -30.00);
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-15.00},{"amount":-15.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        List<String> childIds = (List<String>) createResp.getBody().get("transactionIds");

        // Create a different account
        var account2Resp = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Savings","type":"CASH","subType":"SAVINGS","balance":10000}
                        """, authHeaders(token)),
                Map.class);
        String account2Id = (String) account2Resp.getBody().get("id");

        // Create RI with different account
        var riResp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"Netflix Savings","accountId":"%s","merchantName":"Netflix",
                         "amount":-15.00,"currencyCode":"USD",
                         "frequencyGranularity":"MONTH","frequencyQuantity":1,
                         "anchorDates":["2025-07-01T00:00:00"],"startDate":"2025-01-01T00:00:00"}
                        """.formatted(account2Id), authHeaders(token)),
                Map.class);
        String riId = (String) riResp.getBody().get("id");

        // Try to link RI to split child — account mismatch
        var response = restTemplate.exchange(
                "/api/v1/transactions/" + childIds.get(0), HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"recurringItemId":"%s"}
                        """.formatted(riId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- splitId is fully locked ---

    @Test
    @SuppressWarnings("unchecked")
    void unlinkSplitChild_returns400() {
        String txnId = createTransaction("Target", -100.00);
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-33.00},{"amount":-33.00},{"amount":-34.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        List<String> childIds = (List<String>) createResp.getBody().get("transactionIds");

        var response = restTemplate.exchange(
                "/api/v1/transactions/" + childIds.get(0), HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"splitId":null}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void assignSplitId_returns400() {
        String txnId = createTransaction("Target", -50.00);

        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"splitId":"%s"}
                        """.formatted(UUID.randomUUID()), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- Child inherits splitId ---

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_showsSplitId() {
        String txnId = createTransaction("Target", -100.00);
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-60.00},{"amount":-40.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        String splitId = (String) createResp.getBody().get("id");
        List<String> childIds = (List<String>) createResp.getBody().get("transactionIds");

        var txnResp = restTemplate.exchange(
                "/api/v1/transactions/" + childIds.get(0), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(txnResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(txnResp.getBody().get("splitId")).isEqualTo(splitId);
    }

    // --- Filter by splitId ---

    @Test
    @SuppressWarnings("unchecked")
    void filterTransactionsBySplitId() {
        String txnId = createTransaction("Target", -100.00);
        var createResp = restTemplate.exchange(
                "/api/v1/transaction-splits", HttpMethod.POST,
                new HttpEntity<>("""
                        {"transactionId":"%s","children":[
                            {"amount":-60.00},{"amount":-40.00}
                        ]}
                        """.formatted(txnId), authHeaders(token)),
                Map.class);
        String splitId = (String) createResp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/transactions?splitId=" + splitId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }
}
