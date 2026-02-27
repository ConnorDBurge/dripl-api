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

class TransactionCrudIT extends BaseIntegrationTest {

    @Autowired private TokenService tokenService;

    private String token;
    private UUID workspaceId;
    private UUID userId;
    private String accountId;
    private String categoryId;
    private String tagId;
    private String recurringItemId;
    private String recurringMerchantId;

    @BeforeEach
    void setUp() {
        String email = "txn-user-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Txn", "User");
        token = (String) bootstrap.get("token");
        workspaceId = UUID.fromString((String) bootstrap.get("lastWorkspaceId"));
        userId = UUID.fromString((String) bootstrap.get("id"));

        // Create an account
        var accountResp = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Checking","type":"CASH","subType":"CHECKING","balance":1000}
                        """, authHeaders(token)),
                Map.class);
        accountId = (String) accountResp.getBody().get("id");

        // Create a category
        var categoryResp = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries"}
                        """, authHeaders(token)),
                Map.class);
        categoryId = (String) categoryResp.getBody().get("id");

        // Create a tag
        var tagResp = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"weekly"}
                        """, authHeaders(token)),
                Map.class);
        tagId = (String) tagResp.getBody().get("id");

        // Create a recurring item
        var riResp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Netflix","categoryId":"%s","amount":-15.99,"currencyCode":"EUR","frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-01-15T00:00:00"],"startDate":"2025-01-01T00:00:00","tagIds":["%s"]}
                        """.formatted(accountId, categoryId, tagId), authHeaders(token)),
                Map.class);
        recurringItemId = (String) riResp.getBody().get("id");
        recurringMerchantId = (String) riResp.getBody().get("merchantId");
    }

    @Test
    void createTransaction_withExistingMerchant_returns201() {
        // Pre-create merchant
        restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Kroger"}
                        """, authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Kroger","categoryId":"%s","date":"2025-07-01T00:00:00","amount":-55.00,"notes":"Weekly groceries","tagIds":["%s"]}
                        """.formatted(accountId, categoryId, tagId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = response.getBody();
        assertThat(body.get("accountId")).isEqualTo(accountId);
        assertThat(body.get("merchantId")).isNotNull();
        assertThat(body.get("categoryId")).isEqualTo(categoryId);
        assertThat(body.get("amount")).isEqualTo(-55.0);
        assertThat(body.get("status")).isEqualTo("PENDING");
        assertThat(body.get("source")).isEqualTo("MANUAL");
        assertThat(body.get("pendingAt")).isNotNull();
        assertThat(body.get("notes")).isEqualTo("Weekly groceries");
        assertThat((List<?>) body.get("tagIds")).hasSize(1);
    }

    @Test
    void createTransaction_autoCreatesMerchant_returns201() {
        var response = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Brand New Store","date":"2025-07-01T00:00:00","amount":-10.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("merchantId")).isNotNull();

        // Verify the merchant was actually created
        var merchantsResp = restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                List.class);
        List<Map<String, Object>> merchants = merchantsResp.getBody();
        assertThat(merchants).extracting(m -> m.get("name")).contains("Brand New Store");
    }

    @Test
    void createTransaction_merchantLookup_caseInsensitive() {
        // Create "Kroger"
        restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Kroger"}
                        """, authHeaders(token)),
                Map.class);

        // Create transaction with "KROGER" — should find existing, not create new
        var response = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"KROGER","date":"2025-07-01T00:00:00","amount":-30.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify no duplicate merchant
        var merchantsResp = restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                List.class);
        long krogerCount = ((List<Map<String, Object>>) merchantsResp.getBody()).stream()
                .filter(m -> ((String) m.get("name")).equalsIgnoreCase("Kroger"))
                .count();
        assertThat(krogerCount).isEqualTo(1);
    }

    @Test
    void listTransactions_returnsAll() {
        // Create two transactions
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Store A","date":"2025-07-01T00:00:00","amount":-10.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Store B","date":"2025-07-02T00:00:00","amount":-20.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);
        assertThat(response.getBody()).containsKey("page");
    }

    @Test
    void getTransaction_byId_returns200() {
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Target","date":"2025-07-01T00:00:00","amount":-45.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("amount")).isEqualTo(-45.0);
    }

    @Test
    void updateTransaction_partialFields_returns200() {
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Target","date":"2025-07-01T00:00:00","amount":-45.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"amount":-50.00,"notes":"Updated amount"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("amount")).isEqualTo(-50.0);
        assertThat(response.getBody().get("notes")).isEqualTo("Updated amount");
    }

    @Test
    void updateTransaction_statusToPosted_setsPostedAt() {
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Target","date":"2025-07-01T00:00:00","amount":-45.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");
        assertThat(createResp.getBody().get("postedAt")).isNull();

        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"status":"POSTED"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("POSTED");
        assertThat(response.getBody().get("postedAt")).isNotNull();
    }

    @Test
    void updateTransaction_changeMerchant_autoCreatesNew() {
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"OldStore","date":"2025-07-01T00:00:00","amount":-10.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");
        String oldMerchantId = (String) createResp.getBody().get("merchantId");

        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"merchantName":"NewStore"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("merchantId")).isNotEqualTo(oldMerchantId);
    }

    @Test
    void updateTransaction_setCategoryToNull() {
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Store","categoryId":"%s","date":"2025-07-01T00:00:00","amount":-10.00}
                        """.formatted(accountId, categoryId), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"categoryId":null}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("categoryId")).isNull();
    }

    @Test
    void updateTransaction_setAndClearTags() {
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Store","date":"2025-07-01T00:00:00","amount":-10.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");

        // Set tags
        var setResp = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"tagIds":["%s"]}
                        """.formatted(tagId), authHeaders(token)),
                Map.class);
        assertThat((List<?>) setResp.getBody().get("tagIds")).hasSize(1);

        // Clear tags
        var clearResp = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"tagIds":[]}
                        """, authHeaders(token)),
                Map.class);
        assertThat((List<?>) clearResp.getBody().get("tagIds")).isEmpty();
    }

    @Test
    void deleteTransaction_returns204() {
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Store","date":"2025-07-01T00:00:00","amount":-10.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify gone
        var getResp = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getTransaction_notFound_returns404() {
        var response = restTemplate.exchange(
                "/api/v1/transactions/" + UUID.randomUUID(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void workspaceIsolation_cannotAccessOtherWorkspaceTransaction() {
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Store","date":"2025-07-01T00:00:00","amount":-10.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");

        // Switch to different workspace
        String token2 = tokenService.mintToken(userId, UUID.randomUUID());

        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token2)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createTransaction_accountNotInWorkspace_returns404() {
        var response = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Store","date":"2025-07-01T00:00:00","amount":-10.00}
                        """.formatted(UUID.randomUUID()), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("detail")).toString().contains("Account not found");
    }

    @Test
    void createTransaction_categoryNotInWorkspace_returns404() {
        var response = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Store","categoryId":"%s","date":"2025-07-01T00:00:00","amount":-10.00}
                        """.formatted(accountId, UUID.randomUUID()), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("detail")).toString().contains("Category not found");
    }

    @Test
    void createTransaction_categoryIsGroup_returns400() {
        // Create a parent category with a child
        var parentResp = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food Group"}
                        """, authHeaders(token)),
                Map.class);
        String parentCategoryId = (String) parentResp.getBody().get("id");

        restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Dining Out","parentId":"%s"}
                        """.formatted(parentCategoryId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Store","categoryId":"%s","date":"2025-07-01T00:00:00","amount":-10.00}
                        """.formatted(accountId, parentCategoryId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("detail")).contains("parent category group");
    }

    @Test
    void updateTransaction_categoryIsGroup_returns400() {
        // Create a transaction first
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Store","categoryId":"%s","date":"2025-07-01T00:00:00","amount":-10.00}
                        """.formatted(accountId, categoryId), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");

        // Create a parent category with a child
        var parentResp = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Bills Group"}
                        """, authHeaders(token)),
                Map.class);
        String parentCategoryId = (String) parentResp.getBody().get("id");

        restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Utilities","parentId":"%s"}
                        """.formatted(parentCategoryId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"categoryId":"%s"}
                        """.formatted(parentCategoryId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("detail")).contains("parent category group");
    }

    @Test
    void createTransaction_tagNotInWorkspace_returns404() {
        var response = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Store","date":"2025-07-01T00:00:00","amount":-10.00,"tagIds":["%s"]}
                        """.formatted(accountId, UUID.randomUUID()), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("detail")).toString().contains("Tag");
    }

    // --- recurringItemId inheritance ---

    @Test
    void createTransaction_withRecurringItemId_inheritsDefaults() {
        var response = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"recurringItemId":"%s","date":"2025-07-01T00:00:00","occurrenceDate":"2025-07-15"}
                        """.formatted(recurringItemId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = response.getBody();
        assertThat(body.get("recurringItemId")).isEqualTo(recurringItemId);
        assertThat(body.get("accountId")).isEqualTo(accountId);
        assertThat(body.get("merchantId")).isEqualTo(recurringMerchantId);
        assertThat(body.get("categoryId")).isEqualTo(categoryId);
        assertThat(body.get("amount")).isEqualTo(-15.99);
        assertThat(body.get("currencyCode")).isEqualTo("EUR");
        assertThat((List<String>) body.get("tagIds")).hasSize(1).contains(tagId);
        assertThat(body.get("occurrenceDate")).isEqualTo("2025-07-15");
    }

    @Test
    void createTransaction_withRecurringItemId_lockedFieldsFromRI() {
        // Even when DTO provides overrides for locked fields, RI values win
        var response = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"recurringItemId":"%s","accountId":"%s","merchantName":"OverrideStore","categoryId":null,"tagIds":[],"currencyCode":"USD","date":"2025-07-01T00:00:00","amount":-99.99,"occurrenceDate":"2025-07-15"}
                        """.formatted(recurringItemId, accountId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = response.getBody();
        // Locked fields come from RI, not DTO
        assertThat(body.get("accountId")).isEqualTo(accountId); // RI's account
        assertThat(body.get("merchantId")).isEqualTo(recurringMerchantId);
        assertThat(body.get("categoryId")).isEqualTo(categoryId);
        assertThat(body.get("currencyCode")).isEqualTo("EUR");
        assertThat((List<String>) body.get("tagIds")).hasSize(1).contains(tagId);
        // Amount is not locked — DTO wins
        assertThat(body.get("amount")).isEqualTo(-99.99);
    }

    @Test
    void createTransaction_noRecurringItem_noAccount_returns400() {
        var response = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"merchantName":"Store","date":"2025-07-01T00:00:00","amount":-10.00}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateTransaction_setRecurringItemId_inheritsDefaults() {
        // Create a plain transaction
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"SomeStore","date":"2025-07-01T00:00:00","amount":-10.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");
        assertThat(createResp.getBody().get("recurringItemId")).isNull();

        // Update with recurringItemId
        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"recurringItemId":"%s","occurrenceDate":"2025-07-15"}
                        """.formatted(recurringItemId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body.get("recurringItemId")).isEqualTo(recurringItemId);
        assertThat(body.get("occurrenceDate")).isEqualTo("2025-07-15");
        assertThat(body.get("accountId")).isEqualTo(accountId);
        assertThat(body.get("merchantId")).isEqualTo(recurringMerchantId);
        assertThat(body.get("categoryId")).isEqualTo(categoryId);
        assertThat(body.get("amount")).isEqualTo(-15.99);
        assertThat(body.get("currencyCode")).isEqualTo("EUR");
        assertThat((List<String>) body.get("tagIds")).hasSize(1).contains(tagId);
    }

    @Test
    void updateTransaction_clearRecurringItemId() {
        // Create transaction with recurring item
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"recurringItemId":"%s","date":"2025-07-01T00:00:00","occurrenceDate":"2025-07-15"}
                        """.formatted(recurringItemId), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");
        assertThat(createResp.getBody().get("recurringItemId")).isEqualTo(recurringItemId);

        // Clear recurringItemId
        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"recurringItemId":null}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("recurringItemId")).isNull();
        assertThat(response.getBody().get("occurrenceDate")).isNull();
    }

    // --- Field locking ---

    @Test
    void updateTransaction_recurringLinked_rejectsLockedFields() {
        // Create a transaction linked to recurring item
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"recurringItemId":"%s","date":"2025-07-01T00:00:00"}
                        """.formatted(recurringItemId), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");

        // Try to update categoryId — should fail
        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"categoryId":"%s"}
                        """.formatted(categoryId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateTransaction_recurringLinked_allowsAmountAndStatus() {
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"recurringItemId":"%s","date":"2025-07-01T00:00:00","occurrenceDate":"2025-07-15"}
                        """.formatted(recurringItemId), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");

        // Update amount and status — should succeed
        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"amount":-99.00,"status":"POSTED"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("amount")).isEqualTo(-99.0);
        assertThat(response.getBody().get("status")).isEqualTo("POSTED");
    }

    @Test
    void updateTransaction_recurringLinked_allowsUnlinkThenModify() {
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"recurringItemId":"%s","date":"2025-07-01T00:00:00","occurrenceDate":"2025-07-15"}
                        """.formatted(recurringItemId), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");

        // Unlink recurring item
        restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"recurringItemId":null}
                        """, authHeaders(token)),
                Map.class);

        // Now update categoryId — should succeed
        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"categoryId":"%s"}
                        """.formatted(categoryId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("categoryId")).isEqualTo(categoryId);
    }

    @Test
    void updateTransaction_grouped_rejectsLockedFields() {
        // Create 2 transactions
        var resp1 = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"PlaceA","date":"2025-07-01T00:00:00","amount":-10}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId1 = (String) resp1.getBody().get("id");
        var resp2 = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"PlaceB","date":"2025-07-01T00:00:00","amount":-20}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId2 = (String) resp2.getBody().get("id");

        // Group them
        restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s"]}
                        """.formatted(txnId1, txnId2), authHeaders(token)),
                Map.class);

        // Try to update notes on grouped transaction — should fail
        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId1, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"notes":"new notes"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateTransaction_grouped_rejectsLinkingRecurringItem() {
        var resp1 = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"PlaceA","date":"2025-07-01T00:00:00","amount":-10}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId1 = (String) resp1.getBody().get("id");
        var resp2 = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"PlaceB","date":"2025-07-01T00:00:00","amount":-20}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId2 = (String) resp2.getBody().get("id");

        // Group them
        restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s"]}
                        """.formatted(txnId1, txnId2), authHeaders(token)),
                Map.class);

        // Try to link to recurring item — should fail
        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId1, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"recurringItemId":"%s"}
                        """.formatted(recurringItemId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- Unlink from group via groupId: null ---

    @Test
    void updateTransaction_unlinkFromGroup_succeeds() {
        // Create 3 transactions and group them
        var resp1 = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"PlaceA","date":"2025-07-01T00:00:00","amount":-10}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId1 = (String) resp1.getBody().get("id");
        var resp2 = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"PlaceB","date":"2025-07-01T00:00:00","amount":-20}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId2 = (String) resp2.getBody().get("id");
        var resp3 = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"PlaceC","date":"2025-07-01T00:00:00","amount":-30}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId3 = (String) resp3.getBody().get("id");

        restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s","%s"]}
                        """.formatted(txnId1, txnId2, txnId3), authHeaders(token)),
                Map.class);

        // Unlink txn1 via groupId: null
        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId1, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"groupId":null}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("groupId")).isNull();
    }

    @Test
    void updateTransaction_unlinkFromGroup_rejectsWhenWouldLeaveFewerThan2() {
        // Create 2 transactions and group them (minimum group size)
        var resp1 = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"PlaceA","date":"2025-07-01T00:00:00","amount":-10}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId1 = (String) resp1.getBody().get("id");
        var resp2 = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"PlaceB","date":"2025-07-01T00:00:00","amount":-20}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId2 = (String) resp2.getBody().get("id");

        restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s"]}
                        """.formatted(txnId1, txnId2), authHeaders(token)),
                Map.class);

        // Try to unlink — would leave only 1
        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId1, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"groupId":null}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateTransaction_unlinkFromGroup_thenModifyLockedFields() {
        var resp1 = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"PlaceA","date":"2025-07-01T00:00:00","amount":-10}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId1 = (String) resp1.getBody().get("id");
        var resp2 = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"PlaceB","date":"2025-07-01T00:00:00","amount":-20}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId2 = (String) resp2.getBody().get("id");
        var resp3 = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"PlaceC","date":"2025-07-01T00:00:00","amount":-30}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId3 = (String) resp3.getBody().get("id");

        restTemplate.exchange(
                "/api/v1/transaction-groups", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Trip","transactionIds":["%s","%s","%s"]}
                        """.formatted(txnId1, txnId2, txnId3), authHeaders(token)),
                Map.class);

        // Unlink and update notes in same request
        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId1, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"groupId":null,"notes":"now free"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("groupId")).isNull();
        assertThat(response.getBody().get("notes")).isEqualTo("now free");
    }

    @Test
    void updateTransaction_assignGroupId_rejects() {
        var resp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"PlaceA","date":"2025-07-01T00:00:00","amount":-10}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String txnId = (String) resp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"groupId":"%s"}
                        """.formatted(UUID.randomUUID()), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- RI linking overwrites existing values ---

    @Test
    @SuppressWarnings("unchecked")
    void updateTransaction_linkRI_overwritesExistingLockedFields() {
        // Create a second category and tag to set on the transaction
        var cat2Resp = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Dining"}
                        """, authHeaders(token)),
                Map.class);
        String cat2Id = (String) cat2Resp.getBody().get("id");

        var tag2Resp = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"personal"}
                        """, authHeaders(token)),
                Map.class);
        String tag2Id = (String) tag2Resp.getBody().get("id");

        // Create transaction with its own category, tags, notes, and currencyCode
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"SomeStore","categoryId":"%s","tagIds":["%s"],"notes":"my notes","currencyCode":"GBP","date":"2025-07-01T00:00:00","amount":-10.00}
                        """.formatted(accountId, cat2Id, tag2Id), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");
        assertThat(createResp.getBody().get("categoryId")).isEqualTo(cat2Id);
        assertThat(createResp.getBody().get("currencyCode")).isEqualTo("GBP");

        // Link to recurring item — locked fields should be overwritten by RI values
        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"recurringItemId":"%s","occurrenceDate":"2025-07-15"}
                        """.formatted(recurringItemId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body.get("recurringItemId")).isEqualTo(recurringItemId);
        assertThat(body.get("accountId")).isEqualTo(accountId);
        assertThat(body.get("merchantId")).isEqualTo(recurringMerchantId);
        assertThat(body.get("categoryId")).isEqualTo(categoryId); // RI's category, not cat2
        assertThat(body.get("currencyCode")).isEqualTo("EUR"); // RI's currency, not GBP
        assertThat((List<String>) body.get("tagIds")).hasSize(1).contains(tagId); // RI's tag, not tag2
    }

    @Test
    void updateTransaction_recurringLinked_rejectsCurrencyCodeChange() {
        var createResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"recurringItemId":"%s","date":"2025-07-01T00:00:00"}
                        """.formatted(recurringItemId), authHeaders(token)),
                Map.class);
        String txnId = (String) createResp.getBody().get("id");

        // Try to change currencyCode — should fail
        var response = restTemplate.exchange(
                "/api/v1/transactions/" + txnId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"currencyCode":"GBP"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Pagination ───────────────────────────────────────────────────

    @Test
    void listTransactions_defaultPagination_returnsPageMetadata() {
        // Create 3 transactions
        for (int i = 1; i <= 3; i++) {
            restTemplate.exchange(
                    "/api/v1/transactions", HttpMethod.POST,
                    new HttpEntity<>("""
                            {"accountId":"%s","merchantName":"Store%d","date":"2025-07-0%dT00:00:00","amount":-%d.00}
                            """.formatted(accountId, i, i, i * 10), authHeaders(token)),
                    Map.class);
        }

        var response = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> page = (Map<String, Object>) response.getBody().get("page");
        assertThat(page.get("number")).isEqualTo(0);
        assertThat(page.get("size")).isEqualTo(25);
        assertThat((int) page.get("totalElements")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void listTransactions_customSize_respectsSize() {
        for (int i = 1; i <= 3; i++) {
            restTemplate.exchange(
                    "/api/v1/transactions", HttpMethod.POST,
                    new HttpEntity<>("""
                            {"accountId":"%s","merchantName":"PageStore%d","date":"2025-07-0%dT00:00:00","amount":-%d.00}
                            """.formatted(accountId, i, i, i * 10), authHeaders(token)),
                    Map.class);
        }

        var response = restTemplate.exchange(
                "/api/v1/transactions?size=2", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).hasSize(2);
        Map<String, Object> page = (Map<String, Object>) response.getBody().get("page");
        assertThat(page.get("size")).isEqualTo(2);
        assertThat((int) page.get("totalPages")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void listTransactions_page2_returnsDifferentResults() {
        for (int i = 1; i <= 3; i++) {
            restTemplate.exchange(
                    "/api/v1/transactions", HttpMethod.POST,
                    new HttpEntity<>("""
                            {"accountId":"%s","merchantName":"PageTwoStore%d","date":"2025-07-0%dT00:00:00","amount":-%d.00}
                            """.formatted(accountId, i, i, i * 10), authHeaders(token)),
                    Map.class);
        }

        var page0 = restTemplate.exchange(
                "/api/v1/transactions?size=1&page=0", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        var page1 = restTemplate.exchange(
                "/api/v1/transactions?size=1&page=1", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        List<?> content0 = (List<?>) page0.getBody().get("content");
        List<?> content1 = (List<?>) page1.getBody().get("content");
        assertThat(content0).hasSize(1);
        assertThat(content1).hasSize(1);
        // Different transactions on different pages
        String id0 = (String) ((Map<?, ?>) content0.get(0)).get("id");
        String id1 = (String) ((Map<?, ?>) content1.get(0)).get("id");
        assertThat(id0).isNotEqualTo(id1);
    }

    @Test
    void listTransactions_outOfRangePage_returnsEmptyContent() {
        var response = restTemplate.exchange(
                "/api/v1/transactions?page=9999", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).isEmpty();
    }

    @Test
    void listTransactions_sizeClamped_above250Returns250() {
        var response = restTemplate.exchange(
                "/api/v1/transactions?size=500", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> page = (Map<String, Object>) response.getBody().get("page");
        assertThat(page.get("size")).isEqualTo(250);
    }

    // ── Sorting ──────────────────────────────────────────────────────

    @Test
    void listTransactions_sortByDateAsc_returnsOldestFirst() {
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"OldStore","date":"2025-01-01T00:00:00","amount":-10.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"NewStore","date":"2025-12-01T00:00:00","amount":-20.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions?sortBy=date&sortDirection=ASC", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);
        // First should be older date
        String firstDate = (String) content.get(0).get("date");
        String lastDate = (String) content.get(content.size() - 1).get("date");
        assertThat(firstDate.compareTo(lastDate)).isLessThanOrEqualTo(0);
    }

    @Test
    void listTransactions_sortByAmountDesc_returnsLargestFirst() {
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"SmallStore","date":"2025-07-01T00:00:00","amount":-1.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"BigStore","date":"2025-07-02T00:00:00","amount":-999.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions?sortBy=amount&sortDirection=DESC", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);
        double first = ((Number) content.get(0).get("amount")).doubleValue();
        double last = ((Number) content.get(content.size() - 1).get("amount")).doubleValue();
        assertThat(first).isGreaterThanOrEqualTo(last);
    }

    @Test
    void listTransactions_sortByCategory_sortsByCategoryName() {
        // Create categories with known alphabetical order
        var catA = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"AAA Category"}
                        """, authHeaders(token)),
                Map.class);
        String catAId = (String) catA.getBody().get("id");

        var catZ = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"ZZZ Category"}
                        """, authHeaders(token)),
                Map.class);
        String catZId = (String) catZ.getBody().get("id");

        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"CatStoreZ","date":"2025-07-01T00:00:00","amount":-10.00,"categoryId":"%s"}
                        """.formatted(accountId, catZId), authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"CatStoreA","date":"2025-07-02T00:00:00","amount":-20.00,"categoryId":"%s"}
                        """.formatted(accountId, catAId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions?sortBy=category&sortDirection=ASC", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void listTransactions_sortByMerchant_sortsByMerchantName() {
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Zebra Market","date":"2025-07-01T00:00:00","amount":-10.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Apple Store","date":"2025-07-02T00:00:00","amount":-20.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions?sortBy=merchant&sortDirection=ASC", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);
    }

    // ── Date Range Filters ───────────────────────────────────────────

    @Test
    void listTransactions_startDateFilter_returnsOnOrAfter() {
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"EarlyStore","date":"2024-01-01T00:00:00","amount":-10.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"LateStore","date":"2025-12-01T00:00:00","amount":-20.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions?startDate=2025-06-01", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        for (var txn : content) {
            assertThat(txn.get("date").toString()).isGreaterThanOrEqualTo("2025-06-01");
        }
    }

    @Test
    void listTransactions_endDateFilter_returnsOnOrBefore() {
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"FutureStore","date":"2026-12-01T00:00:00","amount":-10.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"PastStore","date":"2024-06-01T00:00:00","amount":-20.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions?endDate=2025-01-01", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        for (var txn : content) {
            assertThat(txn.get("date").toString()).isLessThanOrEqualTo("2025-01-01T23:59:59");
        }
    }

    @Test
    void listTransactions_dateRange_returnsWithinRange() {
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"InRangeStore","date":"2025-06-15T00:00:00","amount":-10.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"OutOfRangeStore","date":"2024-01-01T00:00:00","amount":-20.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions?startDate=2025-06-01&endDate=2025-07-01", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        for (var txn : content) {
            assertThat(txn.get("date").toString()).isGreaterThanOrEqualTo("2025-06-01");
            assertThat(txn.get("date").toString()).isLessThanOrEqualTo("2025-07-01T23:59:59");
        }
    }

    // ── Amount Range Filters ─────────────────────────────────────────

    @Test
    void listTransactions_minAmountFilter_returnsAboveMin() {
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"CheapStore","date":"2025-07-01T00:00:00","amount":-1.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"ExpensiveStore","date":"2025-07-02T00:00:00","amount":-500.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions?minAmount=-100", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        for (var txn : content) {
            double amount = ((Number) txn.get("amount")).doubleValue();
            assertThat(amount).isGreaterThanOrEqualTo(-100.0);
        }
    }

    @Test
    void listTransactions_maxAmountFilter_returnsBelowMax() {
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"TinyStore","date":"2025-07-01T00:00:00","amount":-5.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions?maxAmount=-2", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        for (var txn : content) {
            double amount = ((Number) txn.get("amount")).doubleValue();
            assertThat(amount).isLessThanOrEqualTo(-2.0);
        }
    }

    // ── Search ───────────────────────────────────────────────────────

    @Test
    void listTransactions_searchByNotes_matchesNotesField() {
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"SomeStore","date":"2025-07-01T00:00:00","amount":-10.00,"notes":"unicorn sparkle purchase"}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions?search=unicorn sparkle", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        assertThat((String) content.get(0).get("notes")).containsIgnoringCase("unicorn sparkle");
    }

    @Test
    void listTransactions_searchByMerchantName_matchesMerchant() {
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"XylophoneEmporium","date":"2025-07-01T00:00:00","amount":-10.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions?search=XylophoneEmpor", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
    }

    @Test
    void listTransactions_searchByCategoryName_matchesCategory() {
        var catResp = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Qwerty Utilities"}
                        """, authHeaders(token)),
                Map.class);
        String qCatId = (String) catResp.getBody().get("id");

        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"UtilStore","date":"2025-07-01T00:00:00","amount":-10.00,"categoryId":"%s"}
                        """.formatted(accountId, qCatId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions?search=Qwerty", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
    }

    @Test
    void listTransactions_searchByAmount_matchesAmountSubstring() {
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"AmountSearchStore","date":"2025-07-01T00:00:00","amount":-829.47}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions?search=829", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        assertThat(content).allSatisfy(t -> {
            String amount = String.valueOf(t.get("amount"));
            assertThat(amount).contains("829");
        });
    }

    @Test
    void listTransactions_searchNoMatch_returnsEmpty() {
        var response = restTemplate.exchange(
                "/api/v1/transactions?search=zzz_no_match_xyz_9999", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).isEmpty();
    }

    // ── Combined Filters ─────────────────────────────────────────────

    @Test
    void listTransactions_combinedFiltersAndPagination_work() {
        // Create transactions with distinct dates and amounts
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"ComboStore1","date":"2025-08-01T00:00:00","amount":-50.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"ComboStore2","date":"2025-08-15T00:00:00","amount":-100.00}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/transactions?startDate=2025-08-01&endDate=2025-08-31&sortBy=amount&sortDirection=ASC&size=10", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);
        Map<String, Object> page = (Map<String, Object>) response.getBody().get("page");
        assertThat(page.get("size")).isEqualTo(10);
    }
}
