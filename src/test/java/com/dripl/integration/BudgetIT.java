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
        accountId = createAccount(token, "Checking", "CASH", "CHECKING", "5000");

        // Create expense category (parent)
        expenseCategoryId = createCategory(token, "Food");

        // Create child expense category
        childCategoryId = createCategory(token, "Groceries", expenseCategoryId, null);

        // Create income category
        incomeCategoryId = createCategory(token, "Salary", true);
    }

    // ── Helpers ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String createMonthlyBudget() {
        var data = graphqlData(token, """
                mutation {
                    createBudget(input: { name: "Monthly Budget", anchorDay1: 1, accountIds: ["%s"] }) {
                        id name anchorDay1 anchorDay2 intervalDays anchorDate accountIds currentPeriodStart currentPeriodEnd
                    }
                }
                """.formatted(accountId));
        var body = (Map<String, Object>) data.get("createBudget");
        return (String) body.get("id");
    }

    @SuppressWarnings("unchecked")
    private String createFixedIntervalBudget(LocalDate anchor, int intervalDays) {
        var data = graphqlData(token, """
                mutation {
                    createBudget(input: { name: "Fixed Budget", anchorDate: "%s", intervalDays: %d, accountIds: ["%s"] }) {
                        id name anchorDate intervalDays accountIds currentPeriodStart currentPeriodEnd
                    }
                }
                """.formatted(anchor, intervalDays, accountId));
        var body = (Map<String, Object>) data.get("createBudget");
        return (String) body.get("id");
    }

    private String createTransaction(String amount, String categoryId, String date) {
        String dateTime = date.contains("T") ? date : date + "T00:00:00";
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) graphqlData(token, """
                mutation {
                    createTransaction(input: {
                        accountId: "%s", merchantName: "Store",
                        amount: %s, date: "%s", categoryId: "%s"
                    }) { id }
                }
                """.formatted(accountId, amount, dateTime, categoryId)).get("createTransaction");
        return (String) data.get("id");
    }

    private String createTransactionOnAccount(String amount, String categoryId, String date, String acctId) {
        String dateTime = date.contains("T") ? date : date + "T00:00:00";
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) graphqlData(token, """
                mutation {
                    createTransaction(input: {
                        accountId: "%s", merchantName: "Store",
                        amount: %s, date: "%s", categoryId: "%s"
                    }) { id }
                }
                """.formatted(acctId, amount, dateTime, categoryId)).get("createTransaction");
        return (String) data.get("id");
    }

    private void setExpectedAmount(String budgetId, String categoryId, LocalDate periodStart, String amount) {
        graphqlData(token, """
                mutation {
                    setBudgetExpectedAmount(budgetId: "%s", categoryId: "%s", periodStart: "%s", input: { expectedAmount: %s })
                }
                """.formatted(budgetId, categoryId, periodStart, amount));
    }

    private void setCategoryConfig(String budgetId, String categoryId, String rolloverType) {
        graphqlData(token, """
                mutation {
                    updateBudgetCategoryConfig(budgetId: "%s", categoryId: "%s", input: { rolloverType: %s }) {
                        id categoryId rolloverType
                    }
                }
                """.formatted(budgetId, categoryId, rolloverType));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getBudgetView(String budgetId, int periodOffset) {
        var data = graphqlData(token, """
                query {
                    budgetView(budgetId: "%s", periodOffset: %d) {
                        periodStart periodEnd budgetable totalBudgeted leftToBudget
                        netTotalAvailable recurringExpected availablePool totalRolledOver
                        inflow { expected activity available categories { categoryId name expected recurringExpected activity available rolledOver rolloverType children { categoryId name expected activity available rolledOver rolloverType } } }
                        outflow { expected activity available categories { categoryId name expected recurringExpected activity available rolledOver rolloverType children { categoryId name expected activity available rolledOver rolloverType } } }
                    }
                }
                """.formatted(budgetId, periodOffset));
        return (Map<String, Object>) data.get("budgetView");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getBudgetViewByDate(String budgetId, String date) {
        var data = graphqlData(token, """
                query {
                    budgetView(budgetId: "%s", date: "%s") {
                        periodStart periodEnd budgetable totalBudgeted leftToBudget
                        netTotalAvailable recurringExpected availablePool totalRolledOver
                        inflow { expected activity available categories { categoryId name expected recurringExpected activity available rolledOver rolloverType children { categoryId name expected activity available rolledOver rolloverType } } }
                        outflow { expected activity available categories { categoryId name expected recurringExpected activity available rolledOver rolloverType children { categoryId name expected activity available rolledOver rolloverType } } }
                    }
                }
                """.formatted(budgetId, date));
        return (Map<String, Object>) data.get("budgetView");
    }

    // ── Budget CRUD Tests ───────────────────────────────────

    @Nested
    class BudgetCrudTests {

        @Test
        @SuppressWarnings("unchecked")
        void createBudget_monthly() {
            var data = graphqlData(token, """
                    mutation {
                        createBudget(input: { name: "My Budget", anchorDay1: 1, accountIds: ["%s"] }) {
                            id name anchorDay1 currentPeriodStart currentPeriodEnd accountIds
                        }
                    }
                    """.formatted(accountId));
            var body = (Map<String, Object>) data.get("createBudget");
            assertThat(body.get("name")).isEqualTo("My Budget");
            assertThat(body.get("anchorDay1")).isEqualTo(1);
            assertThat(body.get("currentPeriodStart")).isNotNull();
            assertThat(body.get("currentPeriodEnd")).isNotNull();
            assertThat((List<?>) body.get("accountIds")).hasSize(1);
        }

        @Test
        @SuppressWarnings("unchecked")
        void createBudget_fixedInterval() {
            LocalDate anchor = LocalDate.now().minusDays(7);
            var data = graphqlData(token, """
                    mutation {
                        createBudget(input: { name: "Biweekly", anchorDate: "%s", intervalDays: 14, accountIds: ["%s"] }) {
                            id intervalDays
                        }
                    }
                    """.formatted(anchor, accountId));
            var body = (Map<String, Object>) data.get("createBudget");
            assertThat(body.get("intervalDays")).isEqualTo(14);
        }

        @Test
        @SuppressWarnings("unchecked")
        void createBudget_semiMonthly() {
            var data = graphqlData(token, """
                    mutation {
                        createBudget(input: { name: "Semi", anchorDay1: 1, anchorDay2: 15, accountIds: ["%s"] }) {
                            id anchorDay1 anchorDay2
                        }
                    }
                    """.formatted(accountId));
            var body = (Map<String, Object>) data.get("createBudget");
            assertThat(body.get("anchorDay1")).isEqualTo(1);
            assertThat(body.get("anchorDay2")).isEqualTo(15);
        }

        @Test
        void createBudget_invalidAnchorDay_returns400() {
            var resp = graphql(token, """
                    mutation {
                        createBudget(input: { name: "Bad", anchorDay1: 32, accountIds: ["%s"] }) {
                            id
                        }
                    }
                    """.formatted(accountId));
            assertThat(resp.get("errors")).isNotNull();
        }

        @Test
        void createBudget_duplicateAnchorDays_returns400() {
            var resp = graphql(token, """
                    mutation {
                        createBudget(input: { name: "Bad", anchorDay1: 15, anchorDay2: 15, accountIds: ["%s"] }) {
                            id
                        }
                    }
                    """.formatted(accountId));
            assertThat(resp.get("errors")).isNotNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        void listBudgets() {
            createMonthlyBudget();
            var data = graphqlData(token, """
                    query {
                        budgets {
                            id name anchorDay1 anchorDay2 intervalDays anchorDate accountIds currentPeriodStart currentPeriodEnd
                        }
                    }
                    """);
            var budgets = (List<?>) data.get("budgets");
            assertThat(budgets).hasSize(1);
        }

        @Test
        @SuppressWarnings("unchecked")
        void getBudget() {
            String budgetId = createMonthlyBudget();
            var data = graphqlData(token, """
                    query {
                        budget(budgetId: "%s") {
                            id name
                        }
                    }
                    """.formatted(budgetId));
            var body = (Map<String, Object>) data.get("budget");
            assertThat(body.get("name")).isEqualTo("Monthly Budget");
        }

        @Test
        @SuppressWarnings("unchecked")
        void updateBudget() {
            String budgetId = createMonthlyBudget();
            var data = graphqlData(token, """
                    mutation {
                        updateBudget(budgetId: "%s", input: { name: "Updated Budget" }) {
                            id name
                        }
                    }
                    """.formatted(budgetId));
            var body = (Map<String, Object>) data.get("updateBudget");
            assertThat(body.get("name")).isEqualTo("Updated Budget");
        }

        @Test
        void deleteBudget() {
            String budgetId = createMonthlyBudget();
            graphqlData(token, """
                    mutation { deleteBudget(budgetId: "%s") }
                    """.formatted(budgetId));

            // Verify it's gone
            var resp = graphql(token, """
                    query { budget(budgetId: "%s") { id } }
                    """.formatted(budgetId));
            assertThat(resp.get("errors")).isNotNull();
        }

        @Test
        void createBudget_duplicateName_returns409() {
            createMonthlyBudget();
            var resp = graphql(token, """
                    mutation {
                        createBudget(input: { name: "Monthly Budget", anchorDay1: 1, accountIds: ["%s"] }) {
                            id
                        }
                    }
                    """.formatted(accountId));
            assertThat(resp.get("errors")).isNotNull();
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
            var data = graphqlData(token, """
                    mutation {
                        updateBudgetCategoryConfig(budgetId: "%s", categoryId: "%s", input: { rolloverType: SAME_CATEGORY }) {
                            id categoryId rolloverType
                        }
                    }
                    """.formatted(budgetId, expenseCategoryId));
            var body = (Map<String, Object>) data.get("updateBudgetCategoryConfig");
            assertThat(body.get("rolloverType")).isEqualTo("SAME_CATEGORY");

            var data2 = graphqlData(token, """
                    mutation {
                        updateBudgetCategoryConfig(budgetId: "%s", categoryId: "%s", input: { rolloverType: NONE }) {
                            id categoryId rolloverType
                        }
                    }
                    """.formatted(budgetId, expenseCategoryId));
            var body2 = (Map<String, Object>) data2.get("updateBudgetCategoryConfig");
            assertThat(body2.get("rolloverType")).isEqualTo("NONE");
        }

        @Test
        void setExpectedAmount_andClear() {
            LocalDate periodStart = LocalDate.now().withDayOfMonth(1);

            setExpectedAmount(budgetId, childCategoryId, periodStart, "500");

            // Verify it shows in the period view
            var body = getBudgetView(budgetId, 0);
            assertThat(body.get("periodStart")).isNotNull();

            setExpectedAmount(budgetId, childCategoryId, periodStart, "null");
        }

        @Test
        @SuppressWarnings("unchecked")
        void setExpectedAmount_updateExisting() {
            LocalDate periodStart = LocalDate.now().withDayOfMonth(1);

            setExpectedAmount(budgetId, childCategoryId, periodStart, "500");
            setExpectedAmount(budgetId, childCategoryId, periodStart, "750");

            var body = getBudgetView(budgetId, 0);
            assertThat(body.get("periodStart")).isNotNull();
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

            setExpectedAmount(budgetId, childCategoryId, periodStart, "200");

            var body = getBudgetView(budgetId, 0);
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
            setExpectedAmount(budgetId, childCategoryId, periodStart, "200");

            // Setting expected on parent should be rejected
            var parentResp = graphql(token, """
                    mutation {
                        setBudgetExpectedAmount(budgetId: "%s", categoryId: "%s", periodStart: "%s", input: { expectedAmount: 100 })
                    }
                    """.formatted(budgetId, expenseCategoryId, periodStart));
            assertThat(parentResp.get("errors")).isNotNull();

            var body = getBudgetView(budgetId, 0);

            var outflow = (Map<String, Object>) body.get("outflow");
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

            setExpectedAmount(budgetId, incomeCategoryId, periodStart, "5000");

            var body = getBudgetView(budgetId, 0);

            var inflow = (Map<String, Object>) body.get("inflow");
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

            createCategoryExcludedFromBudget(token, "ExcludedCat");

            var body = getBudgetView(budgetId, 0);

            var outflow = (Map<String, Object>) body.get("outflow");
            var categories = (List<Map<String, Object>>) outflow.get("categories");
            assertThat(categories.stream().noneMatch(c -> "ExcludedCat".equals(c.get("name")))).isTrue();
        }

        @Test
        @SuppressWarnings("unchecked")
        void getPeriodView_accountNotInBudget_activityExcluded() {
            // Create a second account NOT included in the budget
            String otherAccountId = createAccount(token, "HSA", "CASH", "SAVINGS", "0");

            // Budget only includes the original "Checking" account
            String budgetId = createMonthlyBudget();

            LocalDate periodStart = LocalDate.now().withDayOfMonth(1);
            String txnDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

            // Transaction on non-budgeted account
            createTransactionOnAccount("-100.00", childCategoryId, txnDate, otherAccountId);
            // Transaction on budgeted account
            createTransaction("-50.00", childCategoryId, txnDate);

            setExpectedAmount(budgetId, childCategoryId, periodStart, "200");

            var body = getBudgetView(budgetId, 0);

            var outflow = (Map<String, Object>) body.get("outflow");
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

            var body = getBudgetView(budgetId, -1);

            LocalDate expectedPrevStart = LocalDate.now().withDayOfMonth(1).minusMonths(1);
            assertThat(body.get("periodStart")).isEqualTo(expectedPrevStart.toString());
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

            setCategoryConfig(budgetId, childCategoryId, "SAME_CATEGORY");
            setExpectedAmount(budgetId, childCategoryId, prevPeriodStart, "500");

            createTransaction("-300.00", childCategoryId, prevTxnDate);

            setExpectedAmount(budgetId, childCategoryId, currentPeriodStart, "500");

            var body = getBudgetView(budgetId, 0);

            var outflow = (Map<String, Object>) body.get("outflow");
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

            setCategoryConfig(budgetId, childCategoryId, "SAME_CATEGORY");
            setExpectedAmount(budgetId, childCategoryId, prevPeriodStart, "200");

            createTransaction("-500.00", childCategoryId, prevTxnDate);

            setExpectedAmount(budgetId, childCategoryId, currentPeriodStart, "500");

            var body = getBudgetView(budgetId, 0);

            var outflow = (Map<String, Object>) body.get("outflow");
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

            setExpectedAmount(budgetId, childCategoryId, prevPeriodStart, "500");

            createTransaction("-300.00", childCategoryId, prevTxnDate);

            setExpectedAmount(budgetId, childCategoryId, currentPeriodStart, "500");

            var body = getBudgetView(budgetId, 0);

            var outflow = (Map<String, Object>) body.get("outflow");
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

            setCategoryConfig(budgetId, childCategoryId, "AVAILABLE_POOL");
            setExpectedAmount(budgetId, childCategoryId, prevPeriodStart, "500");

            createTransaction("-300.00", childCategoryId, prevTxnDate);

            var body = getBudgetView(budgetId, 0);

            var outflow = (Map<String, Object>) body.get("outflow");
            var categories = (List<Map<String, Object>>) outflow.get("categories");
            var foodCat = categories.stream()
                    .filter(c -> "Food".equals(c.get("name")))
                    .findFirst().orElse(null);
            var children = (List<Map<String, Object>>) foodCat.get("children");
            var groceriesCat = children.stream()
                    .filter(c -> "Groceries".equals(c.get("name")))
                    .findFirst().orElse(null);
            assertThat(((Number) groceriesCat.get("rolledOver")).doubleValue()).isEqualTo(0.0);

            assertThat(((Number) body.get("availablePool")).doubleValue()).isEqualTo(200.0);
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

            var body = getBudgetView(budgetId, 0);
            LocalDate periodStart = LocalDate.parse((String) body.get("periodStart"));
            LocalDate periodEnd = LocalDate.parse((String) body.get("periodEnd"));
            assertThat(LocalDate.now()).isBetween(periodStart, periodEnd);
        }

        @Test
        @SuppressWarnings("unchecked")
        void fixedInterval_navigateToPrevious() {
            LocalDate anchor = LocalDate.now().minusDays(7);
            String budgetId = createFixedIntervalBudget(anchor, 14);

            var currentBody = getBudgetView(budgetId, 0);
            LocalDate currentStart = LocalDate.parse((String) currentBody.get("periodStart"));

            var prevBody = getBudgetView(budgetId, -1);
            LocalDate prevEnd = LocalDate.parse((String) prevBody.get("periodEnd"));

            assertThat(prevEnd).isEqualTo(currentStart.minusDays(1));
        }

        @Test
        @SuppressWarnings("unchecked")
        void fixedInterval_directAccessByPeriodStart() {
            LocalDate anchor = LocalDate.now().minusDays(7);
            String budgetId = createFixedIntervalBudget(anchor, 14);

            var currentBody = getBudgetView(budgetId, 0);
            String currentStart = (String) currentBody.get("periodStart");

            var directBody = getBudgetViewByDate(budgetId, currentStart);
            assertThat(directBody.get("periodStart")).isEqualTo(currentStart);
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
            // AccountResponse starts at 5000, add income and expense
            String txnDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            createTransaction("3000.00", incomeCategoryId, txnDate);
            createTransaction("-500.00", childCategoryId, txnDate);

            var body = getBudgetView(budgetId, 0);

            // AccountResponse balance = 5000 + 3000 - 500 = 7500
            double netTotal = ((Number) body.get("netTotalAvailable")).doubleValue();
            assertThat(netTotal).isEqualTo(7500.0);
        }

        @Test
        @SuppressWarnings("unchecked")
        void envelopeEquation_holdsWithActivityAndExpected() {
            String txnDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            createTransaction("3000.00", incomeCategoryId, txnDate);
            createTransaction("-400.00", childCategoryId, txnDate);

            // Set expected amounts
            setExpectedAmount(budgetId, childCategoryId, currentPeriodStart, "600");
            setExpectedAmount(budgetId, incomeCategoryId, currentPeriodStart, "5000");

            var body = getBudgetView(budgetId, 0);

            double netTotal = ((Number) body.get("netTotalAvailable")).doubleValue();
            double budgetable = ((Number) body.get("budgetable")).doubleValue();
            double totalBudgeted = ((Number) body.get("totalBudgeted")).doubleValue();
            double leftToBudget = ((Number) body.get("leftToBudget")).doubleValue();

            var outflow = (Map<String, Object>) body.get("outflow");
            double outflowAvailable = ((Number) outflow.get("available")).doubleValue();

            // AccountResponse balance = 5000 + 3000 - 400 = 7600
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
            setCategoryConfig(budgetId, childCategoryId, "SAME_CATEGORY");

            // Set expected for previous period
            setExpectedAmount(budgetId, childCategoryId, prevPeriodStart, "600");

            // Current period: earn $3500, budget $600, spend $550
            String curTxnDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            createTransaction("3500.00", incomeCategoryId, curTxnDate);
            createTransaction("-550.00", childCategoryId, curTxnDate);

            setExpectedAmount(budgetId, childCategoryId, currentPeriodStart, "600");

            var body = getBudgetView(budgetId, 0);

            double netTotal = ((Number) body.get("netTotalAvailable")).doubleValue();
            double leftToBudget = ((Number) body.get("leftToBudget")).doubleValue();
            double totalRolledOver = ((Number) body.get("totalRolledOver")).doubleValue();

            var outflow = (Map<String, Object>) body.get("outflow");
            double outflowAvailable = ((Number) outflow.get("available")).doubleValue();
            double outflowExpected = ((Number) outflow.get("expected")).doubleValue();

            // AccountResponse balance = 5000 + 3000 - 400 + 3500 - 550 = 10550
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
            String rentCategoryId = createCategory(token, "Rent");
            createTransaction("-1400.00", rentCategoryId, prevTxnDate);

            // Set expected for Period 1
            setExpectedAmount(budgetId, childCategoryId, prevPeriodStart, "600");
            setExpectedAmount(budgetId, rentCategoryId, prevPeriodStart, "1400");
            setExpectedAmount(budgetId, incomeCategoryId, prevPeriodStart, "5000");

            // Set groceries to AVAILABLE_POOL rollover
            setCategoryConfig(budgetId, childCategoryId, "AVAILABLE_POOL");

            // ── Verify Period 1 (offset -1)
            var p1 = getBudgetView(budgetId, -1);

            // AccountResponse balance = 5000(starting) + 5000 - 400 - 1400 = 8200
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
            var p2 = getBudgetView(budgetId, 0);

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
            String acct2Id = createAccount(token, "Savings", "CASH", "SAVINGS", "2000");

            // Update budget to include both accounts
            graphqlData(token, """
                    mutation {
                        updateBudget(budgetId: "%s", input: { accountIds: ["%s", "%s"] }) {
                            id accountIds
                        }
                    }
                    """.formatted(budgetId, accountId, acct2Id));

            // Create transactions on different accounts
            String txnDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            createTransaction("2000.00", incomeCategoryId, txnDate);
            createTransactionOnAccount("-300.00", childCategoryId, txnDate, acct2Id);

            var body = getBudgetView(budgetId, 0);

            double netTotal = ((Number) body.get("netTotalAvailable")).doubleValue();

            // AccountResponse 1: 5000 + 2000 = 7000
            // AccountResponse 2: 2000 - 300 = 1700
            // Net total = 7000 + 1700 = 8700
            assertThat(netTotal).isEqualTo(8700.0);
        }
    }
}
