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
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(2);
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
}
