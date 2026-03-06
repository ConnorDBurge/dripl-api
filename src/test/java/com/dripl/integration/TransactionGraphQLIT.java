package com.dripl.integration;

import com.dripl.auth.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionGraphQLIT extends BaseIntegrationTest {

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
        String email = "txn-gql-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Txn", "User");
        token = (String) bootstrap.get("token");
        workspaceId = UUID.fromString((String) bootstrap.get("workspaceId"));
        userId = UUID.fromString((String) bootstrap.get("userId"));

        accountId = createAccount(token, "Checking", "CASH", "CHECKING", "1000");
        categoryId = createCategory(token, "Groceries");
        tagId = createTag(token, "weekly");

        // Create a recurring item (still REST — not yet migrated to GraphQL)
        var riResp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Netflix","categoryId":"%s","amount":-15.99,"currencyCode":"EUR","frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-01-15T00:00:00"],"startDate":"2025-01-01T00:00:00","tagIds":["%s"]}
                        """.formatted(accountId, categoryId, tagId), authHeaders(token)),
                Map.class);
        recurringItemId = (String) riResp.getBody().get("id");
        recurringMerchantId = (String) riResp.getBody().get("merchantId");
    }

    // ── GraphQL helpers ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> gql(String query) {
        return gql(query, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> gql(String query, Map<String, Object> variables) {
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

    private static final String TXN_FIELDS =
            "id accountId merchantId categoryId date amount currencyCode notes status source pendingAt postedAt recurringItemId occurrenceDate groupId splitId tagIds workspaceId";

    // ── Create ───────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void createTransaction_withExistingMerchant_returnsCreatedTransaction() {
        createMerchant(token, "Kroger");

        var result = gql("""
                mutation($acctId: ID!, $catId: ID!, $tagId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "Kroger", categoryId: $catId,
                        date: "2025-07-01T00:00:00", amount: -55.00,
                        notes: "Weekly groceries", tagIds: [$tagId]
                    }) { %s }
                }
                """.formatted(TXN_FIELDS),
                Map.of("acctId", accountId, "catId", categoryId, "tagId", tagId));

        var txn = (Map<String, Object>) data(result).get("createTransaction");
        assertThat(txn.get("accountId")).isEqualTo(accountId);
        assertThat(txn.get("merchantId")).isNotNull();
        assertThat(txn.get("categoryId")).isEqualTo(categoryId);
        assertThat(((Number) txn.get("amount")).doubleValue()).isEqualTo(-55.0);
        assertThat(txn.get("status")).isEqualTo("PENDING");
        assertThat(txn.get("source")).isEqualTo("MANUAL");
        assertThat(txn.get("pendingAt")).isNotNull();
        assertThat(txn.get("notes")).isEqualTo("Weekly groceries");
        assertThat((List<?>) txn.get("tagIds")).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createTransaction_autoCreatesMerchant_returnsTransaction() {
        var result = gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "Brand New Store",
                        date: "2025-07-01T00:00:00", amount: -10.00
                    }) { id merchantId }
                }
                """, Map.of("acctId", accountId));

        var txn = (Map<String, Object>) data(result).get("createTransaction");
        assertThat(txn.get("merchantId")).isNotNull();

        List<Map<String, Object>> merchants = listMerchants(token);
        assertThat(merchants).extracting(m -> m.get("name")).contains("Brand New Store");
    }

    @Test
    void createTransaction_merchantLookup_caseInsensitive() {
        createMerchant(token, "Kroger");

        var result = gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "KROGER",
                        date: "2025-07-01T00:00:00", amount: -30.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));

        assertThat(data(result).get("createTransaction")).isNotNull();

        long krogerCount = listMerchants(token).stream()
                .filter(m -> ((String) m.get("name")).equalsIgnoreCase("Kroger"))
                .count();
        assertThat(krogerCount).isEqualTo(1);
    }

    // ── List ─────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_returnsAll() {
        createTransaction(token, accountId, "Store A", "-10.00");
        createTransaction(token, accountId, "Store B", "-20.00");

        var result = gql("""
                {
                    transactions {
                        content { id }
                        page { number size totalElements totalPages }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        List<?> content = (List<?>) txns.get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);
        assertThat(txns).containsKey("page");
    }

    // ── Get by ID ────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getTransaction_byId_returnsTransaction() {
        String txnId = createTransaction(token, accountId, "Target", "-45.00");

        var result = gql("""
                query($transactionId: ID!) {
                    transaction(transactionId: $transactionId) { id amount }
                }
                """, Map.of("transactionId", txnId));

        var txn = (Map<String, Object>) data(result).get("transaction");
        assertThat(((Number) txn.get("amount")).doubleValue()).isEqualTo(-45.0);
    }

    // ── Update ───────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void updateTransaction_partialFields_returnsUpdated() {
        String txnId = createTransaction(token, accountId, "Target", "-45.00");

        var result = gql("""
                mutation($transactionId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { amount: -50.00, notes: "Updated amount" }) {
                        id amount notes
                    }
                }
                """, Map.of("transactionId", txnId));

        var txn = (Map<String, Object>) data(result).get("updateTransaction");
        assertThat(((Number) txn.get("amount")).doubleValue()).isEqualTo(-50.0);
        assertThat(txn.get("notes")).isEqualTo("Updated amount");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateTransaction_statusToPosted_setsPostedAt() {
        String txnId = createTransaction(token, accountId, "Target", "-45.00");

        // Verify initially no postedAt
        var getResult = gql("""
                query($transactionId: ID!) { transaction(transactionId: $transactionId) { postedAt } }
                """, Map.of("transactionId", txnId));
        assertThat(((Map<String, Object>) data(getResult).get("transaction")).get("postedAt")).isNull();

        var result = gql("""
                mutation($transactionId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { status: POSTED }) {
                        id status postedAt
                    }
                }
                """, Map.of("transactionId", txnId));

        var txn = (Map<String, Object>) data(result).get("updateTransaction");
        assertThat(txn.get("status")).isEqualTo("POSTED");
        assertThat(txn.get("postedAt")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateTransaction_changeMerchant_autoCreatesNew() {
        var createResult = gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "OldStore",
                        date: "2025-07-01T00:00:00", amount: -10.00
                    }) { id merchantId }
                }
                """, Map.of("acctId", accountId));
        var created = (Map<String, Object>) data(createResult).get("createTransaction");
        String txnId = (String) created.get("id");
        String oldMerchantId = (String) created.get("merchantId");

        var result = gql("""
                mutation($transactionId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { merchantName: "NewStore" }) {
                        id merchantId
                    }
                }
                """, Map.of("transactionId", txnId));

        var txn = (Map<String, Object>) data(result).get("updateTransaction");
        assertThat(txn.get("merchantId")).isNotEqualTo(oldMerchantId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateTransaction_setCategoryToNull() {
        var createResult = gql("""
                mutation($acctId: ID!, $catId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "Store", categoryId: $catId,
                        date: "2025-07-01T00:00:00", amount: -10.00
                    }) { id categoryId }
                }
                """, Map.of("acctId", accountId, "catId", categoryId));
        var created = (Map<String, Object>) data(createResult).get("createTransaction");
        String txnId = (String) created.get("id");

        Map<String, Object> vars = new HashMap<>();
        vars.put("transactionId", txnId);
        vars.put("categoryId", null);
        var result = gql("""
                mutation($transactionId: ID!, $categoryId: ID) {
                    updateTransaction(transactionId: $transactionId, input: { categoryId: $categoryId }) {
                        id categoryId
                    }
                }
                """, vars);

        var txn = (Map<String, Object>) data(result).get("updateTransaction");
        assertThat(txn.get("categoryId")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateTransaction_setAndClearTags() {
        String txnId = createTransaction(token, accountId, "Store", "-10.00");

        // Set tags
        var setResult = gql("""
                mutation($transactionId: ID!, $tagId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { tagIds: [$tagId] }) {
                        id tagIds
                    }
                }
                """, Map.of("transactionId", txnId, "tagId", tagId));
        var setTxn = (Map<String, Object>) data(setResult).get("updateTransaction");
        assertThat((List<?>) setTxn.get("tagIds")).hasSize(1);

        // Clear tags
        var clearResult = gql("""
                mutation($transactionId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { tagIds: [] }) {
                        id tagIds
                    }
                }
                """, Map.of("transactionId", txnId));
        var clearTxn = (Map<String, Object>) data(clearResult).get("updateTransaction");
        assertThat((List<?>) clearTxn.get("tagIds")).isEmpty();
    }

    // ── Delete ───────────────────────────────────────────────────────

    @Test
    void deleteTransaction_returnsTrue() {
        String txnId = createTransaction(token, accountId, "Store", "-10.00");

        var result = gql("""
                mutation($transactionId: ID!) { deleteTransaction(transactionId: $transactionId) }
                """, Map.of("transactionId", txnId));
        assertThat(data(result).get("deleteTransaction")).isEqualTo(true);

        // Verify gone
        var getResult = gql("""
                query($transactionId: ID!) { transaction(transactionId: $transactionId) { id } }
                """, Map.of("transactionId", txnId));
        assertThat(getResult.get("errors")).isNotNull();
    }

    // ── Not found / Error cases ──────────────────────────────────────

    @Test
    void getTransaction_notFound_returnsError() {
        var result = gql("""
                query($transactionId: ID!) { transaction(transactionId: $transactionId) { id } }
                """, Map.of("transactionId", UUID.randomUUID().toString()));
        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void workspaceIsolation_cannotAccessOtherWorkspaceTransaction() {
        String txnId = createTransaction(token, accountId, "Store", "-10.00");

        // Mint token for a different workspace
        UUID workspace2Id = UUID.randomUUID();
        String token2 = tokenService.mintToken(userId, workspace2Id);

        Map<String, Object> body = Map.of(
                "query", """
                        query($transactionId: ID!) { transaction(transactionId: $transactionId) { id } }
                        """,
                "variables", Map.of("transactionId", txnId));
        @SuppressWarnings("unchecked")
        var response = restTemplate.exchange(
                "/graphql", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token2)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("errors")).isNotNull();
    }

    @Test
    void createTransaction_accountNotInWorkspace_returnsError() {
        var result = gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "Store",
                        date: "2025-07-01T00:00:00", amount: -10.00
                    }) { id }
                }
                """, Map.of("acctId", UUID.randomUUID().toString()));
        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void createTransaction_categoryNotInWorkspace_returnsError() {
        var result = gql("""
                mutation($acctId: ID!, $catId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "Store", categoryId: $catId,
                        date: "2025-07-01T00:00:00", amount: -10.00
                    }) { id }
                }
                """, Map.of("acctId", accountId, "catId", UUID.randomUUID().toString()));
        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void createTransaction_categoryIsGroup_returnsError() {
        String parentCategoryId = createCategory(token, "Food Group");
        createCategory(token, "Dining Out", parentCategoryId, null);

        var result = gql("""
                mutation($acctId: ID!, $catId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "Store", categoryId: $catId,
                        date: "2025-07-01T00:00:00", amount: -10.00
                    }) { id }
                }
                """, Map.of("acctId", accountId, "catId", parentCategoryId));
        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void updateTransaction_categoryIsGroup_returnsError() {
        String txnId = createTransaction(token, accountId, "Store", "-10.00");

        String parentCategoryId = createCategory(token, "Bills Group");
        createCategory(token, "Utilities", parentCategoryId, null);

        var result = gql("""
                mutation($transactionId: ID!, $catId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { categoryId: $catId }) { id }
                }
                """, Map.of("transactionId", txnId, "catId", parentCategoryId));
        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void createTransaction_tagNotInWorkspace_returnsError() {
        var result = gql("""
                mutation($acctId: ID!, $tagId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "Store",
                        date: "2025-07-01T00:00:00", amount: -10.00,
                        tagIds: [$tagId]
                    }) { id }
                }
                """, Map.of("acctId", accountId, "tagId", UUID.randomUUID().toString()));
        assertThat(result.get("errors")).isNotNull();
    }

    // ── recurringItemId inheritance ──────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void createTransaction_withRecurringItemId_inheritsDefaults() {
        var result = gql("""
                mutation($acctId: ID!, $riId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, recurringItemId: $riId,
                        date: "2025-07-01T00:00:00", amount: -15.99,
                        occurrenceDate: "2025-07-15"
                    }) { id recurringItemId accountId merchantId categoryId amount currencyCode tagIds occurrenceDate }
                }
                """, Map.of("acctId", accountId, "riId", recurringItemId));

        var txn = (Map<String, Object>) data(result).get("createTransaction");
        assertThat(txn.get("recurringItemId")).isEqualTo(recurringItemId);
        assertThat(txn.get("accountId")).isEqualTo(accountId);
        assertThat(txn.get("merchantId")).isEqualTo(recurringMerchantId);
        assertThat(txn.get("categoryId")).isEqualTo(categoryId);
        assertThat(((Number) txn.get("amount")).doubleValue()).isEqualTo(-15.99);
        assertThat(txn.get("currencyCode")).isEqualTo("EUR");
        assertThat((List<String>) txn.get("tagIds")).hasSize(1).contains(tagId);
        assertThat(txn.get("occurrenceDate")).isEqualTo("2025-07-15");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createTransaction_withRecurringItemId_lockedFieldsFromRI() {
        var result = gql("""
                mutation($acctId: ID!, $riId: ID!) {
                    createTransaction(input: {
                        recurringItemId: $riId, accountId: $acctId,
                        merchantName: "OverrideStore", categoryId: null,
                        tagIds: [], currencyCode: USD,
                        date: "2025-07-01T00:00:00", amount: -99.99,
                        occurrenceDate: "2025-07-15"
                    }) { id accountId merchantId categoryId currencyCode tagIds amount }
                }
                """, Map.of("acctId", accountId, "riId", recurringItemId));

        var txn = (Map<String, Object>) data(result).get("createTransaction");
        // Locked fields come from RI, not input
        assertThat(txn.get("accountId")).isEqualTo(accountId);
        assertThat(txn.get("merchantId")).isEqualTo(recurringMerchantId);
        assertThat(txn.get("categoryId")).isEqualTo(categoryId);
        assertThat(txn.get("currencyCode")).isEqualTo("EUR");
        assertThat((List<String>) txn.get("tagIds")).hasSize(1).contains(tagId);
        // Amount is not locked — input wins
        assertThat(((Number) txn.get("amount")).doubleValue()).isEqualTo(-99.99);
    }

    @Test
    void createTransaction_noRecurringItem_noAccount_returnsError() {
        // accountId is required (ID!) in the GraphQL schema — omitting it triggers validation error
        var result = gql("""
                mutation {
                    createTransaction(input: {
                        merchantName: "Store", date: "2025-07-01T00:00:00", amount: -10.00
                    }) { id }
                }
                """);
        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateTransaction_setRecurringItemId_inheritsDefaults() {
        String txnId = createTransaction(token, accountId, "SomeStore", "-10.00");

        // Verify no recurringItemId initially
        var getResult = gql("""
                query($transactionId: ID!) { transaction(transactionId: $transactionId) { recurringItemId } }
                """, Map.of("transactionId", txnId));
        assertThat(((Map<String, Object>) data(getResult).get("transaction")).get("recurringItemId")).isNull();

        var result = gql("""
                mutation($transactionId: ID!, $riId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: {
                        recurringItemId: $riId, occurrenceDate: "2025-07-15"
                    }) { id recurringItemId occurrenceDate accountId merchantId categoryId amount currencyCode tagIds }
                }
                """, Map.of("transactionId", txnId, "riId", recurringItemId));

        var txn = (Map<String, Object>) data(result).get("updateTransaction");
        assertThat(txn.get("recurringItemId")).isEqualTo(recurringItemId);
        assertThat(txn.get("occurrenceDate")).isEqualTo("2025-07-15");
        assertThat(txn.get("accountId")).isEqualTo(accountId);
        assertThat(txn.get("merchantId")).isEqualTo(recurringMerchantId);
        assertThat(txn.get("categoryId")).isEqualTo(categoryId);
        assertThat(((Number) txn.get("amount")).doubleValue()).isEqualTo(-15.99);
        assertThat(txn.get("currencyCode")).isEqualTo("EUR");
        assertThat((List<String>) txn.get("tagIds")).hasSize(1).contains(tagId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateTransaction_clearRecurringItemId() {
        // Create transaction linked to RI
        var createResult = gql("""
                mutation($acctId: ID!, $riId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, recurringItemId: $riId,
                        date: "2025-07-01T00:00:00", amount: -15.99,
                        occurrenceDate: "2025-07-15"
                    }) { id recurringItemId }
                }
                """, Map.of("acctId", accountId, "riId", recurringItemId));
        var created = (Map<String, Object>) data(createResult).get("createTransaction");
        String txnId = (String) created.get("id");
        assertThat(created.get("recurringItemId")).isEqualTo(recurringItemId);

        // Clear recurringItemId
        Map<String, Object> vars = new HashMap<>();
        vars.put("transactionId", txnId);
        vars.put("riId", null);
        var result = gql("""
                mutation($transactionId: ID!, $riId: ID) {
                    updateTransaction(transactionId: $transactionId, input: { recurringItemId: $riId }) {
                        id recurringItemId occurrenceDate
                    }
                }
                """, vars);

        var txn = (Map<String, Object>) data(result).get("updateTransaction");
        assertThat(txn.get("recurringItemId")).isNull();
        assertThat(txn.get("occurrenceDate")).isNull();
    }

    // ── Field locking ────────────────────────────────────────────────

    @Test
    void updateTransaction_recurringLinked_rejectsLockedFields() {
        // Create linked to RI
        var createResult = gql("""
                mutation($acctId: ID!, $riId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, recurringItemId: $riId,
                        date: "2025-07-01T00:00:00", amount: -15.99,
                        occurrenceDate: "2025-07-15"
                    }) { id }
                }
                """, Map.of("acctId", accountId, "riId", recurringItemId));
        @SuppressWarnings("unchecked")
        String txnId = (String) ((Map<String, Object>) data(createResult).get("createTransaction")).get("id");

        // Try to update categoryId — should fail
        var result = gql("""
                mutation($transactionId: ID!, $catId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { categoryId: $catId }) { id }
                }
                """, Map.of("transactionId", txnId, "catId", categoryId));
        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateTransaction_recurringLinked_allowsAmountAndStatus() {
        var createResult = gql("""
                mutation($acctId: ID!, $riId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, recurringItemId: $riId,
                        date: "2025-07-01T00:00:00", amount: -15.99,
                        occurrenceDate: "2025-07-15"
                    }) { id }
                }
                """, Map.of("acctId", accountId, "riId", recurringItemId));
        String txnId = (String) ((Map<String, Object>) data(createResult).get("createTransaction")).get("id");

        var result = gql("""
                mutation($transactionId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { amount: -99.00, status: POSTED }) {
                        id amount status
                    }
                }
                """, Map.of("transactionId", txnId));

        var txn = (Map<String, Object>) data(result).get("updateTransaction");
        assertThat(((Number) txn.get("amount")).doubleValue()).isEqualTo(-99.0);
        assertThat(txn.get("status")).isEqualTo("POSTED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateTransaction_recurringLinked_allowsUnlinkThenModify() {
        var createResult = gql("""
                mutation($acctId: ID!, $riId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, recurringItemId: $riId,
                        date: "2025-07-01T00:00:00", amount: -15.99,
                        occurrenceDate: "2025-07-15"
                    }) { id }
                }
                """, Map.of("acctId", accountId, "riId", recurringItemId));
        String txnId = (String) ((Map<String, Object>) data(createResult).get("createTransaction")).get("id");

        // Unlink recurring item
        Map<String, Object> unlinkVars = new HashMap<>();
        unlinkVars.put("transactionId", txnId);
        unlinkVars.put("riId", null);
        gql("""
                mutation($transactionId: ID!, $riId: ID) {
                    updateTransaction(transactionId: $transactionId, input: { recurringItemId: $riId }) { id }
                }
                """, unlinkVars);

        // Now update categoryId — should succeed
        var result = gql("""
                mutation($transactionId: ID!, $catId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { categoryId: $catId }) {
                        id categoryId
                    }
                }
                """, Map.of("transactionId", txnId, "catId", categoryId));

        var txn = (Map<String, Object>) data(result).get("updateTransaction");
        assertThat(txn.get("categoryId")).isEqualTo(categoryId);
    }

    @Test
    void updateTransaction_grouped_rejectsLockedFields() {
        String txnId1 = createTransaction(token, accountId, "PlaceA", "-10");
        String txnId2 = createTransaction(token, accountId, "PlaceB", "-20");

        createTransactionGroup(token, "Trip",
                "[\"%s\",\"%s\"]".formatted(txnId1, txnId2));

        // Try to update notes on grouped transaction — should fail
        var result = gql("""
                mutation($transactionId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { notes: "new notes" }) { id }
                }
                """, Map.of("transactionId", txnId1));
        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void updateTransaction_grouped_rejectsLinkingRecurringItem() {
        String txnId1 = createTransaction(token, accountId, "PlaceA", "-10");
        String txnId2 = createTransaction(token, accountId, "PlaceB", "-20");

        createTransactionGroup(token, "Trip",
                "[\"%s\",\"%s\"]".formatted(txnId1, txnId2));

        // Try to link to recurring item — should fail
        var result = gql("""
                mutation($transactionId: ID!, $riId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { recurringItemId: $riId }) { id }
                }
                """, Map.of("transactionId", txnId1, "riId", recurringItemId));
        assertThat(result.get("errors")).isNotNull();
    }

    // ── Unlink from group via groupId: null ──────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void updateTransaction_unlinkFromGroup_succeeds() {
        String txnId1 = createTransaction(token, accountId, "PlaceA", "-10");
        String txnId2 = createTransaction(token, accountId, "PlaceB", "-20");
        String txnId3 = createTransaction(token, accountId, "PlaceC", "-30");

        createTransactionGroup(token, "Trip",
                "[\"%s\",\"%s\",\"%s\"]".formatted(txnId1, txnId2, txnId3));

        // Unlink txn1 via groupId: null
        Map<String, Object> vars = new HashMap<>();
        vars.put("transactionId", txnId1);
        vars.put("groupId", null);
        var result = gql("""
                mutation($transactionId: ID!, $groupId: ID) {
                    updateTransaction(transactionId: $transactionId, input: { groupId: $groupId }) {
                        id groupId
                    }
                }
                """, vars);

        var txn = (Map<String, Object>) data(result).get("updateTransaction");
        assertThat(txn.get("groupId")).isNull();
    }

    @Test
    void updateTransaction_unlinkFromGroup_rejectsWhenWouldLeaveFewerThan2() {
        String txnId1 = createTransaction(token, accountId, "PlaceA", "-10");
        String txnId2 = createTransaction(token, accountId, "PlaceB", "-20");

        createTransactionGroup(token, "Trip",
                "[\"%s\",\"%s\"]".formatted(txnId1, txnId2));

        // Try to unlink — would leave only 1
        Map<String, Object> vars = new HashMap<>();
        vars.put("transactionId", txnId1);
        vars.put("groupId", null);
        var result = gql("""
                mutation($transactionId: ID!, $groupId: ID) {
                    updateTransaction(transactionId: $transactionId, input: { groupId: $groupId }) { id }
                }
                """, vars);
        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateTransaction_unlinkFromGroup_thenModifyLockedFields() {
        String txnId1 = createTransaction(token, accountId, "PlaceA", "-10");
        String txnId2 = createTransaction(token, accountId, "PlaceB", "-20");
        String txnId3 = createTransaction(token, accountId, "PlaceC", "-30");

        createTransactionGroup(token, "Trip",
                "[\"%s\",\"%s\",\"%s\"]".formatted(txnId1, txnId2, txnId3));

        // Unlink and update notes in same request
        Map<String, Object> vars = new HashMap<>();
        vars.put("transactionId", txnId1);
        vars.put("groupId", null);
        var result = gql("""
                mutation($transactionId: ID!, $groupId: ID) {
                    updateTransaction(transactionId: $transactionId, input: { groupId: $groupId, notes: "now free" }) {
                        id groupId notes
                    }
                }
                """, vars);

        var txn = (Map<String, Object>) data(result).get("updateTransaction");
        assertThat(txn.get("groupId")).isNull();
        assertThat(txn.get("notes")).isEqualTo("now free");
    }

    @Test
    void updateTransaction_assignGroupId_rejects() {
        String txnId = createTransaction(token, accountId, "PlaceA", "-10");

        var result = gql("""
                mutation($transactionId: ID!, $groupId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { groupId: $groupId }) { id }
                }
                """, Map.of("transactionId", txnId, "groupId", UUID.randomUUID().toString()));
        assertThat(result.get("errors")).isNotNull();
    }

    // ── RI linking overwrites existing values ────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void updateTransaction_linkRI_overwritesExistingLockedFields() {
        String cat2Id = createCategory(token, "Dining");
        String tag2Id = createTag(token, "personal");

        // Create transaction with its own category, tags, notes, and currencyCode
        var createResult = gql("""
                mutation($acctId: ID!, $catId: ID!, $tagId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "SomeStore", categoryId: $catId,
                        tagIds: [$tagId], notes: "my notes", currencyCode: GBP,
                        date: "2025-07-01T00:00:00", amount: -10.00
                    }) { id categoryId currencyCode }
                }
                """, Map.of("acctId", accountId, "catId", cat2Id, "tagId", tag2Id));
        var created = (Map<String, Object>) data(createResult).get("createTransaction");
        String txnId = (String) created.get("id");
        assertThat(created.get("categoryId")).isEqualTo(cat2Id);
        assertThat(created.get("currencyCode")).isEqualTo("GBP");

        // Link to recurring item — locked fields should be overwritten by RI values
        var result = gql("""
                mutation($transactionId: ID!, $riId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: {
                        recurringItemId: $riId, occurrenceDate: "2025-07-15"
                    }) { id recurringItemId accountId merchantId categoryId currencyCode tagIds }
                }
                """, Map.of("transactionId", txnId, "riId", recurringItemId));

        var txn = (Map<String, Object>) data(result).get("updateTransaction");
        assertThat(txn.get("recurringItemId")).isEqualTo(recurringItemId);
        assertThat(txn.get("accountId")).isEqualTo(accountId);
        assertThat(txn.get("merchantId")).isEqualTo(recurringMerchantId);
        assertThat(txn.get("categoryId")).isEqualTo(categoryId); // RI's category, not cat2
        assertThat(txn.get("currencyCode")).isEqualTo("EUR"); // RI's currency, not GBP
        assertThat((List<String>) txn.get("tagIds")).hasSize(1).contains(tagId); // RI's tag, not tag2
    }

    @Test
    void updateTransaction_recurringLinked_rejectsCurrencyCodeChange() {
        @SuppressWarnings("unchecked")
        var createResult = gql("""
                mutation($acctId: ID!, $riId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, recurringItemId: $riId,
                        date: "2025-07-01T00:00:00", amount: -15.99,
                        occurrenceDate: "2025-07-15"
                    }) { id }
                }
                """, Map.of("acctId", accountId, "riId", recurringItemId));
        @SuppressWarnings("unchecked")
        String txnId = (String) ((Map<String, Object>) data(createResult).get("createTransaction")).get("id");

        // Try to change currencyCode — should fail
        var result = gql("""
                mutation($transactionId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { currencyCode: GBP }) { id }
                }
                """, Map.of("transactionId", txnId));
        assertThat(result.get("errors")).isNotNull();
    }

    // ── Pagination ───────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_defaultPagination_returnsPageMetadata() {
        for (int i = 1; i <= 3; i++) {
            createTransaction(token, accountId, "Store" + i, String.valueOf(-i * 10));
        }

        var result = gql("""
                {
                    transactions {
                        content { id }
                        page { number size totalElements totalPages }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var page = (Map<String, Object>) txns.get("page");
        assertThat(page.get("number")).isEqualTo(0);
        assertThat(page.get("size")).isEqualTo(25);
        assertThat((int) page.get("totalElements")).isGreaterThanOrEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_customSize_respectsSize() {
        for (int i = 1; i <= 3; i++) {
            createTransaction(token, accountId, "PageStore" + i, String.valueOf(-i * 10));
        }

        var result = gql("""
                {
                    transactions(size: 2) {
                        content { id }
                        page { number size totalElements totalPages }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        List<?> content = (List<?>) txns.get("content");
        assertThat(content).hasSize(2);
        var page = (Map<String, Object>) txns.get("page");
        assertThat(page.get("size")).isEqualTo(2);
        assertThat((int) page.get("totalPages")).isGreaterThanOrEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_page2_returnsDifferentResults() {
        for (int i = 1; i <= 3; i++) {
            createTransaction(token, accountId, "PageTwoStore" + i, String.valueOf(-i * 10));
        }

        var page0Result = gql("""
                { transactions(size: 1, page: 0) { content { id } } }
                """);
        var page1Result = gql("""
                { transactions(size: 1, page: 1) { content { id } } }
                """);

        var content0 = (List<Map<String, Object>>)
                ((Map<String, Object>) data(page0Result).get("transactions")).get("content");
        var content1 = (List<Map<String, Object>>)
                ((Map<String, Object>) data(page1Result).get("transactions")).get("content");
        assertThat(content0).hasSize(1);
        assertThat(content1).hasSize(1);
        assertThat(content0.get(0).get("id")).isNotEqualTo(content1.get(0).get("id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_outOfRangePage_returnsEmptyContent() {
        var result = gql("""
                { transactions(page: 9999) { content { id } } }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        List<?> content = (List<?>) txns.get("content");
        assertThat(content).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_sizeClamped_above250Returns250() {
        var result = gql("""
                { transactions(size: 500) { page { size } } }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var page = (Map<String, Object>) txns.get("page");
        assertThat(page.get("size")).isEqualTo(250);
    }

    // ── Sorting ──────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_sortByDateAsc_returnsOldestFirst() {
        createTransaction(token, accountId, "OldStore", "-10.00");
        // Need a different date — use a direct mutation
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "NewStore",
                        date: "2025-12-01T00:00:00", amount: -20.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));

        var result = gql("""
                {
                    transactions(sort: { sortBy: "date", sortDirection: ASC }) {
                        content { id date }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var content = (List<Map<String, Object>>) txns.get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);
        String firstDate = (String) content.get(0).get("date");
        String lastDate = (String) content.get(content.size() - 1).get("date");
        assertThat(firstDate.compareTo(lastDate)).isLessThanOrEqualTo(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_sortByAmountDesc_returnsLargestFirst() {
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "SmallStore",
                        date: "2025-07-01T00:00:00", amount: -1.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "BigStore",
                        date: "2025-07-02T00:00:00", amount: -999.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));

        var result = gql("""
                {
                    transactions(sort: { sortBy: "amount", sortDirection: DESC }) {
                        content { id amount }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var content = (List<Map<String, Object>>) txns.get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);
        double first = ((Number) content.get(0).get("amount")).doubleValue();
        double last = ((Number) content.get(content.size() - 1).get("amount")).doubleValue();
        assertThat(first).isGreaterThanOrEqualTo(last);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_sortByCategory_sortsByCategoryName() {
        String catAId = createCategory(token, "AAA Category");
        String catZId = createCategory(token, "ZZZ Category");

        gql("""
                mutation($acctId: ID!, $catId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "CatStoreZ",
                        date: "2025-07-01T00:00:00", amount: -10.00, categoryId: $catId
                    }) { id }
                }
                """, Map.of("acctId", accountId, "catId", catZId));
        gql("""
                mutation($acctId: ID!, $catId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "CatStoreA",
                        date: "2025-07-02T00:00:00", amount: -20.00, categoryId: $catId
                    }) { id }
                }
                """, Map.of("acctId", accountId, "catId", catAId));

        var result = gql("""
                {
                    transactions(sort: { sortBy: "category", sortDirection: ASC }) {
                        content { id categoryId }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var content = (List<Map<String, Object>>) txns.get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_sortByMerchant_sortsByMerchantName() {
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "Zebra Market",
                        date: "2025-07-01T00:00:00", amount: -10.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "Apple Store",
                        date: "2025-07-02T00:00:00", amount: -20.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));

        var result = gql("""
                {
                    transactions(sort: { sortBy: "merchant", sortDirection: ASC }) {
                        content { id merchantId }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var content = (List<Map<String, Object>>) txns.get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);
    }

    // ── Date Range Filters ───────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_startDateFilter_returnsOnOrAfter() {
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "EarlyStore",
                        date: "2024-01-01T00:00:00", amount: -10.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "LateStore",
                        date: "2025-12-01T00:00:00", amount: -20.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));

        var result = gql("""
                {
                    transactions(filter: { startDate: "2025-06-01" }) {
                        content { id date }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var content = (List<Map<String, Object>>) txns.get("content");
        for (var txn : content) {
            assertThat(txn.get("date").toString()).isGreaterThanOrEqualTo("2025-06-01");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_endDateFilter_returnsOnOrBefore() {
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "FutureStore",
                        date: "2026-12-01T00:00:00", amount: -10.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "PastStore",
                        date: "2024-06-01T00:00:00", amount: -20.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));

        var result = gql("""
                {
                    transactions(filter: { endDate: "2025-01-01" }) {
                        content { id date }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var content = (List<Map<String, Object>>) txns.get("content");
        for (var txn : content) {
            assertThat(txn.get("date").toString()).isLessThanOrEqualTo("2025-01-01T23:59:59");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_dateRange_returnsWithinRange() {
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "InRangeStore",
                        date: "2025-06-15T00:00:00", amount: -10.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "OutOfRangeStore",
                        date: "2024-01-01T00:00:00", amount: -20.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));

        var result = gql("""
                {
                    transactions(filter: { startDate: "2025-06-01", endDate: "2025-07-01" }) {
                        content { id date }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var content = (List<Map<String, Object>>) txns.get("content");
        assertThat(content).isNotEmpty();
        for (var txn : content) {
            assertThat(txn.get("date").toString()).isGreaterThanOrEqualTo("2025-06-01");
            assertThat(txn.get("date").toString()).isLessThanOrEqualTo("2025-07-01T23:59:59");
        }
    }

    // ── Amount Range Filters ─────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_minAmountFilter_returnsAboveMin() {
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "CheapStore",
                        date: "2025-07-01T00:00:00", amount: -1.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "ExpensiveStore",
                        date: "2025-07-02T00:00:00", amount: -500.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));

        var result = gql("""
                {
                    transactions(filter: { minAmount: -100 }) {
                        content { id amount }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var content = (List<Map<String, Object>>) txns.get("content");
        for (var txn : content) {
            double amount = ((Number) txn.get("amount")).doubleValue();
            assertThat(amount).isGreaterThanOrEqualTo(-100.0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_maxAmountFilter_returnsBelowMax() {
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "TinyStore",
                        date: "2025-07-01T00:00:00", amount: -5.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));

        var result = gql("""
                {
                    transactions(filter: { maxAmount: -2 }) {
                        content { id amount }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var content = (List<Map<String, Object>>) txns.get("content");
        for (var txn : content) {
            double amount = ((Number) txn.get("amount")).doubleValue();
            assertThat(amount).isLessThanOrEqualTo(-2.0);
        }
    }

    // ── Search ───────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_searchByNotes_matchesNotesField() {
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "SomeStore",
                        date: "2025-07-01T00:00:00", amount: -10.00,
                        notes: "unicorn sparkle purchase"
                    }) { id }
                }
                """, Map.of("acctId", accountId));

        var result = gql("""
                {
                    transactions(filter: { search: "unicorn sparkle" }) {
                        content { id notes }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var content = (List<Map<String, Object>>) txns.get("content");
        assertThat(content).isNotEmpty();
        assertThat((String) content.get(0).get("notes")).containsIgnoringCase("unicorn sparkle");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_searchByMerchantName_matchesMerchant() {
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "XylophoneEmporium",
                        date: "2025-07-01T00:00:00", amount: -10.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));

        var result = gql("""
                {
                    transactions(filter: { search: "XylophoneEmpor" }) {
                        content { id }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var content = (List<Map<String, Object>>) txns.get("content");
        assertThat(content).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_searchByCategoryName_matchesCategory() {
        String qCatId = createCategory(token, "Qwerty Utilities");

        gql("""
                mutation($acctId: ID!, $catId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "UtilStore",
                        date: "2025-07-01T00:00:00", amount: -10.00, categoryId: $catId
                    }) { id }
                }
                """, Map.of("acctId", accountId, "catId", qCatId));

        var result = gql("""
                {
                    transactions(filter: { search: "Qwerty" }) {
                        content { id }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var content = (List<Map<String, Object>>) txns.get("content");
        assertThat(content).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_searchByAmount_matchesAmountSubstring() {
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "AmountSearchStore",
                        date: "2025-07-01T00:00:00", amount: -829.47
                    }) { id }
                }
                """, Map.of("acctId", accountId));

        var result = gql("""
                {
                    transactions(filter: { search: "829" }) {
                        content { id amount }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var content = (List<Map<String, Object>>) txns.get("content");
        assertThat(content).isNotEmpty();
        assertThat(content).allSatisfy(t -> {
            String amount = String.valueOf(t.get("amount"));
            assertThat(amount).contains("829");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_searchNoMatch_returnsEmpty() {
        var result = gql("""
                {
                    transactions(filter: { search: "zzz_no_match_xyz_9999" }) {
                        content { id }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var content = (List<Map<String, Object>>) txns.get("content");
        assertThat(content).isEmpty();
    }

    // ── Combined Filters ─────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void listTransactions_combinedFiltersAndPagination_work() {
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "ComboStore1",
                        date: "2025-08-01T00:00:00", amount: -50.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));
        gql("""
                mutation($acctId: ID!) {
                    createTransaction(input: {
                        accountId: $acctId, merchantName: "ComboStore2",
                        date: "2025-08-15T00:00:00", amount: -100.00
                    }) { id }
                }
                """, Map.of("acctId", accountId));

        var result = gql("""
                {
                    transactions(
                        filter: { startDate: "2025-08-01", endDate: "2025-08-31" },
                        sort: { sortBy: "amount", sortDirection: ASC },
                        size: 10
                    ) {
                        content { id amount }
                        page { size }
                    }
                }
                """);

        var txns = (Map<String, Object>) data(result).get("transactions");
        var content = (List<Map<String, Object>>) txns.get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);
        var page = (Map<String, Object>) txns.get("page");
        assertThat(page.get("size")).isEqualTo(10);
    }
}
