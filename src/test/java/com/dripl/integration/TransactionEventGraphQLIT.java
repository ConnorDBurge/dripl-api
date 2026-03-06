package com.dripl.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class TransactionEventGraphQLIT extends BaseIntegrationTest {

    private String token;
    private String accountId;
    private String categoryId;
    private String categoryId2;

    @BeforeEach
    void setUp() {
        String email = "gql-event-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Event", "User");
        token = (String) bootstrap.get("token");

        accountId = createAccount(token, "Checking", "CASH", "CHECKING", "1000");
        categoryId = createCategory(token, "Groceries");
        categoryId2 = createCategory(token, "Dining");
    }

    private String createTransaction(String amount, String categoryId) {
        String query = """
                mutation {
                    createTransaction(input: {
                        accountId: "%s", merchantName: "TestMerchant",
                        amount: %s, date: "2025-07-01T00:00:00", categoryId: "%s"
                    }) { id }
                }
                """.formatted(accountId, amount, categoryId);
        @SuppressWarnings("unchecked")
        var txn = (Map<String, Object>) graphqlData(token, query).get("createTransaction");
        return (String) txn.get("id");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getEvents(String transactionId) {
        String query = """
                query($transactionId: ID!) {
                    transactionEvents(transactionId: $transactionId) {
                        id transactionId eventType
                        changes { field oldValue newValue }
                        performedBy performedAt
                    }
                }
                """;
        var data = graphqlData(token, query, Map.of("transactionId", transactionId));
        return (List<Map<String, Object>>) data.get("transactionEvents");
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

        graphql(token, """
                mutation($transactionId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { amount: -99.99 }) { id }
                }
                """, Map.of("transactionId", txnId));

        var events = awaitEvents(txnId, 2);
        assertThat(events).hasSize(2);
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

        graphql(token, """
                mutation($transactionId: ID!, $catId: ID) {
                    updateTransaction(transactionId: $transactionId, input: { categoryId: $catId }) { id }
                }
                """, Map.of("transactionId", txnId, "catId", categoryId2));

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

        graphql(token, """
                mutation($transactionId: ID!) {
                    updateTransaction(transactionId: $transactionId, input: { amount: -42.50 }) { id }
                }
                """, Map.of("transactionId", txnId));

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

        createTransactionGroup(token, "Test Group",
                """
                ["%s", "%s"]
                """.formatted(txnId1, txnId2).trim());

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

        var group = createTransactionGroup(token, "Test Group",
                """
                ["%s", "%s"]
                """.formatted(txnId1, txnId2).trim());
        String groupId = (String) group.get("id");
        awaitEvents(txnId1, 2);

        graphql(token, """
                mutation($transactionGroupId: ID!) { deleteTransactionGroup(transactionGroupId: $transactionGroupId) }
                """, Map.of("transactionGroupId", groupId));

        var events = awaitEvents(txnId1, 3);
        assertThat(events.stream().anyMatch(e -> "transaction.ungrouped".equals(e.get("eventType")))).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void splitTransaction_producesEvents() {
        String txnId = createTransaction("-100.00", categoryId);
        awaitEvents(txnId, 1);

        var split = createTransactionSplit(token, txnId,
                "[{amount: -60.00}, {amount: -40.00}]");
        var childIds = (List<String>) split.get("transactionIds");
        assertThat(childIds).hasSize(2);

        for (String childId : childIds) {
            var events = awaitEvents(childId, 1);
            assertThat(events.stream().anyMatch(e -> "transaction.split".equals(e.get("eventType")))).isTrue();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void dissolveSplit_producesUnsplitEvents() {
        String txnId = createTransaction("-100.00", categoryId);
        awaitEvents(txnId, 1);

        var split = createTransactionSplit(token, txnId,
                "[{amount: -60.00}, {amount: -40.00}]");
        String splitId = (String) split.get("id");
        var childIds = (List<String>) split.get("transactionIds");
        awaitEvents(childIds.get(0), 1);

        graphql(token, """
                mutation($transactionSplitId: ID!) { deleteTransactionSplit(transactionSplitId: $transactionSplitId) }
                """, Map.of("transactionSplitId", splitId));

        var events = awaitEvents(childIds.get(0), 2);
        assertThat(events.stream().anyMatch(e -> "transaction.unsplit".equals(e.get("eventType")))).isTrue();
    }

    @Test
    void transactionEvents_nonexistentTransaction_returnsError() {
        var result = graphql(token, """
                query($transactionId: ID!) {
                    transactionEvents(transactionId: $transactionId) { id }
                }
                """, Map.of("transactionId", UUID.randomUUID().toString()));
        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void deleteTransaction_cascadesDeleteEvents() {
        String txnId = createTransaction("-42.50", categoryId);
        awaitEvents(txnId, 1);

        graphql(token, """
                mutation($transactionId: ID!) { deleteTransaction(transactionId: $transactionId) }
                """, Map.of("transactionId", txnId));

        // Events should be gone (cascade delete) — transaction itself not found
        var result = graphql(token, """
                query($transactionId: ID!) {
                    transactionEvents(transactionId: $transactionId) { id }
                }
                """, Map.of("transactionId", txnId));
        assertThat(result.get("errors")).isNotNull();
    }
}
