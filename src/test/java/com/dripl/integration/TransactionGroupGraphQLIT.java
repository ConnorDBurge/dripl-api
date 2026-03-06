package com.dripl.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionGroupGraphQLIT extends BaseIntegrationTest {

    private String token;
    private String accountId;
    private String txn1Id;
    private String txn2Id;
    private String txn3Id;
    private String categoryId;
    private String tagId;

    @BeforeEach
    void setUp() {
        String email = "gql-group-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Group", "User");
        token = (String) bootstrap.get("token");

        accountId = createAccount(token, "Checking", "CASH", "CHECKING", "1000");
        categoryId = createCategory(token, "Vacation");
        tagId = createTag(token, "beach-trip");

        txn1Id = createTransaction(token, accountId, "Restaurant A", String.valueOf(-50.0));
        txn2Id = createTransaction(token, accountId, "Gas Station", String.valueOf(-30.0));
        txn3Id = createTransaction(token, accountId, "Hotel", String.valueOf(-200.0));
    }

    private Map<String, Object> graphql(String query) {
        return graphql(token, query);
    }

    private Map<String, Object> graphql(String query, Map<String, Object> variables) {
        return graphql(token, query, variables);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> response) {
        return (Map<String, Object>) response.get("data");
    }

    private String txnIds2() {
        return "[\"%s\",\"%s\"]".formatted(txn1Id, txn2Id);
    }

    private String txnIds3() {
        return "[\"%s\",\"%s\",\"%s\"]".formatted(txn1Id, txn2Id, txn3Id);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createTransactionGroup_returnsGroup() {
        var group = createTransactionGroup(token, "Beach Vacation", txnIds2(),
                "categoryId: \"%s\", tagIds: [\"%s\"]".formatted(categoryId, tagId));

        assertThat(group.get("name")).isEqualTo("Beach Vacation");
        assertThat(group.get("categoryId")).isEqualTo(categoryId);
        assertThat((List<String>) group.get("tagIds")).containsExactly(tagId);
        assertThat(group.get("totalAmount")).isEqualTo(-80.0);
        assertThat((List<?>) group.get("transactionIds")).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTransactionGroups_returnsAll() {
        createTransactionGroup(token, "Trip", txnIds2());

        var result = graphql("{ transactionGroups { id name } }");
        var groups = (List<Map<String, Object>>) data(result).get("transactionGroups");
        assertThat(groups).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getTransactionGroup_returnsGroup() {
        var group = createTransactionGroup(token, "Trip", txnIds2());
        String groupId = (String) group.get("id");

        var result = graphql("""
                query($transactionGroupId: ID!) { transactionGroup(transactionGroupId: $transactionGroupId) { id name transactionIds } }
                """, Map.of("transactionGroupId", groupId));

        var fetched = (Map<String, Object>) data(result).get("transactionGroup");
        assertThat(fetched.get("name")).isEqualTo("Trip");
        assertThat((List<?>) fetched.get("transactionIds")).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateTransactionGroup_returnsUpdated() {
        var group = createTransactionGroup(token, "Trip", txnIds2());
        String groupId = (String) group.get("id");

        var result = graphql("""
                mutation($transactionGroupId: ID!) {
                    updateTransactionGroup(transactionGroupId: $transactionGroupId, input: { name: "Beach Vacation 2025", categoryId: "%s" }) {
                        id name categoryId
                    }
                }
                """.formatted(categoryId), Map.of("transactionGroupId", groupId));

        var updated = (Map<String, Object>) data(result).get("updateTransactionGroup");
        assertThat(updated.get("name")).isEqualTo("Beach Vacation 2025");
        assertThat(updated.get("categoryId")).isEqualTo(categoryId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void addTransactionToGroup_viaTransactionIds() {
        var group = createTransactionGroup(token, "Trip", txnIds2());
        String groupId = (String) group.get("id");

        var result = graphql("""
                mutation($transactionGroupId: ID!) {
                    updateTransactionGroup(transactionGroupId: $transactionGroupId, input: { transactionIds: ["%s","%s","%s"] }) {
                        id transactionIds totalAmount
                    }
                }
                """.formatted(txn1Id, txn2Id, txn3Id), Map.of("transactionGroupId", groupId));

        var updated = (Map<String, Object>) data(result).get("updateTransactionGroup");
        assertThat((List<?>) updated.get("transactionIds")).hasSize(3);
        assertThat(updated.get("totalAmount")).isEqualTo(-280.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void removeTransactionFromGroup_viaTransactionIds() {
        var group = createTransactionGroup(token, "Trip", txnIds3());
        String groupId = (String) group.get("id");

        var result = graphql("""
                mutation($transactionGroupId: ID!) {
                    updateTransactionGroup(transactionGroupId: $transactionGroupId, input: { transactionIds: ["%s","%s"] }) {
                        id transactionIds
                    }
                }
                """.formatted(txn1Id, txn2Id), Map.of("transactionGroupId", groupId));

        var updated = (Map<String, Object>) data(result).get("updateTransactionGroup");
        assertThat((List<?>) updated.get("transactionIds")).hasSize(2);
    }

    @Test
    void updateGroupTransactionIds_belowMinimum_returnsError() {
        var group = createTransactionGroup(token, "Trip", txnIds2());
        String groupId = (String) group.get("id");

        var result = graphql("""
                mutation($transactionGroupId: ID!) {
                    updateTransactionGroup(transactionGroupId: $transactionGroupId, input: { transactionIds: ["%s"] }) { id }
                }
                """.formatted(txn1Id), Map.of("transactionGroupId", groupId));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteTransactionGroup_dissolvesGroup() {
        var group = createTransactionGroup(token, "Trip", txnIds2());
        String groupId = (String) group.get("id");

        // Delete the group
        var deleteResult = graphql("""
                mutation($transactionGroupId: ID!) { deleteTransactionGroup(transactionGroupId: $transactionGroupId) }
                """, Map.of("transactionGroupId", groupId));
        assertThat(data(deleteResult).get("deleteTransactionGroup")).isEqualTo(true);

        // Verify group is gone
        var getResult = graphql("""
                query($transactionGroupId: ID!) { transactionGroup(transactionGroupId: $transactionGroupId) { id } }
                """, Map.of("transactionGroupId", groupId));
        assertThat(getResult.get("errors")).isNotNull();

        // Verify transaction still exists and groupId is null
        var txnResult = graphql("""
                query($transactionId: ID!) { transaction(transactionId: $transactionId) { id groupId } }
                """, Map.of("transactionId", txn1Id));
        var txn = (Map<String, Object>) data(txnResult).get("transaction");
        assertThat(txn.get("groupId")).isNull();
    }

    @Test
    void transactionAlreadyInGroup_returnsError() {
        createTransactionGroup(token, "Trip 1", txnIds2());

        // Try to create another group with txn1 (already grouped)
        var result = graphql("""
                mutation {
                    createTransactionGroup(input: {
                        name: "Trip 2", transactionIds: ["%s","%s"]
                    }) { id }
                }
                """.formatted(txn1Id, txn3Id));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void transactionShowsGroupId() {
        var group = createTransactionGroup(token, "Trip", txnIds2());
        String groupId = (String) group.get("id");

        var result = graphql("""
                query($transactionId: ID!) { transaction(transactionId: $transactionId) { id groupId } }
                """, Map.of("transactionId", txn1Id));
        var txn = (Map<String, Object>) data(result).get("transaction");
        assertThat(txn.get("groupId")).isEqualTo(groupId);
    }

    @Test
    void createGroup_lessThan2Transactions_returnsError() {
        var result = graphql("""
                mutation {
                    createTransactionGroup(input: {
                        name: "Solo", transactionIds: ["%s"]
                    }) { id }
                }
                """.formatted(txn1Id));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void createGroup_overridesCategoryTagsNotesOnTransactions() {
        createTransactionGroup(token, "Trip", txnIds2(),
                "categoryId: \"%s\", tagIds: [\"%s\"], notes: \"group notes\"".formatted(categoryId, tagId));

        var result = graphql("""
                query($transactionId: ID!) { transaction(transactionId: $transactionId) { id categoryId tagIds notes } }
                """, Map.of("transactionId", txn1Id));
        var txn = (Map<String, Object>) data(result).get("transaction");
        assertThat(txn.get("categoryId")).isEqualTo(categoryId);
        assertThat((List<String>) txn.get("tagIds")).containsExactly(tagId);
        assertThat(txn.get("notes")).isEqualTo("group notes");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateGroup_overridesCategoryTagsOnTransactions() {
        var group = createTransactionGroup(token, "Trip", txnIds2());
        String groupId = (String) group.get("id");

        graphql("""
                mutation($transactionGroupId: ID!) {
                    updateTransactionGroup(transactionGroupId: $transactionGroupId, input: {
                        categoryId: "%s", tagIds: ["%s"], notes: "updated notes"
                    }) { id }
                }
                """.formatted(categoryId, tagId), Map.of("transactionGroupId", groupId));

        var result = graphql("""
                query($transactionId: ID!) { transaction(transactionId: $transactionId) { id categoryId tagIds notes } }
                """, Map.of("transactionId", txn1Id));
        var txn = (Map<String, Object>) data(result).get("transaction");
        assertThat(txn.get("categoryId")).isEqualTo(categoryId);
        assertThat((List<String>) txn.get("tagIds")).containsExactly(tagId);
        assertThat(txn.get("notes")).isEqualTo("updated notes");
    }

    @Test
    @SuppressWarnings("unchecked")
    void addTransaction_inheritsGroupOverrides() {
        var group = createTransactionGroup(token, "Trip", txnIds2(),
                "categoryId: \"%s\", tagIds: [\"%s\"]".formatted(categoryId, tagId));
        String groupId = (String) group.get("id");

        graphql("""
                mutation($transactionGroupId: ID!) {
                    updateTransactionGroup(transactionGroupId: $transactionGroupId, input: {
                        transactionIds: ["%s","%s","%s"]
                    }) { id }
                }
                """.formatted(txn1Id, txn2Id, txn3Id), Map.of("transactionGroupId", groupId));

        var result = graphql("""
                query($transactionId: ID!) { transaction(transactionId: $transactionId) { id categoryId tagIds } }
                """, Map.of("transactionId", txn3Id));
        var txn = (Map<String, Object>) data(result).get("transaction");
        assertThat(txn.get("categoryId")).isEqualTo(categoryId);
        assertThat((List<String>) txn.get("tagIds")).containsExactly(tagId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void removeTransactionFromGroup_clearsGroupIdOnRemovedTxn() {
        var group = createTransactionGroup(token, "Trip", txnIds3());
        String groupId = (String) group.get("id");

        graphql("""
                mutation($transactionGroupId: ID!) {
                    updateTransactionGroup(transactionGroupId: $transactionGroupId, input: {
                        transactionIds: ["%s","%s"]
                    }) { id }
                }
                """.formatted(txn1Id, txn2Id), Map.of("transactionGroupId", groupId));

        // Verify txn3 no longer has groupId
        var txn3Result = graphql("""
                query($transactionId: ID!) { transaction(transactionId: $transactionId) { id groupId } }
                """, Map.of("transactionId", txn3Id));
        var txn3 = (Map<String, Object>) data(txn3Result).get("transaction");
        assertThat(txn3.get("groupId")).isNull();

        // Verify txn1 still in group
        var txn1Result = graphql("""
                query($transactionId: ID!) { transaction(transactionId: $transactionId) { id groupId } }
                """, Map.of("transactionId", txn1Id));
        var txn1 = (Map<String, Object>) data(txn1Result).get("transaction");
        assertThat(txn1.get("groupId")).isEqualTo(groupId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void addRecurringLinkedTransaction_viaTransactionIds_returnsError() {
        var group = createTransactionGroup(token, "Trip", txnIds2());
        String groupId = (String) group.get("id");

        // Create a recurring item (still REST — not yet migrated to GraphQL)
        var riResp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Netflix","amount":-15.99,"frequencyGranularity":"MONTH","anchorDates":["2025-01-15T00:00:00"],"startDate":"2025-01-01T00:00:00"}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String riId = (String) riResp.getBody().get("id");

        // Create RI-linked transaction via GraphQL
        @SuppressWarnings("unchecked")
        var riTxnResult = graphql("""
                mutation {
                    createTransaction(input: {
                        recurringItemId: "%s", date: "2025-07-01T00:00:00", occurrenceDate: "2025-07-01"
                    }) { id }
                }
                """.formatted(riId));
        String riTxnId = (String) ((Map<String, Object>) data(riTxnResult).get("createTransaction")).get("id");

        // Try to add RI-linked txn to group — should fail
        var result = graphql("""
                mutation($transactionGroupId: ID!) {
                    updateTransactionGroup(transactionGroupId: $transactionGroupId, input: {
                        transactionIds: ["%s","%s","%s"]
                    }) { id }
                }
                """.formatted(txn1Id, txn2Id, riTxnId), Map.of("transactionGroupId", groupId));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteGroup_clearsGroupIdOnAllTransactions() {
        var group = createTransactionGroup(token, "Trip", txnIds2());
        String groupId = (String) group.get("id");

        var deleteResult = graphql("""
                mutation($transactionGroupId: ID!) { deleteTransactionGroup(transactionGroupId: $transactionGroupId) }
                """, Map.of("transactionGroupId", groupId));
        assertThat(data(deleteResult).get("deleteTransactionGroup")).isEqualTo(true);

        // Both transactions should have null groupId
        var txn1Result = graphql("""
                query($transactionId: ID!) { transaction(transactionId: $transactionId) { id groupId } }
                """, Map.of("transactionId", txn1Id));
        var txn2Result = graphql("""
                query($transactionId: ID!) { transaction(transactionId: $transactionId) { id groupId } }
                """, Map.of("transactionId", txn2Id));
        assertThat(((Map<String, Object>) data(txn1Result).get("transaction")).get("groupId")).isNull();
        assertThat(((Map<String, Object>) data(txn2Result).get("transaction")).get("groupId")).isNull();
    }
}
