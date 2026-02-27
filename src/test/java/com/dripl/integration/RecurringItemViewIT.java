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

class RecurringItemViewIT extends BaseIntegrationTest {

    @Autowired private TokenService tokenService;

    private String token;
    private UUID workspaceId;
    private String accountId;
    private String categoryId;

    @BeforeEach
    void setUp() {
        String email = "riview-user-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "RIView", "User");
        token = (String) bootstrap.get("token");
        workspaceId = UUID.fromString((String) bootstrap.get("lastWorkspaceId"));

        var accountResp = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Checking\",\"type\":\"CASH\",\"subType\":\"CHECKING\",\"balance\":5000}", authHeaders(token)),
                Map.class);
        accountId = (String) accountResp.getBody().get("id");

        var categoryResp = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Subscriptions\"}", authHeaders(token)),
                Map.class);
        categoryId = (String) categoryResp.getBody().get("id");
    }

    private void createRecurringItem(String description, String amount, String granularity,
                                      int quantity, String anchorDate, String startDate, String status) {
        String body = "{\"description\":\"%s\",\"merchantName\":\"%s\",\"accountId\":\"%s\",\"categoryId\":\"%s\",\"amount\":%s,\"frequencyGranularity\":\"%s\",\"frequencyQuantity\":%d,\"anchorDates\":[\"%s\"],\"startDate\":\"%s\"}"
                .formatted(description, description, accountId, categoryId, amount, granularity, quantity, anchorDate, startDate);
        var resp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        if (!"ACTIVE".equals(status)) {
            String id = (String) resp.getBody().get("id");
            restTemplate.exchange(
                    "/api/v1/recurring-items/" + id, HttpMethod.PATCH,
                    new HttpEntity<>("{\"status\":\"" + status + "\"}", authHeaders(token)), Map.class);
        }
    }

    @Test
    void getMonthView_defaultMonth_returnsCurrentMonth() {
        createRecurringItem("Netflix", "-14.99", "MONTH", 1, "2026-01-15", "2026-01-01", "ACTIVE");

        var resp = restTemplate.exchange(
                "/api/v1/recurring-items/view", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = resp.getBody();
        assertThat(body.get("monthStart")).isNotNull();
        assertThat(body.get("monthEnd")).isNotNull();
        assertThat(body.get("items")).isNotNull();
        assertThat(body.get("expectedExpenses")).isNotNull();
        assertThat(body.get("expectedIncome")).isNotNull();
        assertThat((int) body.get("itemCount")).isGreaterThanOrEqualTo(0);
    }

    @Test
    void getMonthView_withMonthParam_returnsSpecificMonth() {
        createRecurringItem("Rent", "-1500.00", "MONTH", 1, "2026-03-01", "2026-01-01", "ACTIVE");
        createRecurringItem("Internet", "-79.99", "MONTH", 1, "2026-03-10", "2026-01-01", "ACTIVE");

        var resp = restTemplate.exchange(
                "/api/v1/recurring-items/view?month=2026-03", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = resp.getBody();
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

    @Test
    void getMonthView_withPeriodOffset_returnsRelativeMonth() {
        createRecurringItem("Spotify", "-9.99", "MONTH", 1, "2026-01-20", "2026-01-01", "ACTIVE");

        var resp = restTemplate.exchange(
                "/api/v1/recurring-items/view?periodOffset=0", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((int) resp.getBody().get("itemCount")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void getMonthView_bothParams_returns400() {
        var resp = restTemplate.exchange(
                "/api/v1/recurring-items/view?month=2026-03&periodOffset=1", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getMonthView_inactiveItemsExcluded() {
        createRecurringItem("Active", "-10.00", "MONTH", 1, "2026-03-05", "2026-01-01", "ACTIVE");
        createRecurringItem("Paused", "-20.00", "MONTH", 1, "2026-03-05", "2026-01-01", "PAUSED");
        createRecurringItem("Cancelled", "-30.00", "MONTH", 1, "2026-03-05", "2026-01-01", "CANCELLED");

        var resp = restTemplate.exchange(
                "/api/v1/recurring-items/view?month=2026-03", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((int) resp.getBody().get("itemCount")).isEqualTo(1);
        List<Map<String, Object>> items = (List<Map<String, Object>>) resp.getBody().get("items");
        assertThat(items.get(0).get("description")).isEqualTo("Active");
    }

    @Test
    void getMonthView_emptyMonth_returnsEmptyList() {
        // Yearly item anchored in June — March should be empty
        createRecurringItem("Insurance", "-600.00", "YEAR", 1, "2025-06-15", "2025-06-01", "ACTIVE");

        var resp = restTemplate.exchange(
                "/api/v1/recurring-items/view?month=2026-03", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((int) resp.getBody().get("itemCount")).isZero();
        assertThat((List<?>) resp.getBody().get("items")).isEmpty();
    }

    @Test
    void getMonthView_biweeklyItem_multipleOccurrences() {
        createRecurringItem("Paycheck", "-3000.00", "WEEK", 2, "2026-03-06", "2026-01-01", "ACTIVE");

        var resp = restTemplate.exchange(
                "/api/v1/recurring-items/view?month=2026-03", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = (List<Map<String, Object>>) resp.getBody().get("items");
        assertThat(items).hasSize(1);
        List<Map<String, Object>> occurrences = (List<Map<String, Object>>) items.get(0).get("occurrences");
        assertThat(occurrences.size()).isGreaterThanOrEqualTo(2);
        assertThat((int) resp.getBody().get("occurrenceCount")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void getMonthView_itemFields_populated() {
        createRecurringItem("Netflix", "-14.99", "MONTH", 1, "2026-03-15", "2026-01-01", "ACTIVE");

        var resp = restTemplate.exchange(
                "/api/v1/recurring-items/view?month=2026-03", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = (List<Map<String, Object>>) resp.getBody().get("items");
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

    private String createRecurringItemAndReturnId(String description, String amount, String granularity,
                                                    int quantity, String anchorDate, String startDate) {
        String body = "{\"description\":\"%s\",\"merchantName\":\"%s\",\"accountId\":\"%s\",\"categoryId\":\"%s\",\"amount\":%s,\"frequencyGranularity\":\"%s\",\"frequencyQuantity\":%d,\"anchorDates\":[\"%s\"],\"startDate\":\"%s\"}"
                .formatted(description, description, accountId, categoryId, amount, granularity, quantity, anchorDate, startDate);
        var resp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resp.getBody().get("id");
    }

    @Test
    void createOverride_valid_returns201() {
        String riId = createRecurringItemAndReturnId("Electric", "-120.00", "MONTH", 1, "2026-03-10", "2026-01-01");

        var resp = restTemplate.exchange(
                "/api/v1/recurring-items/" + riId + "/overrides", HttpMethod.POST,
                new HttpEntity<>("{\"occurrenceDate\":\"2026-03-10\",\"amount\":-145.00,\"notes\":\"Higher bill\"}", authHeaders(token)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void createOverride_invalidOccurrenceDate_returns400() {
        String riId = createRecurringItemAndReturnId("Water", "-50.00", "MONTH", 1, "2026-03-15", "2026-01-01");

        var resp = restTemplate.exchange(
                "/api/v1/recurring-items/" + riId + "/overrides", HttpMethod.POST,
                new HttpEntity<>("{\"occurrenceDate\":\"2026-03-20\",\"amount\":-60.00}", authHeaders(token)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteOverride_returns204() {
        String riId = createRecurringItemAndReturnId("Gas", "-80.00", "MONTH", 1, "2026-03-05", "2026-01-01");

        // Create override
        restTemplate.exchange(
                "/api/v1/recurring-items/" + riId + "/overrides", HttpMethod.POST,
                new HttpEntity<>("{\"occurrenceDate\":\"2026-03-05\",\"amount\":-100.00}", authHeaders(token)), Map.class);

        // Get overrideId from view
        var viewResp = restTemplate.exchange(
                "/api/v1/recurring-items/view?month=2026-03", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        List<Map<String, Object>> items = (List<Map<String, Object>>) viewResp.getBody().get("items");
        Map<String, Object> gasItem = items.stream()
                .filter(i -> "Gas".equals(i.get("description")))
                .findFirst().orElseThrow();
        String overrideId = (String) ((List<Map<String, Object>>) gasItem.get("occurrences")).get(0).get("overrideId");
        assertThat(overrideId).isNotNull();

        // Delete override
        var resp = restTemplate.exchange(
                "/api/v1/recurring-items/" + riId + "/overrides/" + overrideId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void monthView_showsOverriddenOccurrence() {
        String riId = createRecurringItemAndReturnId("Electric", "-120.00", "MONTH", 1, "2026-03-10", "2026-01-01");

        // Create override for March occurrence
        restTemplate.exchange(
                "/api/v1/recurring-items/" + riId + "/overrides", HttpMethod.POST,
                new HttpEntity<>("{\"occurrenceDate\":\"2026-03-10\",\"amount\":-180.00,\"notes\":\"Summer spike\"}", authHeaders(token)), Map.class);

        var resp = restTemplate.exchange(
                "/api/v1/recurring-items/view?month=2026-03", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = (List<Map<String, Object>>) resp.getBody().get("items");
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

    @Test
    void monthView_clearedOverrideShowsDefault() {
        String riId = createRecurringItemAndReturnId("Water", "-50.00", "MONTH", 1, "2026-03-20", "2026-01-01");

        // Create override
        restTemplate.exchange(
                "/api/v1/recurring-items/" + riId + "/overrides", HttpMethod.POST,
                new HttpEntity<>("{\"occurrenceDate\":\"2026-03-20\",\"amount\":-75.00}", authHeaders(token)), Map.class);

        // Get overrideId from view
        var viewResp = restTemplate.exchange(
                "/api/v1/recurring-items/view?month=2026-03", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        List<Map<String, Object>> items = (List<Map<String, Object>>) viewResp.getBody().get("items");
        Map<String, Object> waterItem = items.stream()
                .filter(i -> "Water".equals(i.get("description")))
                .findFirst().orElseThrow();
        String overrideId = (String) ((List<Map<String, Object>>) waterItem.get("occurrences")).get(0).get("overrideId");

        // Delete override
        restTemplate.exchange(
                "/api/v1/recurring-items/" + riId + "/overrides/" + overrideId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Map.class);

        var resp = restTemplate.exchange(
                "/api/v1/recurring-items/view?month=2026-03", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        List<Map<String, Object>> itemsAfter = (List<Map<String, Object>>) resp.getBody().get("items");
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
        restTemplate.exchange(
                "/api/v1/recurring-items/" + electricId + "/overrides", HttpMethod.POST,
                new HttpEntity<>("{\"occurrenceDate\":\"2026-03-15\",\"amount\":-200.00}", authHeaders(token)), Map.class);

        var resp = restTemplate.exchange(
                "/api/v1/recurring-items/view?month=2026-03", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // expectedExpenses = -14.99 + -200.00 = -214.99
        double expenses = ((Number) resp.getBody().get("expectedExpenses")).doubleValue();
        assertThat(expenses).isEqualTo(-214.99);
    }

    @Test
    void deleteRecurringItem_cascadesOverrides() {
        String riId = createRecurringItemAndReturnId("Temp", "-50.00", "MONTH", 1, "2026-03-10", "2026-01-01");

        // Create an override
        restTemplate.exchange(
                "/api/v1/recurring-items/" + riId + "/overrides", HttpMethod.POST,
                new HttpEntity<>("{\"occurrenceDate\":\"2026-03-10\",\"amount\":-75.00}", authHeaders(token)), Map.class);

        // Delete the recurring item
        var deleteResp = restTemplate.exchange(
                "/api/v1/recurring-items/" + riId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // View should not include deleted item
        var viewResp = restTemplate.exchange(
                "/api/v1/recurring-items/view?month=2026-03", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        List<Map<String, Object>> items = (List<Map<String, Object>>) viewResp.getBody().get("items");
        assertThat(items.stream().noneMatch(i -> "Temp".equals(i.get("description")))).isTrue();
    }

    @Test
    void monthView_showsPaidOccurrenceWithTransactionDetails() {
        String riId = createRecurringItemAndReturnId("Rent", "-1500.00", "MONTH", 1, "2026-03-10", "2026-01-01");

        // Create a transaction linked to this RI on the occurrence date
        String txnBody = "{\"merchantName\":\"Rent\",\"accountId\":\"%s\",\"categoryId\":\"%s\",\"amount\":-1550.00,\"date\":\"2026-03-10\",\"recurringItemId\":\"%s\",\"occurrenceDate\":\"2026-03-10\"}"
                .formatted(accountId, categoryId, riId);
        var txnResp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>(txnBody, authHeaders(token)), Map.class);
        assertThat(txnResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String txnId = (String) txnResp.getBody().get("id");

        var viewResp = restTemplate.exchange(
                "/api/v1/recurring-items/view?month=2026-03", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        assertThat(viewResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = (List<Map<String, Object>>) viewResp.getBody().get("items");
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

    @Test
    void monthView_paidOccurrenceWithOverrideShowsOverrideAsExpected() {
        String riId = createRecurringItemAndReturnId("Electric", "-120.00", "MONTH", 1, "2026-03-05", "2026-01-01");

        // Create an override
        restTemplate.exchange(
                "/api/v1/recurring-items/" + riId + "/overrides", HttpMethod.POST,
                new HttpEntity<>("{\"occurrenceDate\":\"2026-03-05\",\"amount\":-150.00,\"notes\":\"Expected higher\"}", authHeaders(token)), Map.class);

        // Create a linked transaction (actual amount different from override)
        String txnBody = "{\"merchantName\":\"Electric\",\"accountId\":\"%s\",\"categoryId\":\"%s\",\"amount\":-145.00,\"date\":\"2026-03-05\",\"recurringItemId\":\"%s\",\"occurrenceDate\":\"2026-03-05\"}"
                .formatted(accountId, categoryId, riId);
        restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>(txnBody, authHeaders(token)), Map.class);

        var viewResp = restTemplate.exchange(
                "/api/v1/recurring-items/view?month=2026-03", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        List<Map<String, Object>> items = (List<Map<String, Object>>) viewResp.getBody().get("items");
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

    @Test
    void monthView_unpaidOccurrenceHasNullTransaction() {
        createRecurringItem("Netflix", "-14.99", "MONTH", 1, "2026-03-15", "2026-01-01", "ACTIVE");

        var viewResp = restTemplate.exchange(
                "/api/v1/recurring-items/view?month=2026-03", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        List<Map<String, Object>> items = (List<Map<String, Object>>) viewResp.getBody().get("items");
        Map<String, Object> occ = ((List<Map<String, Object>>) items.get(0).get("occurrences")).get(0);
        assertThat(occ.get("transaction")).isNull();
        assertThat(occ.get("overrideId")).isNull();
    }
}
