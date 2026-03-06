package com.dripl.integration;

import com.dripl.auth.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringItemViewIT extends BaseIntegrationTest {

    @Autowired private TokenService tokenService;

    private String token;
    private UUID workspaceId;
    private String accountId;
    private String categoryId;

    private static final String MONTH_VIEW_FIELDS = """
            monthStart monthEnd expectedExpenses expectedIncome occurrenceCount itemCount
            items {
                recurringItemId description merchantId accountId categoryId amount currencyCode
                status frequencyGranularity frequencyQuantity totalExpected
                occurrences { date expectedAmount overrideId notes transaction { id date amount } }
            }
            """;

    @BeforeEach
    void setUp() {
        String email = "riview-user-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "RIView", "User");
        token = (String) bootstrap.get("token");
        workspaceId = UUID.fromString((String) bootstrap.get("workspaceId"));

        accountId = createAccount(token, "Checking", "CASH", "CHECKING", "5000");

        categoryId = createCategory(token, "Subscriptions");
    }

    @SuppressWarnings("unchecked")
    private void createRecurringItem(String description, String amount, String granularity,
                                      int quantity, String anchorDate, String startDate, String status) {
        var data = graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "%s", merchantName: "%s", accountId: "%s", categoryId: "%s",
                        amount: %s, frequencyGranularity: %s, frequencyQuantity: %d,
                        anchorDates: ["%sT00:00:00"], startDate: "%sT00:00:00"
                    }) { id }
                }
                """.formatted(description, description, accountId, categoryId, amount, granularity, quantity, anchorDate, startDate));
        String id = (String) ((Map<String, Object>) data.get("createRecurringItem")).get("id");

        if (!"ACTIVE".equals(status)) {
            graphqlData(token, """
                    mutation {
                        updateRecurringItem(recurringItemId: "%s", input: { status: %s }) { id }
                    }
                    """.formatted(id, status));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMonthView(String monthParam) {
        String arg = monthParam != null ? "(month: \"%s\")".formatted(monthParam) : "";
        var data = graphqlData(token, "{ recurringItemMonthView%s { %s } }".formatted(arg, MONTH_VIEW_FIELDS));
        return (Map<String, Object>) data.get("recurringItemMonthView");
    }

    @Test
    void getMonthView_defaultMonth_returnsCurrentMonth() {
        createRecurringItem("Netflix", "-14.99", "MONTH", 1, "2026-01-15", "2026-01-01", "ACTIVE");

        var body = getMonthView(null);

        assertThat(body.get("monthStart")).isNotNull();
        assertThat(body.get("monthEnd")).isNotNull();
        assertThat(body.get("items")).isNotNull();
        assertThat(body.get("expectedExpenses")).isNotNull();
        assertThat(body.get("expectedIncome")).isNotNull();
        assertThat((int) body.get("itemCount")).isGreaterThanOrEqualTo(0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getMonthView_withMonthParam_returnsSpecificMonth() {
        createRecurringItem("Rent", "-1500.00", "MONTH", 1, "2026-03-01", "2026-01-01", "ACTIVE");
        createRecurringItem("Internet", "-79.99", "MONTH", 1, "2026-03-10", "2026-01-01", "ACTIVE");

        var body = getMonthView("2026-03");

        assertThat(body.get("monthStart")).isEqualTo("2026-03-01");
        assertThat(body.get("monthEnd")).isEqualTo("2026-03-31");
        assertThat((int) body.get("itemCount")).isEqualTo(2);
        assertThat((int) body.get("occurrenceCount")).isEqualTo(2);

        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertThat(items).hasSize(2);
        // Sorted by first occurrence: Rent (1st) before Internet (10th)
        assertThat(items.get(0).get("description")).isEqualTo("Rent");
        assertThat(items.get(1).get("description")).isEqualTo("Internet");
    }

    @SuppressWarnings("unchecked")
    @Test
    void getMonthView_withPeriodOffset_returnsRelativeMonth() {
        createRecurringItem("Spotify", "-9.99", "MONTH", 1, "2026-01-20", "2026-01-01", "ACTIVE");

        var data = graphqlData(token, "{ recurringItemMonthView(periodOffset: 0) { %s } }".formatted(MONTH_VIEW_FIELDS));
        var body = (Map<String, Object>) data.get("recurringItemMonthView");

        assertThat((int) body.get("itemCount")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void getMonthView_bothParams_returnsError() {
        var resp = graphql(token, "{ recurringItemMonthView(month: \"2026-03\", periodOffset: 1) { monthStart } }");

        assertThat(resp.get("errors")).isNotNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getMonthView_inactiveItemsExcluded() {
        createRecurringItem("Active", "-10.00", "MONTH", 1, "2026-03-05", "2026-01-01", "ACTIVE");
        createRecurringItem("Paused", "-20.00", "MONTH", 1, "2026-03-05", "2026-01-01", "PAUSED");
        createRecurringItem("Cancelled", "-30.00", "MONTH", 1, "2026-03-05", "2026-01-01", "CANCELLED");

        var body = getMonthView("2026-03");

        assertThat((int) body.get("itemCount")).isEqualTo(1);
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertThat(items.get(0).get("description")).isEqualTo("Active");
    }

    @Test
    void getMonthView_emptyMonth_returnsEmptyList() {
        // Yearly item anchored in June — March should be empty
        createRecurringItem("Insurance", "-600.00", "YEAR", 1, "2025-06-15", "2025-06-01", "ACTIVE");

        var body = getMonthView("2026-03");

        assertThat((int) body.get("itemCount")).isZero();
        assertThat((List<?>) body.get("items")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getMonthView_biweeklyItem_multipleOccurrences() {
        createRecurringItem("Paycheck", "-3000.00", "WEEK", 2, "2026-03-06", "2026-01-01", "ACTIVE");

        var body = getMonthView("2026-03");

        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertThat(items).hasSize(1);
        List<Map<String, Object>> occurrences = (List<Map<String, Object>>) items.get(0).get("occurrences");
        assertThat(occurrences.size()).isGreaterThanOrEqualTo(2);
        assertThat((int) body.get("occurrenceCount")).isGreaterThanOrEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getMonthView_itemFields_populated() {
        createRecurringItem("Netflix", "-14.99", "MONTH", 1, "2026-03-15", "2026-01-01", "ACTIVE");

        var body = getMonthView("2026-03");

        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        Map<String, Object> item = items.get(0);
        assertThat(item.get("recurringItemId")).isNotNull();
        assertThat(item.get("description")).isEqualTo("Netflix");
        assertThat(item.get("merchantId")).isNotNull();
        assertThat(item.get("accountId")).isEqualTo(accountId);
        assertThat(item.get("categoryId")).isEqualTo(categoryId);
        assertThat(item.get("currencyCode")).isEqualTo("USD");
        assertThat(item.get("status")).isEqualTo("ACTIVE");
        assertThat(item.get("frequencyGranularity")).isEqualTo("MONTH");
        assertThat(item.get("frequencyQuantity")).isEqualTo(1);
        assertThat(item.get("totalExpected")).isNotNull();
        List<Map<String, Object>> occurrences = (List<Map<String, Object>>) item.get("occurrences");
        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.get(0).get("date")).isEqualTo("2026-03-15");
        assertThat(occurrences.get(0).get("expectedAmount")).isNotNull();
    }

    // --- Override integration tests ---

    @SuppressWarnings("unchecked")
    private String createRecurringItemAndReturnId(String description, String amount, String granularity,
                                                    int quantity, String anchorDate, String startDate) {
        var data = graphqlData(token, """
                mutation {
                    createRecurringItem(input: {
                        description: "%s", merchantName: "%s", accountId: "%s", categoryId: "%s",
                        amount: %s, frequencyGranularity: %s, frequencyQuantity: %d,
                        anchorDates: ["%sT00:00:00"], startDate: "%sT00:00:00"
                    }) { id }
                }
                """.formatted(description, description, accountId, categoryId, amount, granularity, quantity, anchorDate, startDate));
        return (String) ((Map<String, Object>) data.get("createRecurringItem")).get("id");
    }

    private void createOverride(String riId, String occurrenceDate, String amount, String notes) {
        String notesField = notes != null ? ", notes: \"%s\"".formatted(notes) : "";
        graphqlData(token, """
                mutation {
                    createRecurringItemOverride(recurringItemId: "%s", input: {
                        occurrenceDate: "%s", amount: %s%s
                    }) { id }
                }
                """.formatted(riId, occurrenceDate, amount, notesField));
    }

    private void deleteOverride(String riId, String overrideId) {
        graphqlData(token, """
                mutation {
                    deleteRecurringItemOverride(recurringItemId: "%s", overrideId: "%s")
                }
                """.formatted(riId, overrideId));
    }

    @Test
    void createOverride_valid_succeeds() {
        String riId = createRecurringItemAndReturnId("Electric", "-120.00", "MONTH", 1, "2026-03-10", "2026-01-01");

        var data = graphqlData(token, """
                mutation {
                    createRecurringItemOverride(recurringItemId: "%s", input: {
                        occurrenceDate: "2026-03-10", amount: -145.00, notes: "Higher bill"
                    }) { id }
                }
                """.formatted(riId));

        assertThat(data.get("createRecurringItemOverride")).isNotNull();
    }

    @Test
    void createOverride_invalidOccurrenceDate_returnsError() {
        String riId = createRecurringItemAndReturnId("Water", "-50.00", "MONTH", 1, "2026-03-15", "2026-01-01");

        var resp = graphql(token, """
                mutation {
                    createRecurringItemOverride(recurringItemId: "%s", input: {
                        occurrenceDate: "2026-03-20", amount: -60.00
                    }) { id }
                }
                """.formatted(riId));

        assertThat(resp.get("errors")).isNotNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteOverride_succeeds() {
        String riId = createRecurringItemAndReturnId("Gas", "-80.00", "MONTH", 1, "2026-03-05", "2026-01-01");

        // Create override
        createOverride(riId, "2026-03-05", "-100.00", null);

        // Get overrideId from view
        var body = getMonthView("2026-03");
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        Map<String, Object> gasItem = items.stream()
                .filter(i -> "Gas".equals(i.get("description")))
                .findFirst().orElseThrow();
        String overrideId = (String) ((List<Map<String, Object>>) gasItem.get("occurrences")).get(0).get("overrideId");
        assertThat(overrideId).isNotNull();

        // Delete override
        var data = graphqlData(token, """
                mutation {
                    deleteRecurringItemOverride(recurringItemId: "%s", overrideId: "%s")
                }
                """.formatted(riId, overrideId));

        assertThat(data.get("deleteRecurringItemOverride")).isEqualTo(true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void monthView_showsOverriddenOccurrence() {
        String riId = createRecurringItemAndReturnId("Electric", "-120.00", "MONTH", 1, "2026-03-10", "2026-01-01");

        // Create override for March occurrence
        createOverride(riId, "2026-03-10", "-180.00", "Summer spike");

        var body = getMonthView("2026-03");

        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        Map<String, Object> electricItem = items.stream()
                .filter(i -> "Electric".equals(i.get("description")))
                .findFirst().orElseThrow();

        List<Map<String, Object>> occurrences = (List<Map<String, Object>>) electricItem.get("occurrences");
        assertThat(occurrences).hasSize(1);
        Map<String, Object> occ = occurrences.get(0);
        assertThat(occ.get("date")).isEqualTo("2026-03-10");
        assertThat(((Number) occ.get("expectedAmount")).doubleValue()).isEqualTo(-180.00);
        assertThat(occ.get("overrideId")).isNotNull();
        assertThat(occ.get("notes")).isEqualTo("Summer spike");
        assertThat(((Number) electricItem.get("totalExpected")).doubleValue()).isEqualTo(-180.00);
    }

    @SuppressWarnings("unchecked")
    @Test
    void monthView_clearedOverrideShowsDefault() {
        String riId = createRecurringItemAndReturnId("Water", "-50.00", "MONTH", 1, "2026-03-20", "2026-01-01");

        // Create override
        createOverride(riId, "2026-03-20", "-75.00", null);

        // Get overrideId from view
        var body = getMonthView("2026-03");
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        Map<String, Object> waterItem = items.stream()
                .filter(i -> "Water".equals(i.get("description")))
                .findFirst().orElseThrow();
        String overrideId = (String) ((List<Map<String, Object>>) waterItem.get("occurrences")).get(0).get("overrideId");

        // Delete override
        deleteOverride(riId, overrideId);

        var bodyAfter = getMonthView("2026-03");

        List<Map<String, Object>> itemsAfter = (List<Map<String, Object>>) bodyAfter.get("items");
        Map<String, Object> waterItemAfter = itemsAfter.stream()
                .filter(i -> "Water".equals(i.get("description")))
                .findFirst().orElseThrow();

        List<Map<String, Object>> occurrences = (List<Map<String, Object>>) waterItemAfter.get("occurrences");
        Map<String, Object> occ = occurrences.get(0);
        assertThat(((Number) occ.get("expectedAmount")).doubleValue()).isEqualTo(-50.00);
        assertThat(occ.get("overrideId")).isNull();
        assertThat(occ.get("notes")).isNull();
    }

    @Test
    void monthView_overrideAffectsTotals() {
        // Two items: one default, one overridden
        createRecurringItem("Netflix", "-14.99", "MONTH", 1, "2026-03-01", "2026-01-01", "ACTIVE");
        String electricId = createRecurringItemAndReturnId("Electric", "-120.00", "MONTH", 1, "2026-03-15", "2026-01-01");

        // Override electric from -120 to -200
        createOverride(electricId, "2026-03-15", "-200.00", null);

        var body = getMonthView("2026-03");

        // expectedExpenses = -14.99 + -200.00 = -214.99
        double expenses = ((Number) body.get("expectedExpenses")).doubleValue();
        assertThat(expenses).isEqualTo(-214.99);
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteRecurringItem_cascadesOverrides() {
        String riId = createRecurringItemAndReturnId("Temp", "-50.00", "MONTH", 1, "2026-03-10", "2026-01-01");

        // Create an override
        createOverride(riId, "2026-03-10", "-75.00", null);

        // Delete the recurring item
        var data = graphqlData(token, """
                mutation {
                    deleteRecurringItem(recurringItemId: "%s")
                }
                """.formatted(riId));
        assertThat(data.get("deleteRecurringItem")).isEqualTo(true);

        // View should not include deleted item
        var body = getMonthView("2026-03");

        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertThat(items.stream().noneMatch(i -> "Temp".equals(i.get("description")))).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void monthView_showsPaidOccurrenceWithTransactionDetails() {
        String riId = createRecurringItemAndReturnId("Rent", "-1500.00", "MONTH", 1, "2026-03-10", "2026-01-01");

        // Create a transaction linked to this RI on the occurrence date
        var txnData = (Map<String, Object>) graphqlData(token, """
                mutation {
                    createTransaction(input: {
                        merchantName: "Rent", accountId: "%s", categoryId: "%s",
                        amount: -1550.00, date: "2026-03-10T00:00:00",
                        recurringItemId: "%s", occurrenceDate: "2026-03-10"
                    }) { id }
                }
                """.formatted(accountId, categoryId, riId)).get("createTransaction");
        String txnId = (String) txnData.get("id");

        var body = getMonthView("2026-03");

        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        Map<String, Object> rentItem = items.stream()
                .filter(i -> "Rent".equals(i.get("description")))
                .findFirst().orElseThrow();

        List<Map<String, Object>> occurrences = (List<Map<String, Object>>) rentItem.get("occurrences");
        Map<String, Object> occ = occurrences.get(0);
        assertThat(occ.get("transaction")).isNotNull();
        Map<String, Object> txn = (Map<String, Object>) occ.get("transaction");
        assertThat(txn.get("id")).isEqualTo(txnId);
        assertThat(((Number) txn.get("amount")).doubleValue()).isEqualTo(-1550.00);
        assertThat(((Number) occ.get("expectedAmount")).doubleValue()).isEqualTo(-1500.00);
    }

    @SuppressWarnings("unchecked")
    @Test
    void monthView_paidOccurrenceWithOverrideShowsOverrideAsExpected() {
        String riId = createRecurringItemAndReturnId("Electric", "-120.00", "MONTH", 1, "2026-03-05", "2026-01-01");

        // Create an override
        createOverride(riId, "2026-03-05", "-150.00", "Expected higher");

        // Create a linked transaction (actual amount different from override)
        graphql(token, """
                mutation {
                    createTransaction(input: {
                        merchantName: "Electric", accountId: "%s", categoryId: "%s",
                        amount: -145.00, date: "2026-03-05T00:00:00",
                        recurringItemId: "%s", occurrenceDate: "2026-03-05"
                    }) { id }
                }
                """.formatted(accountId, categoryId, riId));

        var body = getMonthView("2026-03");

        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        Map<String, Object> electricItem = items.stream()
                .filter(i -> "Electric".equals(i.get("description")))
                .findFirst().orElseThrow();

        List<Map<String, Object>> occurrences = (List<Map<String, Object>>) electricItem.get("occurrences");
        Map<String, Object> occ = occurrences.get(0);
        assertThat(occ.get("transaction")).isNotNull();
        assertThat(occ.get("overrideId")).isNotNull();
        // expectedAmount is the override amount, not the transaction amount
        assertThat(((Number) occ.get("expectedAmount")).doubleValue()).isEqualTo(-150.00);
        Map<String, Object> txn = (Map<String, Object>) occ.get("transaction");
        assertThat(((Number) txn.get("amount")).doubleValue()).isEqualTo(-145.00);
    }

    @SuppressWarnings("unchecked")
    @Test
    void monthView_unpaidOccurrenceHasNullTransaction() {
        createRecurringItem("Netflix", "-14.99", "MONTH", 1, "2026-03-15", "2026-01-01", "ACTIVE");

        var body = getMonthView("2026-03");

        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        Map<String, Object> occ = ((List<Map<String, Object>>) items.get(0).get("occurrences")).get(0);
        assertThat(occ.get("transaction")).isNull();
        assertThat(occ.get("overrideId")).isNull();
    }
}
