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
        assertThat(occurrences.get(0).get("amount")).isNotNull();
    }
}
