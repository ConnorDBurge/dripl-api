package com.dripl.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionSplitGraphQLIT extends BaseIntegrationTest {

    private String token;
    private String accountId;
    private String categoryId;
    private String category2Id;
    private String tagId;

    @BeforeEach
    void setUp() {
        String email = "gql-split-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Split", "User");
        token = (String) bootstrap.get("token");

        accountId = createAccount(token, "Checking", "CASH", "CHECKING", "5000");
        categoryId = createCategory(token, "Groceries");
        category2Id = createCategory(token, "Electronics");
        tagId = createTag(token, "split-tag");
    }

    private String createTransaction(String merchantName, double amount) {
        return createTransaction(token, accountId, merchantName, String.valueOf(amount));
    }

    @SuppressWarnings("unchecked")
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

    // --- CRUD ---

    @Test
    @SuppressWarnings("unchecked")
    void createSplit_returnsCreatedSplit() {
        String txnId = createTransaction("Target", -100.00);

        var result = graphql("""
                mutation {
                    createTransactionSplit(input: {
                        transactionId: "%s",
                        children: [
                            { amount: -60.00, categoryId: "%s" },
                            { amount: -40.00, categoryId: "%s", notes: "USB cable" }
                        ]
                    }) { id totalAmount accountId transactionIds currencyCode }
                }
                """.formatted(txnId, categoryId, category2Id));

        assertThat(result.get("errors")).isNull();
        var split = (Map<String, Object>) data(result).get("createTransactionSplit");
        assertThat(((Number) split.get("totalAmount")).doubleValue()).isEqualTo(-100.0);
        assertThat(split.get("accountId")).isEqualTo(accountId);
        assertThat((List<?>) split.get("transactionIds")).hasSize(2);

        // Original transaction should be deleted
        var txnResult = graphql("""
                query { transaction(transactionId: "%s") { id } }
                """.formatted(txnId));
        assertThat(txnResult.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSplits_returnsAll() {
        String txnId = createTransaction("Publix", -80.00);
        createTransactionSplit(token, txnId, """
                [{ amount: -50.00 }, { amount: -30.00 }]
                """);

        var result = graphql("{ transactionSplits { id totalAmount } }");

        assertThat(result.get("errors")).isNull();
        var splits = (List<?>) data(result).get("transactionSplits");
        assertThat(splits).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSplit_returnsSplit() {
        String txnId = createTransaction("Walmart", -120.00);
        var split = createTransactionSplit(token, txnId, """
                [{ amount: -70.00 }, { amount: -50.00 }]
                """);
        String splitId = (String) split.get("id");

        var result = graphql("""
                query { transactionSplit(transactionSplitId: "%s") { id totalAmount accountId } }
                """.formatted(splitId));

        assertThat(result.get("errors")).isNull();
        var fetched = (Map<String, Object>) data(result).get("transactionSplit");
        assertThat(((Number) fetched.get("totalAmount")).doubleValue()).isEqualTo(-120.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateSplit_updateChildren() {
        String txnId = createTransaction("Target", -100.00);
        var split = createTransactionSplit(token, txnId, """
                [{ amount: -60.00 }, { amount: -40.00 }]
                """);
        String splitId = (String) split.get("id");
        List<String> childIds = (List<String>) split.get("transactionIds");

        var result = graphql("""
                mutation {
                    updateTransactionSplit(transactionSplitId: "%s", input: {
                        children: [
                            { id: "%s", amount: -70.00 },
                            { id: "%s", amount: -30.00 }
                        ]
                    }) { id totalAmount transactionIds }
                }
                """.formatted(splitId, childIds.get(0), childIds.get(1)));

        assertThat(result.get("errors")).isNull();
        assertThat(data(result).get("updateTransactionSplit")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateSplit_addNewChild_removeExisting() {
        String txnId = createTransaction("Target", -100.00);
        var split = createTransactionSplit(token, txnId, """
                [{ amount: -60.00 }, { amount: -40.00 }]
                """);
        String splitId = (String) split.get("id");
        List<String> childIds = (List<String>) split.get("transactionIds");

        var result = graphql("""
                mutation {
                    updateTransactionSplit(transactionSplitId: "%s", input: {
                        children: [
                            { id: "%s", amount: -50.00 },
                            { amount: -50.00, merchantName: "Walmart" }
                        ]
                    }) { id totalAmount transactionIds }
                }
                """.formatted(splitId, childIds.get(0)));

        assertThat(result.get("errors")).isNull();
        var updated = (Map<String, Object>) data(result).get("updateTransactionSplit");
        assertThat((List<?>) updated.get("transactionIds")).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteSplit_dissolves() {
        String txnId = createTransaction("Target", -100.00);
        var split = createTransactionSplit(token, txnId, """
                [{ amount: -60.00 }, { amount: -40.00 }]
                """);
        String splitId = (String) split.get("id");
        List<String> childIds = (List<String>) split.get("transactionIds");

        // Dissolve
        var deleteResult = graphql("""
                mutation { deleteTransactionSplit(transactionSplitId: "%s") }
                """.formatted(splitId));
        assertThat(data(deleteResult).get("deleteTransactionSplit")).isEqualTo(true);

        // Split is gone
        var getResult = graphql("""
                query { transactionSplit(transactionSplitId: "%s") { id } }
                """.formatted(splitId));
        assertThat(getResult.get("errors")).isNotNull();

        // Children still exist with splitId = null
        var txnResult = graphql("""
                query { transaction(transactionId: "%s") { id splitId } }
                """.formatted(childIds.get(0)));
        assertThat(txnResult.get("errors")).isNull();
        var txn = (Map<String, Object>) data(txnResult).get("transaction");
        assertThat(txn.get("splitId")).isNull();
    }

    // --- Amount Validation ---

    @Test
    void createSplit_amountMismatch_returnsError() {
        String txnId = createTransaction("Target", -100.00);

        var result = graphql("""
                mutation {
                    createTransactionSplit(input: {
                        transactionId: "%s",
                        children: [
                            { amount: -60.00 },
                            { amount: -30.00 }
                        ]
                    }) { id }
                }
                """.formatted(txnId));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateSplit_amountMismatch_returnsError() {
        String txnId = createTransaction("Target", -100.00);
        var split = createTransactionSplit(token, txnId, """
                [{ amount: -60.00 }, { amount: -40.00 }]
                """);
        String splitId = (String) split.get("id");
        List<String> childIds = (List<String>) split.get("transactionIds");

        var result = graphql("""
                mutation {
                    updateTransactionSplit(transactionSplitId: "%s", input: {
                        children: [
                            { id: "%s", amount: -70.00 },
                            { id: "%s", amount: -40.00 }
                        ]
                    }) { id }
                }
                """.formatted(splitId, childIds.get(0), childIds.get(1)));

        assertThat(result.get("errors")).isNotNull();
    }

    // --- Split Locked Fields ---

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_rejectAccountIdChange() {
        String txnId = createTransaction("Target", -100.00);
        var split = createTransactionSplit(token, txnId, """
                [{ amount: -60.00 }, { amount: -40.00 }]
                """);
        List<String> childIds = (List<String>) split.get("transactionIds");

        var result = graphql("""
                mutation {
                    updateTransaction(transactionId: "%s", input: { accountId: "%s" }) { id }
                }
                """.formatted(childIds.get(0), UUID.randomUUID()));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_rejectAmountChange() {
        String txnId = createTransaction("Target", -100.00);
        var split = createTransactionSplit(token, txnId, """
                [{ amount: -60.00 }, { amount: -40.00 }]
                """);
        List<String> childIds = (List<String>) split.get("transactionIds");

        var result = graphql("""
                mutation {
                    updateTransaction(transactionId: "%s", input: { amount: -99.99 }) { id }
                }
                """.formatted(childIds.get(0)));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_rejectDateChange() {
        String txnId = createTransaction("Target", -100.00);
        var split = createTransactionSplit(token, txnId, """
                [{ amount: -60.00 }, { amount: -40.00 }]
                """);
        List<String> childIds = (List<String>) split.get("transactionIds");

        var result = graphql("""
                mutation {
                    updateTransaction(transactionId: "%s", input: { date: "2025-12-01T00:00:00" }) { id }
                }
                """.formatted(childIds.get(0)));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_allowCategoryChange() {
        String txnId = createTransaction("Target", -100.00);
        var split = createTransactionSplit(token, txnId, """
                [{ amount: -60.00 }, { amount: -40.00 }]
                """);
        List<String> childIds = (List<String>) split.get("transactionIds");

        var result = graphql("""
                mutation {
                    updateTransaction(transactionId: "%s", input: { categoryId: "%s" }) {
                        id categoryId
                    }
                }
                """.formatted(childIds.get(0), category2Id));

        assertThat(result.get("errors")).isNull();
        var txn = (Map<String, Object>) data(result).get("updateTransaction");
        assertThat(txn.get("categoryId")).isEqualTo(category2Id);
    }

    // --- Mutual Exclusivity ---

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_cannotBeGrouped() {
        String txnId = createTransaction("Target", -100.00);
        String txnId2 = createTransaction("Walmart", -50.00);
        var split = createTransactionSplit(token, txnId, """
                [{ amount: -60.00 }, { amount: -40.00 }]
                """);
        List<String> childIds = (List<String>) split.get("transactionIds");

        var result = graphql("""
                mutation {
                    createTransactionGroup(input: {
                        name: "Group",
                        transactionIds: ["%s", "%s"]
                    }) { id }
                }
                """.formatted(childIds.get(0), txnId2));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void groupedTransaction_cannotBeSplit() {
        String txnId = createTransaction("Target", -100.00);
        String txnId2 = createTransaction("Walmart", -50.00);

        // Create group
        graphql("""
                mutation {
                    createTransactionGroup(input: {
                        name: "Group",
                        transactionIds: ["%s", "%s"]
                    }) { id }
                }
                """.formatted(txnId, txnId2));

        // Try to split a grouped transaction
        var result = graphql("""
                mutation {
                    createTransactionSplit(input: {
                        transactionId: "%s",
                        children: [
                            { amount: -60.00 },
                            { amount: -40.00 }
                        ]
                    }) { id }
                }
                """.formatted(txnId));

        assertThat(result.get("errors")).isNotNull();
    }

    // --- Split + RI linking ---

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_canBeRILinked() {
        String txnId = createTransaction("Netflix", -30.00);
        var split = createTransactionSplit(token, txnId, """
                [{ amount: -15.00 }, { amount: -15.00 }]
                """);
        List<String> childIds = (List<String>) split.get("transactionIds");

        // Create a recurring item with same account via GraphQL
        @SuppressWarnings("unchecked")
        var riData = (Map<String, Object>) graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "Netflix", accountId: "%s", merchantName: "Netflix",
                        amount: -15.00, frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-01T00:00:00"], startDate: "2025-01-01T00:00:00"
                    }) { id }
                }
                """.formatted(accountId)).get("createRecurringItem");
        String riId = (String) riData.get("id");

        // Link RI to split child via GraphQL
        var result = graphql("""
                mutation {
                    updateTransaction(transactionId: "%s", input: {
                        recurringItemId: "%s", occurrenceDate: "2025-07-01"
                    }) { id recurringItemId }
                }
                """.formatted(childIds.get(0), riId));

        assertThat(result.get("errors")).isNull();
        var txn = (Map<String, Object>) data(result).get("updateTransaction");
        assertThat(txn.get("recurringItemId")).isEqualTo(riId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_RIMismatchAccount_returnsError() {
        String txnId = createTransaction("Netflix", -30.00);
        var split = createTransactionSplit(token, txnId, """
                [{ amount: -15.00 }, { amount: -15.00 }]
                """);
        List<String> childIds = (List<String>) split.get("transactionIds");

        String account2Id = createAccount(token, "Savings", "CASH", "SAVINGS", "10000");

        // Create RI with different account via GraphQL
        @SuppressWarnings("unchecked")
        var riData2 = (Map<String, Object>) graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "Netflix Savings", accountId: "%s", merchantName: "Netflix",
                        amount: -15.00, frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-01T00:00:00"], startDate: "2025-01-01T00:00:00"
                    }) { id }
                }
                """.formatted(account2Id)).get("createRecurringItem");
        String riId = (String) riData2.get("id");

        // Try to link RI to split child — account mismatch
        var result = graphql("""
                mutation {
                    updateTransaction(transactionId: "%s", input: { recurringItemId: "%s" }) { id }
                }
                """.formatted(childIds.get(0), riId));

        assertThat(result.get("errors")).isNotNull();
    }

    // --- splitId is fully locked ---

    @Test
    @SuppressWarnings("unchecked")
    void unlinkSplitChild_returnsError() {
        String txnId = createTransaction("Target", -100.00);
        var split = createTransactionSplit(token, txnId, """
                [{ amount: -33.00 }, { amount: -33.00 }, { amount: -34.00 }]
                """);
        List<String> childIds = (List<String>) split.get("transactionIds");

        var result = graphql("""
                mutation($transactionId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { splitId: null }) { id }
                }
                """, Map.of("transactionId", childIds.get(0)));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void assignSplitId_returnsError() {
        String txnId = createTransaction("Target", -50.00);

        var result = graphql("""
                mutation {
                    updateTransaction(transactionId: "%s", input: { splitId: "%s" }) { id }
                }
                """.formatted(txnId, UUID.randomUUID()));

        assertThat(result.get("errors")).isNotNull();
    }

    // --- Child inherits splitId ---

    @Test
    @SuppressWarnings("unchecked")
    void splitChild_showsSplitId() {
        String txnId = createTransaction("Target", -100.00);
        var split = createTransactionSplit(token, txnId, """
                [{ amount: -60.00 }, { amount: -40.00 }]
                """);
        String splitId = (String) split.get("id");
        List<String> childIds = (List<String>) split.get("transactionIds");

        var txnResult = graphql("""
                query { transaction(transactionId: "%s") { id splitId } }
                """.formatted(childIds.get(0)));

        assertThat(txnResult.get("errors")).isNull();
        var txn = (Map<String, Object>) data(txnResult).get("transaction");
        assertThat(txn.get("splitId")).isEqualTo(splitId);
    }

    // --- Filter by splitId ---

    @Test
    @SuppressWarnings("unchecked")
    void filterTransactionsBySplitId() {
        String txnId = createTransaction("Target", -100.00);
        var split = createTransactionSplit(token, txnId, """
                [{ amount: -60.00 }, { amount: -40.00 }]
                """);
        String splitId = (String) split.get("id");

        var result = graphql("""
                query {
                    transactions(filter: { splitId: "%s" }) {
                        content { id splitId }
                    }
                }
                """.formatted(splitId));

        assertThat(result.get("errors")).isNull();
        var connection = (Map<String, Object>) data(result).get("transactions");
        var content = (List<?>) connection.get("content");
        assertThat(content).hasSize(2);
    }
}
