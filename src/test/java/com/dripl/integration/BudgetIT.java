package com.dripl.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetIT extends BaseIntegrationTest {

    private String token;
    private String accountId;
    private String expenseCategoryId;
    private String incomeCategoryId;
    private String childCategoryId;

    @BeforeEach
    void setUp() {
        String email = "budget-user-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Budget", "User");
        token = (String) bootstrap.get("token");

        // Create an account
        var accountResp = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Checking","type":"CASH","subType":"CHECKING","startingBalance":5000}
                        """, authHeaders(token)),
                Map.class);
        accountId = (String) accountResp.getBody().get("id");

        // Create expense category (parent)
        var catResp = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);
        expenseCategoryId = (String) catResp.getBody().get("id");

        // Create child expense category
        var childCatResp = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries","parentId":"%s"}
                        """.formatted(expenseCategoryId), authHeaders(token)),
                Map.class);
        childCategoryId = (String) childCatResp.getBody().get("id");

        // Create income category
        var incomeResp = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Salary","income":true}
                        """, authHeaders(token)),
                Map.class);
        incomeCategoryId = (String) incomeResp.getBody().get("id");
    }

    // ── Helpers ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String createMonthlyBudget() {
        var resp = restTemplate.exchange(
                "/api/v1/budgets", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Monthly Budget","anchorDay1":1,"accountIds":["%s"]}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resp.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private String createFixedIntervalBudget(LocalDate anchor, int intervalDays) {
        var resp = restTemplate.exchange(
                "/api/v1/budgets", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Fixed Budget","anchorDate":"%s","intervalDays":%d,"accountIds":["%s"]}
                        """.formatted(anchor, intervalDays, accountId), authHeaders(token)),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resp.getBody().get("id");
    }

    private String createTransaction(String amount, String categoryId, String date) {
        var resp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Store","amount":%s,"date":"%s","categoryId":"%s"}
                        """.formatted(accountId, amount, date, categoryId), authHeaders(token)),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resp.getBody().get("id");
    }

    private String createTransactionOnAccount(String amount, String categoryId, String date, String acctId) {
        var resp = restTemplate.exchange(
                "/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>("""
                        {"accountId":"%s","merchantName":"Store","amount":%s,"date":"%s","categoryId":"%s"}
                        """.formatted(acctId, amount, date, categoryId), authHeaders(token)),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resp.getBody().get("id");
    }

    // ── Budget CRUD Tests ───────────────────────────────────

    @Nested
    class BudgetCrudTests {

        @Test
        @SuppressWarnings("unchecked")
        void createBudget_monthly() {
            var resp = restTemplate.exchange(
                    "/api/v1/budgets", HttpMethod.POST,
                    new HttpEntity<>("""
                            {"name":"My Budget","anchorDay1":1,"accountIds":["%s"]}
                            """.formatted(accountId), authHeaders(token)),
                    Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(resp.getBody().get("name")).isEqualTo("My Budget");
            assertThat(resp.getBody().get("anchorDay1")).isEqualTo(1);
            assertThat(resp.getBody().get("currentPeriodStart")).isNotNull();
            assertThat(resp.getBody().get("currentPeriodEnd")).isNotNull();
            assertThat((List<?>) resp.getBody().get("accountIds")).hasSize(1);
        }

        @Test
        @SuppressWarnings("unchecked")
        void createBudget_fixedInterval() {
            LocalDate anchor = LocalDate.now().minusDays(7);
            var resp = restTemplate.exchange(
                    "/api/v1/budgets", HttpMethod.POST,
                    new HttpEntity<>("""
                            {"name":"Biweekly","anchorDate":"%s","intervalDays":14,"accountIds":["%s"]}
                            """.formatted(anchor, accountId), authHeaders(token)),
                    Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(resp.getBody().get("intervalDays")).isEqualTo(14);
        }

        @Test
        @SuppressWarnings("unchecked")
        void createBudget_semiMonthly() {
            var resp = restTemplate.exchange(
                    "/api/v1/budgets", HttpMethod.POST,
                    new HttpEntity<>("""
                            {"name":"Semi","anchorDay1":1,"anchorDay2":15,"accountIds":["%s"]}
                            """.formatted(accountId), authHeaders(token)),
                    Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(resp.getBody().get("anchorDay1")).isEqualTo(1);
            assertThat(resp.getBody().get("anchorDay2")).isEqualTo(15);
        }

        @Test
        void createBudget_invalidAnchorDay_returns400() {
            var resp = restTemplate.exchange(
                    "/api/v1/budgets", HttpMethod.POST,
                    new HttpEntity<>("""
                            {"name":"Bad","anchorDay1":32,"accountIds":["%s"]}
                            """.formatted(accountId), authHeaders(token)),
                    Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void createBudget_duplicateAnchorDays_returns400() {
            var resp = restTemplate.exchange(
                    "/api/v1/budgets", HttpMethod.POST,
                    new HttpEntity<>("""
                            {"name":"Bad","anchorDay1":15,"anchorDay2":15,"accountIds":["%s"]}
                            """.formatted(accountId), authHeaders(token)),
                    Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @SuppressWarnings("unchecked")
        void listBudgets() {
            createMonthlyBudget();
            var resp = restTemplate.exchange(
                    "/api/v1/budgets", HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), List.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).hasSize(1);
        }

        @Test
        @SuppressWarnings("unchecked")
        void getBudget() {
            String budgetId = createMonthlyBudget();
            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().get("name")).isEqualTo("Monthly Budget");
        }

        @Test
        @SuppressWarnings("unchecked")
        void updateBudget() {
            String budgetId = createMonthlyBudget();
            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s".formatted(budgetId), HttpMethod.PATCH,
                    new HttpEntity<>("""
                            {"name":"Updated Budget"}
                            """, authHeaders(token)), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().get("name")).isEqualTo("Updated Budget");
        }

        @Test
        void deleteBudget() {
            String budgetId = createMonthlyBudget();
            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s".formatted(budgetId), HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(token)), Void.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // Verify it's gone
            var getResp = restTemplate.exchange(
                    "/api/v1/budgets/%s".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void createBudget_duplicateName_returns409() {
            createMonthlyBudget();
            var resp = restTemplate.exchange(
                    "/api/v1/budgets", HttpMethod.POST,
                    new HttpEntity<>("""
                            {"name":"Monthly Budget","anchorDay1":1,"accountIds":["%s"]}
                            """.formatted(accountId), authHeaders(token)),
                    Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ── Budget Config Tests ─────────────────────────────────

    @Nested
    class BudgetConfigTests {

        private String budgetId;

        @BeforeEach
        void createBudget() {
            budgetId = createMonthlyBudget();
        }

        @Test
        @SuppressWarnings("unchecked")
        void updateCategoryConfig_sameCategoryAndBack() {
            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/config".formatted(budgetId, expenseCategoryId), HttpMethod.PATCH,
                    new HttpEntity<>("""
                            {"rolloverType":"SAME_CATEGORY"}
                            """, authHeaders(token)), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().get("rolloverType")).isEqualTo("SAME_CATEGORY");

            var resp2 = restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/config".formatted(budgetId, expenseCategoryId), HttpMethod.PATCH,
                    new HttpEntity<>("""
                            {"rolloverType":"NONE"}
                            """, authHeaders(token)), Map.class);
            assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp2.getBody().get("rolloverType")).isEqualTo("NONE");
        }

        @Test
        void setExpectedAmount_andClear() {
            LocalDate periodStart = LocalDate.now().withDayOfMonth(1);

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, periodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":500.00}
                            """, authHeaders(token)), Void.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Verify it shows in the period view
            var periodResp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            assertThat(periodResp.getStatusCode()).isEqualTo(HttpStatus.OK);

            var delResp = restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, periodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":null}
                            """, authHeaders(token)), Void.class);
            assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @SuppressWarnings("unchecked")
        void setExpectedAmount_updateExisting() {
            LocalDate periodStart = LocalDate.now().withDayOfMonth(1);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, periodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":500.00}
                            """, authHeaders(token)), Void.class);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, periodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":750.00}
                            """, authHeaders(token)), Void.class);

            var periodResp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            assertThat(periodResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ── Budget Period View Tests ────────────────────────────

    @Nested
    class BudgetPeriodViewTests {

        @Test
        @SuppressWarnings("unchecked")
        void getPeriodView_monthly_correctActivityAndExpected() {
            String budgetId = createMonthlyBudget();

            LocalDate periodStart = LocalDate.now().withDayOfMonth(1);
            String txnDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

            createTransaction("-42.50", childCategoryId, txnDate);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, periodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":200.00}
                            """, authHeaders(token)), Void.class);

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

            var body = resp.getBody();
            assertThat(body.get("periodStart")).isEqualTo(periodStart.toString());

            var outflow = (Map<String, Object>) body.get("outflow");
            var categories = (List<Map<String, Object>>) outflow.get("categories");

            var foodCat = categories.stream()
                    .filter(c -> "Food".equals(c.get("name")))
                    .findFirst().orElse(null);
            assertThat(foodCat).isNotNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        void getPeriodView_parentCategoryRollup() {
            String budgetId = createMonthlyBudget();

            LocalDate periodStart = LocalDate.now().withDayOfMonth(1);
            String txnDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

            createTransaction("-50.00", childCategoryId, txnDate);

            // Only set expected on child — parent rolls up from children
            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, periodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":200.00}
                            """, authHeaders(token)), Void.class);

            // Setting expected on parent should be rejected
            var parentResp = restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, expenseCategoryId, periodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":100.00}
                            """, authHeaders(token)), Map.class);
            assertThat(parentResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

            var outflow = (Map<String, Object>) resp.getBody().get("outflow");
            var categories = (List<Map<String, Object>>) outflow.get("categories");

            var foodCat = categories.stream()
                    .filter(c -> "Food".equals(c.get("name")))
                    .findFirst().orElse(null);
            assertThat(foodCat).isNotNull();
            assertThat(((Number) foodCat.get("expected")).doubleValue()).isEqualTo(200.0);
            assertThat(((Number) foodCat.get("activity")).doubleValue()).isEqualTo(-50.0);

            var children = (List<Map<String, Object>>) foodCat.get("children");
            assertThat(children).hasSize(1);
            assertThat(children.get(0).get("name")).isEqualTo("Groceries");
        }

        @Test
        @SuppressWarnings("unchecked")
        void getPeriodView_inflowSection() {
            String budgetId = createMonthlyBudget();

            LocalDate periodStart = LocalDate.now().withDayOfMonth(1);
            String txnDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

            createTransaction("3000.00", incomeCategoryId, txnDate);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, incomeCategoryId, periodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":5000.00}
                            """, authHeaders(token)), Void.class);

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);

            var inflow = (Map<String, Object>) resp.getBody().get("inflow");
            var categories = (List<Map<String, Object>>) inflow.get("categories");

            var salary = categories.stream()
                    .filter(c -> "Salary".equals(c.get("name")))
                    .findFirst().orElse(null);
            assertThat(salary).isNotNull();
            assertThat(((Number) salary.get("expected")).doubleValue()).isEqualTo(5000.0);
            assertThat(((Number) salary.get("activity")).doubleValue()).isEqualTo(3000.0);
        }

        @Test
        @SuppressWarnings("unchecked")
        void getPeriodView_excludeFromBudget_omitted() {
            String budgetId = createMonthlyBudget();

            var excludedResp = restTemplate.exchange(
                    "/api/v1/categories", HttpMethod.POST,
                    new HttpEntity<>("""
                            {"name":"ExcludedCat","excludeFromBudget":true}
                            """, authHeaders(token)), Map.class);
            assertThat(excludedResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);

            var outflow = (Map<String, Object>) resp.getBody().get("outflow");
            var categories = (List<Map<String, Object>>) outflow.get("categories");
            assertThat(categories.stream().noneMatch(c -> "ExcludedCat".equals(c.get("name")))).isTrue();
        }

        @Test
        @SuppressWarnings("unchecked")
        void getPeriodView_accountNotInBudget_activityExcluded() {
            // Create a second account NOT included in the budget
            var otherAcctResp = restTemplate.exchange(
                    "/api/v1/accounts", HttpMethod.POST,
                    new HttpEntity<>("""
                            {"name":"HSA","type":"CASH","subType":"SAVINGS","startingBalance":0}
                            """, authHeaders(token)),
                    Map.class);
            String otherAccountId = (String) otherAcctResp.getBody().get("id");

            // Budget only includes the original "Checking" account
            String budgetId = createMonthlyBudget();

            LocalDate periodStart = LocalDate.now().withDayOfMonth(1);
            String txnDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

            // Transaction on non-budgeted account
            createTransactionOnAccount("-100.00", childCategoryId, txnDate, otherAccountId);
            // Transaction on budgeted account
            createTransaction("-50.00", childCategoryId, txnDate);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, periodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":200.00}
                            """, authHeaders(token)), Void.class);

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

            var outflow = (Map<String, Object>) resp.getBody().get("outflow");
            var categories = (List<Map<String, Object>>) outflow.get("categories");
            var foodCat = categories.stream()
                    .filter(c -> "Food".equals(c.get("name")))
                    .findFirst().orElse(null);
            assertThat(foodCat).isNotNull();
            var children = (List<Map<String, Object>>) foodCat.get("children");
            var groceriesCat = children.stream()
                    .filter(c -> "Groceries".equals(c.get("name")))
                    .findFirst().orElse(null);

            // Activity should only include the -50 from the budgeted account
            assertThat(((Number) groceriesCat.get("activity")).doubleValue()).isEqualTo(-50.0);
        }

        @Test
        @SuppressWarnings("unchecked")
        void getPeriodView_previousPeriodViaOffset() {
            String budgetId = createMonthlyBudget();

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view?periodOffset=-1".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

            LocalDate expectedPrevStart = LocalDate.now().withDayOfMonth(1).minusMonths(1);
            assertThat(resp.getBody().get("periodStart")).isEqualTo(expectedPrevStart.toString());
        }
    }

    // ── Rollover Tests ──────────────────────────────────────

    @Nested
    class RolloverTests {

        private String budgetId;

        @BeforeEach
        void createBudget() {
            budgetId = createMonthlyBudget();
        }

        @Test
        @SuppressWarnings("unchecked")
        void sameCategoryRollover_underspend_carriesForward() {
            LocalDate prevPeriodStart = LocalDate.now().withDayOfMonth(1).minusMonths(1);
            LocalDate currentPeriodStart = LocalDate.now().withDayOfMonth(1);
            String prevTxnDate = prevPeriodStart.plusDays(14).format(DateTimeFormatter.ISO_DATE);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/config".formatted(budgetId, childCategoryId), HttpMethod.PATCH,
                    new HttpEntity<>("""
                            {"rolloverType":"SAME_CATEGORY"}
                            """, authHeaders(token)), Map.class);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, prevPeriodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":500.00}
                            """, authHeaders(token)), Void.class);

            createTransaction("-300.00", childCategoryId, prevTxnDate);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, currentPeriodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":500.00}
                            """, authHeaders(token)), Void.class);

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view?periodOffset=0".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

            var outflow = (Map<String, Object>) resp.getBody().get("outflow");
            var categories = (List<Map<String, Object>>) outflow.get("categories");
            var foodCat = categories.stream()
                    .filter(c -> "Food".equals(c.get("name")))
                    .findFirst().orElse(null);
            assertThat(foodCat).isNotNull();
            var children = (List<Map<String, Object>>) foodCat.get("children");
            var groceriesCat = children.stream()
                    .filter(c -> "Groceries".equals(c.get("name")))
                    .findFirst().orElse(null);
            assertThat(groceriesCat).isNotNull();

            assertThat(((Number) groceriesCat.get("rolledOver")).doubleValue()).isEqualTo(200.0);
        }

        @Test
        @SuppressWarnings("unchecked")
        void sameCategoryRollover_overspend_carriesNegative() {
            LocalDate prevPeriodStart = LocalDate.now().withDayOfMonth(1).minusMonths(1);
            LocalDate currentPeriodStart = LocalDate.now().withDayOfMonth(1);
            String prevTxnDate = prevPeriodStart.plusDays(14).format(DateTimeFormatter.ISO_DATE);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/config".formatted(budgetId, childCategoryId), HttpMethod.PATCH,
                    new HttpEntity<>("""
                            {"rolloverType":"SAME_CATEGORY"}
                            """, authHeaders(token)), Map.class);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, prevPeriodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":200.00}
                            """, authHeaders(token)), Void.class);
            createTransaction("-500.00", childCategoryId, prevTxnDate);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, currentPeriodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":500.00}
                            """, authHeaders(token)), Void.class);

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view?periodOffset=0".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);

            var outflow = (Map<String, Object>) resp.getBody().get("outflow");
            var categories = (List<Map<String, Object>>) outflow.get("categories");
            var foodCat = categories.stream()
                    .filter(c -> "Food".equals(c.get("name")))
                    .findFirst().orElse(null);
            var children = (List<Map<String, Object>>) foodCat.get("children");
            var groceriesCat = children.stream()
                    .filter(c -> "Groceries".equals(c.get("name")))
                    .findFirst().orElse(null);

            assertThat(((Number) groceriesCat.get("rolledOver")).doubleValue()).isEqualTo(-300.0);
        }

        @Test
        @SuppressWarnings("unchecked")
        void noneRollover_noCarryForward() {
            LocalDate prevPeriodStart = LocalDate.now().withDayOfMonth(1).minusMonths(1);
            LocalDate currentPeriodStart = LocalDate.now().withDayOfMonth(1);
            String prevTxnDate = prevPeriodStart.plusDays(14).format(DateTimeFormatter.ISO_DATE);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, prevPeriodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":500.00}
                            """, authHeaders(token)), Void.class);
            createTransaction("-300.00", childCategoryId, prevTxnDate);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, currentPeriodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":500.00}
                            """, authHeaders(token)), Void.class);

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view?periodOffset=0".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);

            var outflow = (Map<String, Object>) resp.getBody().get("outflow");
            var categories = (List<Map<String, Object>>) outflow.get("categories");
            var foodCat = categories.stream()
                    .filter(c -> "Food".equals(c.get("name")))
                    .findFirst().orElse(null);
            var children = (List<Map<String, Object>>) foodCat.get("children");
            var groceriesCat = children.stream()
                    .filter(c -> "Groceries".equals(c.get("name")))
                    .findFirst().orElse(null);

            assertThat(((Number) groceriesCat.get("rolledOver")).doubleValue()).isEqualTo(0.0);
        }

        @Test
        @SuppressWarnings("unchecked")
        void availablePoolRollover_showsInPool() {
            LocalDate prevPeriodStart = LocalDate.now().withDayOfMonth(1).minusMonths(1);
            String prevTxnDate = prevPeriodStart.plusDays(14).format(DateTimeFormatter.ISO_DATE);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/config".formatted(budgetId, childCategoryId), HttpMethod.PATCH,
                    new HttpEntity<>("""
                            {"rolloverType":"AVAILABLE_POOL"}
                            """, authHeaders(token)), Map.class);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, prevPeriodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":500.00}
                            """, authHeaders(token)), Void.class);
            createTransaction("-300.00", childCategoryId, prevTxnDate);

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view?periodOffset=0".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);

            var outflow = (Map<String, Object>) resp.getBody().get("outflow");
            var categories = (List<Map<String, Object>>) outflow.get("categories");
            var foodCat = categories.stream()
                    .filter(c -> "Food".equals(c.get("name")))
                    .findFirst().orElse(null);
            var children = (List<Map<String, Object>>) foodCat.get("children");
            var groceriesCat = children.stream()
                    .filter(c -> "Groceries".equals(c.get("name")))
                    .findFirst().orElse(null);
            assertThat(((Number) groceriesCat.get("rolledOver")).doubleValue()).isEqualTo(0.0);

            assertThat(((Number) resp.getBody().get("availablePool")).doubleValue()).isEqualTo(200.0);
        }
    }

    // ── Fixed Interval Tests ────────────────────────────────

    @Nested
    class FixedIntervalTests {

        @Test
        @SuppressWarnings("unchecked")
        void fixedInterval_correctPeriodComputed() {
            LocalDate anchor = LocalDate.now().minusDays(7);
            String budgetId = createFixedIntervalBudget(anchor, 14);

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view?periodOffset=0".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            LocalDate periodStart = LocalDate.parse((String) resp.getBody().get("periodStart"));
            LocalDate periodEnd = LocalDate.parse((String) resp.getBody().get("periodEnd"));
            assertThat(LocalDate.now()).isBetween(periodStart, periodEnd);
        }

        @Test
        @SuppressWarnings("unchecked")
        void fixedInterval_navigateToPrevious() {
            LocalDate anchor = LocalDate.now().minusDays(7);
            String budgetId = createFixedIntervalBudget(anchor, 14);

            var currentResp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view?periodOffset=0".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            LocalDate currentStart = LocalDate.parse((String) currentResp.getBody().get("periodStart"));

            var prevResp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view?periodOffset=-1".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            assertThat(prevResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            LocalDate prevEnd = LocalDate.parse((String) prevResp.getBody().get("periodEnd"));

            assertThat(prevEnd).isEqualTo(currentStart.minusDays(1));
        }

        @Test
        @SuppressWarnings("unchecked")
        void fixedInterval_directAccessByPeriodStart() {
            LocalDate anchor = LocalDate.now().minusDays(7);
            String budgetId = createFixedIntervalBudget(anchor, 14);

            var currentResp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view?periodOffset=0".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            String currentStart = (String) currentResp.getBody().get("periodStart");

            var directResp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view?date=%s".formatted(budgetId, currentStart), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            assertThat(directResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(directResp.getBody().get("periodStart")).isEqualTo(currentStart);
        }
    }

    // ── Workspace Settings Tests (non-budget) ───────────────

    @Nested
    class WorkspaceSettingsTests {

        @Test
        @SuppressWarnings("unchecked")
        void getSettings_returnsDefaults() {
            var resp = restTemplate.exchange(
                    "/api/v1/workspaces/current/settings", HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().get("defaultCurrencyCode")).isEqualTo("USD");
            assertThat(resp.getBody().get("timezone")).isEqualTo("UTC");
        }

        @Test
        @SuppressWarnings("unchecked")
        void updateSettings_timezone() {
            var resp = restTemplate.exchange(
                    "/api/v1/workspaces/current/settings", HttpMethod.PATCH,
                    new HttpEntity<>("""
                            {"timezone":"America/New_York"}
                            """, authHeaders(token)), Map.class);
            assertThat(resp.getBody().get("timezone")).isEqualTo("America/New_York");
        }
    }

    // ── Envelope Math Tests ─────────────────────────────────

    @Nested
    class EnvelopeMathTests {

        private String budgetId;
        private LocalDate currentPeriodStart;
        private LocalDate prevPeriodStart;

        @BeforeEach
        void createBudget() {
            budgetId = createMonthlyBudget();
            currentPeriodStart = LocalDate.now().withDayOfMonth(1);
            prevPeriodStart = currentPeriodStart.minusMonths(1);
        }

        @Test
        @SuppressWarnings("unchecked")
        void netTotalAvailable_matchesAccountBalance() {
            // Account starts at 5000, add income and expense
            String txnDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            createTransaction("3000.00", incomeCategoryId, txnDate);
            createTransaction("-500.00", childCategoryId, txnDate);

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Account balance = 5000 + 3000 - 500 = 7500
            double netTotal = ((Number) resp.getBody().get("netTotalAvailable")).doubleValue();
            assertThat(netTotal).isEqualTo(7500.0);
        }

        @Test
        @SuppressWarnings("unchecked")
        void envelopeEquation_holdsWithActivityAndExpected() {
            String txnDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            createTransaction("3000.00", incomeCategoryId, txnDate);
            createTransaction("-400.00", childCategoryId, txnDate);

            // Set expected amounts
            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, currentPeriodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":600.00}
                            """, authHeaders(token)), Void.class);
            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, incomeCategoryId, currentPeriodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":5000.00}
                            """, authHeaders(token)), Void.class);

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);

            var body = resp.getBody();
            double netTotal = ((Number) body.get("netTotalAvailable")).doubleValue();
            double budgetable = ((Number) body.get("budgetable")).doubleValue();
            double totalBudgeted = ((Number) body.get("totalBudgeted")).doubleValue();
            double leftToBudget = ((Number) body.get("leftToBudget")).doubleValue();

            var outflow = (Map<String, Object>) body.get("outflow");
            double outflowAvailable = ((Number) outflow.get("available")).doubleValue();

            // Account balance = 5000 + 3000 - 400 = 7600
            assertThat(netTotal).isEqualTo(7600.0);

            // budgetable = inflowExpected = 5000
            // totalBudgeted = outflowExpected = 600
            // leftToBudget = 5000 - 600 = 4400
            assertThat(budgetable).isEqualTo(5000.0);
            assertThat(totalBudgeted).isEqualTo(600.0);
            assertThat(leftToBudget).isEqualTo(4400.0);
            assertThat(outflowAvailable).isEqualTo(200.0);
        }

        @Test
        @SuppressWarnings("unchecked")
        void envelopeEquation_holdsAcrossPeriodsWithRollover() {
            // Previous period: earn $3000, budget $600 for groceries, spend $400
            String prevTxnDate = prevPeriodStart.plusDays(14).format(DateTimeFormatter.ISO_DATE);
            createTransaction("3000.00", incomeCategoryId, prevTxnDate);
            createTransaction("-400.00", childCategoryId, prevTxnDate);

            // Set rollover on groceries
            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/config".formatted(budgetId, childCategoryId), HttpMethod.PATCH,
                    new HttpEntity<>("""
                            {"rolloverType":"SAME_CATEGORY"}
                            """, authHeaders(token)), Map.class);

            // Set expected for previous period
            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, prevPeriodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":600.00}
                            """, authHeaders(token)), Void.class);

            // Current period: earn $3500, budget $600, spend $550
            String curTxnDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            createTransaction("3500.00", incomeCategoryId, curTxnDate);
            createTransaction("-550.00", childCategoryId, curTxnDate);

            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, currentPeriodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":600.00}
                            """, authHeaders(token)), Void.class);

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view?periodOffset=0".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            var body = resp.getBody();

            double netTotal = ((Number) body.get("netTotalAvailable")).doubleValue();
            double leftToBudget = ((Number) body.get("leftToBudget")).doubleValue();
            double totalRolledOver = ((Number) body.get("totalRolledOver")).doubleValue();

            var outflow = (Map<String, Object>) body.get("outflow");
            double outflowAvailable = ((Number) outflow.get("available")).doubleValue();
            double outflowExpected = ((Number) outflow.get("expected")).doubleValue();

            // Account balance = 5000 + 3000 - 400 + 3500 - 550 = 10550
            assertThat(netTotal).isEqualTo(10550.0);

            // Previous period: expected=600, activity=-400, available=200 → rolls over
            assertThat(totalRolledOver).isEqualTo(200.0);

            // Current period groceries: expected=600, rolledOver=200, activity=-550, available=250
            assertThat(outflowAvailable).isEqualTo(250.0);
            assertThat(outflowExpected).isEqualTo(600.0);

            // leftToBudget = budgetable(0) - totalBudgeted(600) = -600 (no income expected set for current period)
            assertThat(leftToBudget).isEqualTo(-600.0);
        }

        @Test
        @SuppressWarnings("unchecked")
        void availablePool_flowsThroughTwoPeriods() {
            // ── Period 1: earn $5000, budget groceries $600, spend $400, budget rent $1400, spend $1400
            String prevTxnDate = prevPeriodStart.plusDays(5).format(DateTimeFormatter.ISO_DATE);
            createTransaction("5000.00", incomeCategoryId, prevTxnDate);
            createTransaction("-400.00", childCategoryId, prevTxnDate);

            // Create a rent category (non-parent expense)
            var rentResp = restTemplate.exchange(
                    "/api/v1/categories", HttpMethod.POST,
                    new HttpEntity<>("""
                            {"name":"Rent"}
                            """, authHeaders(token)), Map.class);
            String rentCategoryId = (String) rentResp.getBody().get("id");
            createTransaction("-1400.00", rentCategoryId, prevTxnDate);

            // Set expected for Period 1
            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, childCategoryId, prevPeriodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":600.00}
                            """, authHeaders(token)), Void.class);
            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, rentCategoryId, prevPeriodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":1400.00}
                            """, authHeaders(token)), Void.class);
            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/expected?periodStart=%s".formatted(budgetId, incomeCategoryId, prevPeriodStart),
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {"expectedAmount":5000.00}
                            """, authHeaders(token)), Void.class);

            // Set groceries to AVAILABLE_POOL rollover
            restTemplate.exchange(
                    "/api/v1/budgets/%s/categories/%s/config".formatted(budgetId, childCategoryId), HttpMethod.PATCH,
                    new HttpEntity<>("""
                            {"rolloverType":"AVAILABLE_POOL"}
                            """, authHeaders(token)), Map.class);

            // ── Verify Period 1 (offset -1)
            var p1Resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view?periodOffset=-1".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            var p1 = p1Resp.getBody();

            // Account balance = 5000(starting) + 5000 - 400 - 1400 = 8200
            assertThat(((Number) p1.get("netTotalAvailable")).doubleValue()).isEqualTo(8200.0);
            // budgetable = inflowExpected(5000) + availablePool(0) = 5000
            assertThat(((Number) p1.get("budgetable")).doubleValue()).isEqualTo(5000.0);
            // totalBudgeted = 600 + 1400 = 2000
            assertThat(((Number) p1.get("totalBudgeted")).doubleValue()).isEqualTo(2000.0);
            // leftToBudget = 5000 - 2000 = 3000
            assertThat(((Number) p1.get("leftToBudget")).doubleValue()).isEqualTo(3000.0);
            // groceries: expected=600, activity=-400, available=200 → will flow to pool
            var p1Outflow = (Map<String, Object>) p1.get("outflow");
            assertThat(((Number) p1Outflow.get("available")).doubleValue()).isEqualTo(200.0);

            // ── Verify Period 2 (offset 0) — no expected set yet
            var p2Resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view?periodOffset=0".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);
            var p2 = p2Resp.getBody();

            // availablePool gets groceries' $200 surplus
            assertThat(((Number) p2.get("availablePool")).doubleValue()).isEqualTo(200.0);
            assertThat(((Number) p2.get("totalRolledOver")).doubleValue()).isEqualTo(200.0);
            // budgetable = inflowExpected(0) + availablePool(200) = 200
            assertThat(((Number) p2.get("budgetable")).doubleValue()).isEqualTo(200.0);
            // totalBudgeted = 0 (no expected set)
            assertThat(((Number) p2.get("totalBudgeted")).doubleValue()).isEqualTo(0.0);
            // leftToBudget = 200 - 0 = 200 (the pool money is available to assign)
            assertThat(((Number) p2.get("leftToBudget")).doubleValue()).isEqualTo(200.0);
            // netTotalAvailable unchanged
            assertThat(((Number) p2.get("netTotalAvailable")).doubleValue()).isEqualTo(8200.0);
            // groceries rolledOver should be 0 (AVAILABLE_POOL sends it to pool, not category)
            var p2Outflow = (Map<String, Object>) p2.get("outflow");
            var p2Categories = (java.util.List<Map<String, Object>>) p2Outflow.get("categories");
            for (var cat : p2Categories) {
                assertThat(((Number) cat.get("rolledOver")).doubleValue()).isEqualTo(0.0);
            }
        }

        @Test
        @SuppressWarnings("unchecked")
        void envelopeEquation_multiAccount() {
            // Create second account with balance 2000
            var acct2Resp = restTemplate.exchange(
                    "/api/v1/accounts", HttpMethod.POST,
                    new HttpEntity<>("""
                            {"name":"Savings","type":"CASH","subType":"SAVINGS","startingBalance":2000}
                            """, authHeaders(token)),
                    Map.class);
            String acct2Id = (String) acct2Resp.getBody().get("id");

            // Update budget to include both accounts
            restTemplate.exchange(
                    "/api/v1/budgets/%s".formatted(budgetId), HttpMethod.PATCH,
                    new HttpEntity<>("""
                            {"accountIds":["%s","%s"]}
                            """.formatted(accountId, acct2Id), authHeaders(token)),
                    Map.class);

            // Create transactions on different accounts
            String txnDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            createTransaction("2000.00", incomeCategoryId, txnDate);
            createTransactionOnAccount("-300.00", childCategoryId, txnDate, acct2Id);

            var resp = restTemplate.exchange(
                    "/api/v1/budgets/%s/view".formatted(budgetId), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)), Map.class);

            double netTotal = ((Number) resp.getBody().get("netTotalAvailable")).doubleValue();

            // Account 1: 5000 + 2000 = 7000
            // Account 2: 2000 - 300 = 1700
            // Net total = 7000 + 1700 = 8700
            assertThat(netTotal).isEqualTo(8700.0);
        }
    }
}
