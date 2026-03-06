package com.dripl.integration;

import com.dripl.auth.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringItemCrudIT extends BaseIntegrationTest {

    @Autowired private TokenService tokenService;

    private String token;
    private UUID workspaceId;
    private UUID userId;
    private String accountId;
    private String categoryId;
    private String tagId;

    private static final String RECURRING_ITEM_FIELDS =
            "id description amount status frequencyGranularity frequencyQuantity merchantId categoryId anchorDates tagIds endDate";

    @BeforeEach
    void setUp() {
        String email = "recurring-user-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Recurring", "User");
        token = (String) bootstrap.get("token");
        workspaceId = UUID.fromString((String) bootstrap.get("workspaceId"));
        userId = UUID.fromString((String) bootstrap.get("userId"));

        // Create an account
        accountId = createAccount(token, "Checking", "CASH", "CHECKING", "1000");

        // Create a category
        categoryId = createCategory(token, "Subscriptions");

        // Create a tag
        tagId = createTag(token, "monthly");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createRecurringItem_withExistingMerchant_returns201() {
        // Pre-create merchant
        createMerchant(token, "Netflix");

        var data = graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "Netflix", merchantName: "Netflix",
                        accountId: "%s", categoryId: "%s", amount: -15.99,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00",
                        tagIds: ["%s"]
                    }) { %s }
                }
                """.formatted(accountId, categoryId, tagId, RECURRING_ITEM_FIELDS));
        var body = (Map<String, Object>) data.get("createRecurringItem");

        assertThat(body.get("description")).isEqualTo("Netflix");
        assertThat(((Number) body.get("amount")).doubleValue()).isEqualTo(-15.99);
        assertThat(body.get("status")).isEqualTo("ACTIVE");
        assertThat(body.get("frequencyGranularity")).isEqualTo("MONTH");
        assertThat(body.get("frequencyQuantity")).isEqualTo(1);
        assertThat(body.get("merchantId")).isNotNull();
        assertThat(body.get("categoryId")).isEqualTo(categoryId);
        assertThat((List<?>) body.get("anchorDates")).hasSize(1);
        assertThat((List<?>) body.get("tagIds")).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createRecurringItem_autoCreatesMerchant_returns201() {
        var data = graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "New Service", merchantName: "New Service",
                        accountId: "%s", amount: -9.99,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00"
                    }) { id merchantId }
                }
                """.formatted(accountId));
        var body = (Map<String, Object>) data.get("createRecurringItem");

        assertThat(body.get("merchantId")).isNotNull();

        // Verify the merchant was actually created
        List<Map<String, Object>> merchants = listMerchants(token);
        assertThat(merchants).extracting(m -> m.get("name")).contains("New Service");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createRecurringItem_merchantLookup_caseInsensitive() {
        // Create "Netflix"
        createMerchant(token, "Netflix");

        // Create recurring item with "NETFLIX" — should find existing, not create new
        var data = graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "Netflix", merchantName: "NETFLIX",
                        accountId: "%s", amount: -15.99,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00"
                    }) { id merchantId }
                }
                """.formatted(accountId));
        var body = (Map<String, Object>) data.get("createRecurringItem");

        assertThat(body.get("id")).isNotNull();

        // Verify no duplicate merchant
        long netflixCount = listMerchants(token).stream()
                .filter(m -> ((String) m.get("name")).equalsIgnoreCase("Netflix"))
                .count();
        assertThat(netflixCount).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listRecurringItems_returnsAll() {
        // Create two recurring items
        graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "Service A", merchantName: "Service A",
                        accountId: "%s", amount: -10.00,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00"
                    }) { id }
                }
                """.formatted(accountId));
        graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "Service B", merchantName: "Service B",
                        accountId: "%s", amount: -20.00,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00"
                    }) { id }
                }
                """.formatted(accountId));

        var data = graphqlData(token, """
                { recurringItems { id } }
                """);
        var items = (List<?>) data.get("recurringItems");

        assertThat(items).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getRecurringItem_byId_returns200() {
        var createData = graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "Spotify", merchantName: "Spotify",
                        accountId: "%s", amount: -9.99,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00"
                    }) { id }
                }
                """.formatted(accountId));
        String itemId = (String) ((Map<String, Object>) createData.get("createRecurringItem")).get("id");

        var data = graphqlData(token, """
                { recurringItem(recurringItemId: "%s") { id amount } }
                """.formatted(itemId));
        var body = (Map<String, Object>) data.get("recurringItem");

        assertThat(((Number) body.get("amount")).doubleValue()).isEqualTo(-9.99);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateRecurringItem_partialFields_returns200() {
        var createData = graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "Spotify", merchantName: "Spotify",
                        accountId: "%s", amount: -9.99,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00"
                    }) { id }
                }
                """.formatted(accountId));
        String itemId = (String) ((Map<String, Object>) createData.get("createRecurringItem")).get("id");

        var data = graphqlData(token, """
                mutation {
                    updateRecurringItem(recurringItemId: "%s", input: {
                        amount: -25.99, description: "Updated"
                    }) { id amount description }
                }
                """.formatted(itemId));
        var body = (Map<String, Object>) data.get("updateRecurringItem");

        assertThat(((Number) body.get("amount")).doubleValue()).isEqualTo(-25.99);
        assertThat(body.get("description")).isEqualTo("Updated");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateRecurringItem_changeMerchant_autoCreatesNew() {
        var createData = graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "OldService", merchantName: "OldService",
                        accountId: "%s", amount: -10.00,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00"
                    }) { id merchantId }
                }
                """.formatted(accountId));
        var created = (Map<String, Object>) createData.get("createRecurringItem");
        String itemId = (String) created.get("id");
        String oldMerchantId = (String) created.get("merchantId");

        var data = graphqlData(token, """
                mutation {
                    updateRecurringItem(recurringItemId: "%s", input: {
                        merchantName: "NewService"
                    }) { id merchantId }
                }
                """.formatted(itemId));
        var body = (Map<String, Object>) data.get("updateRecurringItem");

        assertThat(body.get("merchantId")).isNotEqualTo(oldMerchantId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateRecurringItem_setCategoryToNull() {
        var createData = graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "WithCategory", merchantName: "WithCategory",
                        accountId: "%s", categoryId: "%s", amount: -10.00,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00"
                    }) { id categoryId }
                }
                """.formatted(accountId, categoryId));
        String itemId = (String) ((Map<String, Object>) createData.get("createRecurringItem")).get("id");

        var data = graphqlData(token, """
                mutation {
                    updateRecurringItem(recurringItemId: "%s", input: {
                        categoryId: null
                    }) { id categoryId }
                }
                """.formatted(itemId));
        var body = (Map<String, Object>) data.get("updateRecurringItem");

        assertThat(body.get("categoryId")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateRecurringItem_setAndClearTags() {
        var createData = graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "TagTest", merchantName: "TagTest",
                        accountId: "%s", amount: -10.00,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00"
                    }) { id tagIds }
                }
                """.formatted(accountId));
        String itemId = (String) ((Map<String, Object>) createData.get("createRecurringItem")).get("id");

        // Set tags
        var setData = graphqlData(token, """
                mutation {
                    updateRecurringItem(recurringItemId: "%s", input: {
                        tagIds: ["%s"]
                    }) { id tagIds }
                }
                """.formatted(itemId, tagId));
        var setBody = (Map<String, Object>) setData.get("updateRecurringItem");
        assertThat((List<?>) setBody.get("tagIds")).hasSize(1);

        // Clear tags
        var clearData = graphqlData(token, """
                mutation {
                    updateRecurringItem(recurringItemId: "%s", input: {
                        tagIds: []
                    }) { id tagIds }
                }
                """.formatted(itemId));
        var clearBody = (Map<String, Object>) clearData.get("updateRecurringItem");
        assertThat((List<?>) clearBody.get("tagIds")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateRecurringItem_endDate_setAndClear() {
        var createData = graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "EndDateTest", merchantName: "EndDateTest",
                        accountId: "%s", amount: -10.00,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00"
                    }) { id endDate }
                }
                """.formatted(accountId));
        var created = (Map<String, Object>) createData.get("createRecurringItem");
        String itemId = (String) created.get("id");
        assertThat(created.get("endDate")).isNull();

        // Set endDate
        var setData = graphqlData(token, """
                mutation {
                    updateRecurringItem(recurringItemId: "%s", input: {
                        endDate: "2025-12-31T00:00:00"
                    }) { id endDate }
                }
                """.formatted(itemId));
        var setBody = (Map<String, Object>) setData.get("updateRecurringItem");
        assertThat(setBody.get("endDate")).isNotNull();

        // Clear endDate
        var clearData = graphqlData(token, """
                mutation {
                    updateRecurringItem(recurringItemId: "%s", input: {
                        endDate: null
                    }) { id endDate }
                }
                """.formatted(itemId));
        var clearBody = (Map<String, Object>) clearData.get("updateRecurringItem");
        assertThat(clearBody.get("endDate")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteRecurringItem_returns204() {
        var createData = graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "ToDelete", merchantName: "ToDelete",
                        accountId: "%s", amount: -10.00,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00"
                    }) { id }
                }
                """.formatted(accountId));
        String itemId = (String) ((Map<String, Object>) createData.get("createRecurringItem")).get("id");

        var deleteData = graphqlData(token, """
                mutation {
                    deleteRecurringItem(recurringItemId: "%s")
                }
                """.formatted(itemId));
        assertThat(deleteData.get("deleteRecurringItem")).isEqualTo(true);

        // Verify gone
        var resp = graphql(token, """
                { recurringItem(recurringItemId: "%s") { id } }
                """.formatted(itemId));
        assertThat(resp.get("errors")).isNotNull();
    }

    @Test
    void getRecurringItem_notFound_returns404() {
        var resp = graphql(token, """
                { recurringItem(recurringItemId: "%s") { id } }
                """.formatted(UUID.randomUUID()));

        assertThat(resp.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void workspaceIsolation_cannotAccessOtherWorkspaceRecurringItem() {
        var createData = graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "Isolated", merchantName: "Isolated",
                        accountId: "%s", amount: -10.00,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00"
                    }) { id }
                }
                """.formatted(accountId));
        String itemId = (String) ((Map<String, Object>) createData.get("createRecurringItem")).get("id");

        // Switch to different workspace
        String token2 = tokenService.mintToken(userId, UUID.randomUUID());

        var resp = graphql(token2, """
                { recurringItem(recurringItemId: "%s") { id } }
                """.formatted(itemId));

        assertThat(resp.get("errors")).isNotNull();
    }

    @Test
    void createRecurringItem_accountNotInWorkspace_returns404() {
        var resp = graphql(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "BadAccount", merchantName: "BadAccount",
                        accountId: "%s", amount: -10.00,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00"
                    }) { id }
                }
                """.formatted(UUID.randomUUID()));

        assertThat(resp.get("errors")).isNotNull();
    }

    @Test
    void createRecurringItem_categoryNotInWorkspace_returns404() {
        var resp = graphql(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "BadCategory", merchantName: "BadCategory",
                        accountId: "%s", categoryId: "%s", amount: -10.00,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00"
                    }) { id }
                }
                """.formatted(accountId, UUID.randomUUID()));

        assertThat(resp.get("errors")).isNotNull();
    }

    @Test
    void createRecurringItem_tagNotInWorkspace_returns404() {
        var resp = graphql(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "BadTag", merchantName: "BadTag",
                        accountId: "%s", amount: -10.00,
                        frequencyGranularity: MONTH, frequencyQuantity: 1,
                        anchorDates: ["2025-07-15T00:00:00"], startDate: "2025-07-01T00:00:00",
                        tagIds: ["%s"]
                    }) { id }
                }
                """.formatted(accountId, UUID.randomUUID()));

        assertThat(resp.get("errors")).isNotNull();
    }
}
