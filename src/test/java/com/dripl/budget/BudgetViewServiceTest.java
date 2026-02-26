package com.dripl.budget;

import com.dripl.account.repository.AccountRepository;
import com.dripl.budget.dto.BudgetPeriodViewDto;
import com.dripl.budget.entity.Budget;
import com.dripl.budget.entity.BudgetAccount;
import com.dripl.budget.entity.BudgetCategoryConfig;
import com.dripl.budget.entity.BudgetPeriodEntry;
import com.dripl.budget.enums.RolloverType;
import com.dripl.budget.repository.BudgetAccountRepository;
import com.dripl.budget.repository.BudgetCategoryConfigRepository;
import com.dripl.budget.repository.BudgetPeriodEntryRepository;
import com.dripl.budget.service.BudgetService;
import com.dripl.budget.service.BudgetViewService;
import com.dripl.budget.util.BudgetPeriodCalculator;
import com.dripl.budget.util.PeriodRange;
import com.dripl.category.entity.Category;
import com.dripl.category.repository.CategoryRepository;
import com.dripl.common.exception.BadRequestException;
import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.enums.FrequencyGranularity;
import com.dripl.recurring.enums.RecurringItemStatus;
import com.dripl.recurring.repository.RecurringItemRepository;
import com.dripl.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetViewServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private RecurringItemRepository recurringItemRepository;
    @Mock private com.dripl.recurring.repository.RecurringItemOverrideRepository recurringItemOverrideRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private BudgetAccountRepository budgetAccountRepository;
    @Mock private BudgetCategoryConfigRepository configRepository;
    @Mock private BudgetPeriodEntryRepository entryRepository;
    @Mock private BudgetService budgetService;
    @InjectMocks private BudgetViewService service;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();
    private final UUID expenseCatId = UUID.randomUUID();
    private final UUID incomeCatId = UUID.randomUUID();

    private Budget budget;

    @BeforeEach
    void setUp() {
        budget = Budget.builder()
                .id(budgetId)
                .workspaceId(workspaceId)
                .name("Default")
                .anchorDay1(1)
                .build();

        when(recurringItemOverrideRepository.findByWorkspaceIdAndOccurrenceDateBetween(any(), any(), any()))
                .thenReturn(java.util.List.of());
        when(transactionRepository.findLinkedToRecurringItemsInDateRange(any(), any(), any()))
                .thenReturn(java.util.List.of());
    }

    @Test
    void getView_noBudgetConfigured_throws() {
        Budget unconfigured = Budget.builder()
                .id(budgetId).workspaceId(workspaceId).name("Empty").build();
        when(budgetService.findBudget(workspaceId, budgetId)).thenReturn(unconfigured);

        assertThatThrownBy(() -> service.getView(workspaceId, budgetId, 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Budget period is not configured");
    }

    @Nested
    class PeriodView {

        private Category expenseCategory;
        private Category incomeCategory;
        private LocalDate currentPeriodStart;
        private LocalDate currentPeriodEnd;

        @BeforeEach
        void setUp() {
            expenseCategory = Category.builder()
                    .id(expenseCatId).workspaceId(workspaceId).name("Groceries")
                    .income(false).excludeFromBudget(false).build();
            incomeCategory = Category.builder()
                    .id(incomeCatId).workspaceId(workspaceId).name("Salary")
                    .income(true).excludeFromBudget(false).build();

            var period = BudgetPeriodCalculator.computePeriod(budget, LocalDate.now());
            currentPeriodStart = period.start();
            currentPeriodEnd = period.end();

            lenient().when(budgetService.findBudget(workspaceId, budgetId)).thenReturn(budget);
            lenient().when(transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                    any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
            lenient().when(entryRepository.findByBudgetIdAndCategoryIdAndPeriodStart(
                    any(), any(), any())).thenReturn(Optional.empty());
            lenient().when(entryRepository.findAllByBudgetIdAndPeriodStart(
                    any(), any())).thenReturn(List.of());
            lenient().when(recurringItemRepository.findAllByWorkspaceId(any())).thenReturn(List.of());
            lenient().when(budgetAccountRepository.findAllByBudgetId(any())).thenReturn(List.of());
            lenient().when(accountRepository.sumBalancesByIds(any())).thenReturn(BigDecimal.ZERO);
        }

        @Test
        void getPeriodView_basicExpenseAndIncome() {
            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(expenseCategory, incomeCategory));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            when(entryRepository.findAllByBudgetIdAndPeriodStart(eq(budgetId), eq(currentPeriodStart)))
                    .thenReturn(List.of(
                            BudgetPeriodEntry.builder()
                                    .categoryId(expenseCatId).periodStart(currentPeriodStart)
                                    .expectedAmount(new BigDecimal("300")).build(),
                            BudgetPeriodEntry.builder()
                                    .categoryId(incomeCatId).periodStart(currentPeriodStart)
                                    .expectedAmount(new BigDecimal("5000")).build()
                    ));

            when(transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                    eq(budgetId), eq(expenseCatId),
                    eq(currentPeriodStart.atStartOfDay()), any()))
                    .thenReturn(new BigDecimal("-200"));
            when(transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                    eq(budgetId), eq(incomeCatId),
                    eq(currentPeriodStart.atStartOfDay()), any()))
                    .thenReturn(new BigDecimal("4500"));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getPeriodStart()).isEqualTo(currentPeriodStart);
            assertThat(result.getPeriodEnd()).isEqualTo(currentPeriodEnd);

            assertThat(result.getOutflow().getCategories()).hasSize(1);
            assertThat(result.getOutflow().getExpected()).isEqualByComparingTo("300");
            assertThat(result.getOutflow().getActivity()).isEqualByComparingTo("-200");
            assertThat(result.getOutflow().getAvailable()).isEqualByComparingTo("100");

            assertThat(result.getInflow().getCategories()).hasSize(1);
            assertThat(result.getInflow().getExpected()).isEqualByComparingTo("5000");
            assertThat(result.getInflow().getActivity()).isEqualByComparingTo("4500");
            assertThat(result.getInflow().getAvailable()).isEqualByComparingTo("500");

            assertThat(result.getBudgetable()).isEqualByComparingTo("5000");
            assertThat(result.getTotalBudgeted()).isEqualByComparingTo("300");
            assertThat(result.getLeftToBudget()).isEqualByComparingTo("4700");
            assertThat(result.getTotalRolledOver()).isEqualByComparingTo("0");
            assertThat(result.getNetTotalAvailable()).isEqualByComparingTo("0");
        }

        @Test
        void getPeriodView_excludeFromBudget_omitted() {
            Category excluded = Category.builder()
                    .id(UUID.randomUUID()).workspaceId(workspaceId).name("Excluded")
                    .income(false).excludeFromBudget(true).build();

            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(expenseCategory, excluded));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getOutflow().getCategories()).hasSize(1);
            assertThat(result.getOutflow().getCategories().get(0).getName()).isEqualTo("Groceries");
        }

        @Test
        void getPeriodView_sameCategoryRollover_carriesForward() {
            var prevPeriodStart = BudgetPeriodCalculator.computePreviousPeriod(budget,
                    new PeriodRange(currentPeriodStart, currentPeriodEnd)).start();

            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(expenseCategory));
            when(configRepository.findAllByBudgetId(budgetId))
                    .thenReturn(List.of(BudgetCategoryConfig.builder()
                            .categoryId(expenseCatId).rolloverType(RolloverType.SAME_CATEGORY).build()));

            when(entryRepository.findAllByBudgetIdAndPeriodStart(eq(budgetId), eq(currentPeriodStart)))
                    .thenReturn(List.of(BudgetPeriodEntry.builder()
                            .categoryId(expenseCatId).periodStart(currentPeriodStart)
                            .expectedAmount(new BigDecimal("300")).build()));
            when(transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                    eq(budgetId), eq(expenseCatId),
                    eq(currentPeriodStart.atStartOfDay()), any()))
                    .thenReturn(new BigDecimal("-200"));

            when(entryRepository.findByBudgetIdAndCategoryIdAndPeriodStart(
                    eq(budgetId), eq(expenseCatId), eq(prevPeriodStart)))
                    .thenReturn(Optional.of(BudgetPeriodEntry.builder()
                            .expectedAmount(new BigDecimal("400")).build()));
            when(transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                    eq(budgetId), eq(expenseCatId),
                    eq(prevPeriodStart.atStartOfDay()), any()))
                    .thenReturn(new BigDecimal("-350"));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getOutflow().getCategories().get(0).getRolledOver())
                    .isEqualByComparingTo("50");
            assertThat(result.getOutflow().getCategories().get(0).getAvailable())
                    .isEqualByComparingTo("150");
        }

        @Test
        void getPeriodView_sameCategoryRollover_overspentCarriesNegative() {
            var prevPeriodStart = BudgetPeriodCalculator.computePreviousPeriod(budget,
                    new PeriodRange(currentPeriodStart, currentPeriodEnd)).start();

            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(expenseCategory));
            when(configRepository.findAllByBudgetId(budgetId))
                    .thenReturn(List.of(BudgetCategoryConfig.builder()
                            .categoryId(expenseCatId).rolloverType(RolloverType.SAME_CATEGORY).build()));

            when(entryRepository.findAllByBudgetIdAndPeriodStart(eq(budgetId), eq(currentPeriodStart)))
                    .thenReturn(List.of(BudgetPeriodEntry.builder()
                            .categoryId(expenseCatId).periodStart(currentPeriodStart)
                            .expectedAmount(new BigDecimal("300")).build()));
            when(transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                    eq(budgetId), eq(expenseCatId),
                    eq(currentPeriodStart.atStartOfDay()), any()))
                    .thenReturn(new BigDecimal("-100"));

            when(entryRepository.findByBudgetIdAndCategoryIdAndPeriodStart(
                    eq(budgetId), eq(expenseCatId), eq(prevPeriodStart)))
                    .thenReturn(Optional.of(BudgetPeriodEntry.builder()
                            .expectedAmount(new BigDecimal("200")).build()));
            when(transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                    eq(budgetId), eq(expenseCatId),
                    eq(prevPeriodStart.atStartOfDay()), any()))
                    .thenReturn(new BigDecimal("-250"));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getOutflow().getCategories().get(0).getRolledOver())
                    .isEqualByComparingTo("-50");
            assertThat(result.getOutflow().getCategories().get(0).getAvailable())
                    .isEqualByComparingTo("150");
        }

        @Test
        void getPeriodView_noneRollover_noCarryover() {
            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(expenseCategory));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            when(entryRepository.findAllByBudgetIdAndPeriodStart(eq(budgetId), eq(currentPeriodStart)))
                    .thenReturn(List.of(BudgetPeriodEntry.builder()
                            .categoryId(expenseCatId).periodStart(currentPeriodStart)
                            .expectedAmount(new BigDecimal("300")).build()));
            when(transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                    eq(budgetId), eq(expenseCatId),
                    eq(currentPeriodStart.atStartOfDay()), any()))
                    .thenReturn(new BigDecimal("-100"));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getOutflow().getCategories().get(0).getRolledOver())
                    .isEqualByComparingTo("0");
        }

        @Test
        void getPeriodView_availablePoolRollover_showsInPool() {
            var prevPeriodStart = BudgetPeriodCalculator.computePreviousPeriod(budget,
                    new PeriodRange(currentPeriodStart, currentPeriodEnd)).start();

            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(expenseCategory));
            when(configRepository.findAllByBudgetId(budgetId))
                    .thenReturn(List.of(BudgetCategoryConfig.builder()
                            .categoryId(expenseCatId).rolloverType(RolloverType.AVAILABLE_POOL).build()));

            when(entryRepository.findAllByBudgetIdAndPeriodStart(eq(budgetId), eq(currentPeriodStart)))
                    .thenReturn(List.of(BudgetPeriodEntry.builder()
                            .categoryId(expenseCatId).periodStart(currentPeriodStart)
                            .expectedAmount(new BigDecimal("300")).build()));
            when(transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                    eq(budgetId), eq(expenseCatId),
                    eq(currentPeriodStart.atStartOfDay()), any()))
                    .thenReturn(new BigDecimal("-100"));

            when(entryRepository.findByBudgetIdAndCategoryIdAndPeriodStart(
                    eq(budgetId), eq(expenseCatId), eq(prevPeriodStart)))
                    .thenReturn(Optional.of(BudgetPeriodEntry.builder()
                            .expectedAmount(new BigDecimal("400")).build()));
            when(transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                    eq(budgetId), eq(expenseCatId),
                    eq(prevPeriodStart.atStartOfDay()), any()))
                    .thenReturn(new BigDecimal("-300"));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getOutflow().getCategories().get(0).getRolledOver())
                    .isEqualByComparingTo("0");
            assertThat(result.getAvailablePool()).isEqualByComparingTo("100");
            assertThat(result.getTotalRolledOver()).isEqualByComparingTo("100");
            assertThat(result.getLeftToBudget()).isEqualByComparingTo("-200");
        }

        @Test
        void getPeriodView_parentCategoryRollup() {
            UUID childId = UUID.randomUUID();
            Category parent = Category.builder()
                    .id(expenseCatId).workspaceId(workspaceId).name("Food")
                    .income(false).excludeFromBudget(false).build();
            Category child = Category.builder()
                    .id(childId).workspaceId(workspaceId).name("Groceries")
                    .parentId(expenseCatId).income(false).excludeFromBudget(false).build();

            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(parent, child));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            when(entryRepository.findAllByBudgetIdAndPeriodStart(eq(budgetId), eq(currentPeriodStart)))
                    .thenReturn(List.of(
                            BudgetPeriodEntry.builder()
                                    .categoryId(childId).periodStart(currentPeriodStart)
                                    .expectedAmount(new BigDecimal("200")).build()
                    ));
            when(transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                    eq(budgetId), eq(expenseCatId),
                    eq(currentPeriodStart.atStartOfDay()), any()))
                    .thenReturn(new BigDecimal("-50"));
            when(transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                    eq(budgetId), eq(childId),
                    eq(currentPeriodStart.atStartOfDay()), any()))
                    .thenReturn(new BigDecimal("-150"));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getOutflow().getCategories()).hasSize(1);
            var parentView = result.getOutflow().getCategories().get(0);
            assertThat(parentView.getExpected()).isEqualByComparingTo("200");
            assertThat(parentView.getActivity()).isEqualByComparingTo("-150");
            assertThat(parentView.getChildren()).hasSize(1);
            assertThat(parentView.getChildren().get(0).getName()).isEqualTo("Groceries");
        }

        @Test
        void getPeriodView_orphanedExpectedOnParent_ignored() {
            UUID childId = UUID.randomUUID();
            Category parent = Category.builder()
                    .id(expenseCatId).workspaceId(workspaceId).name("Food")
                    .income(false).excludeFromBudget(false).build();
            Category child = Category.builder()
                    .id(childId).workspaceId(workspaceId).name("Groceries")
                    .parentId(expenseCatId).income(false).excludeFromBudget(false).build();

            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(parent, child));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            // Orphaned expected amount on parent (was a child before becoming a parent)
            when(entryRepository.findAllByBudgetIdAndPeriodStart(eq(budgetId), eq(currentPeriodStart)))
                    .thenReturn(List.of(
                            BudgetPeriodEntry.builder()
                                    .categoryId(expenseCatId).periodStart(currentPeriodStart)
                                    .expectedAmount(new BigDecimal("500")).build(),
                            BudgetPeriodEntry.builder()
                                    .categoryId(childId).periodStart(currentPeriodStart)
                                    .expectedAmount(new BigDecimal("200")).build()
                    ));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            var parentView = result.getOutflow().getCategories().get(0);
            // Parent expected should only be child rollup (200), not 500 + 200
            assertThat(parentView.getExpected()).isEqualByComparingTo("200");
            assertThat(parentView.getChildren().get(0).getExpected()).isEqualByComparingTo("200");
        }

        @Test
        void getView_byPeriodStart_computesPeriodFromDate() {
            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(expenseCategory));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, currentPeriodStart);

            assertThat(result.getPeriodStart()).isEqualTo(currentPeriodStart);
            assertThat(result.getPeriodEnd()).isEqualTo(currentPeriodEnd);
        }

        @Test
        void getView_netTotalAvailable_sumsLinkedAccountBalances() {
            UUID accountId1 = UUID.randomUUID();
            UUID accountId2 = UUID.randomUUID();

            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(expenseCategory, incomeCategory));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());
            when(budgetAccountRepository.findAllByBudgetId(budgetId)).thenReturn(List.of(
                    BudgetAccount.builder().accountId(accountId1).build(),
                    BudgetAccount.builder().accountId(accountId2).build()));
            when(accountRepository.sumBalancesByIds(any()))
                    .thenReturn(new BigDecimal("5500.00"));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getNetTotalAvailable()).isEqualByComparingTo("5500.00");
        }

        @Test
        void getView_noLinkedAccounts_netTotalAvailableZero() {
            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(expenseCategory));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getNetTotalAvailable()).isEqualByComparingTo("0");
        }

        @Test
        void getView_envelopeEquation_holds() {
            // Set up: income with activity, expense with expected + activity
            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(expenseCategory, incomeCategory));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            when(entryRepository.findAllByBudgetIdAndPeriodStart(eq(budgetId), eq(currentPeriodStart)))
                    .thenReturn(List.of(
                            BudgetPeriodEntry.builder()
                                    .categoryId(expenseCatId).periodStart(currentPeriodStart)
                                    .expectedAmount(new BigDecimal("300")).build(),
                            BudgetPeriodEntry.builder()
                                    .categoryId(incomeCatId).periodStart(currentPeriodStart)
                                    .expectedAmount(new BigDecimal("5000")).build()
                    ));

            when(transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                    eq(budgetId), eq(expenseCatId),
                    eq(currentPeriodStart.atStartOfDay()), any()))
                    .thenReturn(new BigDecimal("-200"));
            when(transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                    eq(budgetId), eq(incomeCatId),
                    eq(currentPeriodStart.atStartOfDay()), any()))
                    .thenReturn(new BigDecimal("4500"));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            // budgetable = inflowExpected = 5000
            // totalBudgeted = outflowExpected = 300
            // leftToBudget = 5000 - 300 = 4700
            assertThat(result.getInflow().getAvailable()).isEqualByComparingTo("500");
            assertThat(result.getOutflow().getAvailable()).isEqualByComparingTo("100");
            assertThat(result.getBudgetable()).isEqualByComparingTo("5000");
            assertThat(result.getTotalBudgeted()).isEqualByComparingTo("300");
            assertThat(result.getLeftToBudget()).isEqualByComparingTo("4700");
        }
    }

    @Nested
    class RecurringExpected {

        private Category subscriptionsCat;
        private final UUID subCatId = UUID.randomUUID();
        private final UUID includedAccountId = UUID.randomUUID();
        private LocalDate currentPeriodStart;
        private LocalDate currentPeriodEnd;

        @BeforeEach
        void setUp() {
            subscriptionsCat = Category.builder()
                    .id(subCatId).workspaceId(workspaceId).name("Subscriptions")
                    .income(false).excludeFromBudget(false).build();

            var period = BudgetPeriodCalculator.computePeriod(budget, LocalDate.now());
            currentPeriodStart = period.start();
            currentPeriodEnd = period.end();

            lenient().when(budgetService.findBudget(workspaceId, budgetId)).thenReturn(budget);
            lenient().when(transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                    any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
            lenient().when(entryRepository.findByBudgetIdAndCategoryIdAndPeriodStart(
                    any(), any(), any())).thenReturn(Optional.empty());
            lenient().when(entryRepository.findAllByBudgetIdAndPeriodStart(
                    any(), any())).thenReturn(List.of());
            lenient().when(recurringItemRepository.findAllByWorkspaceId(any())).thenReturn(List.of());
            lenient().when(budgetAccountRepository.findAllByBudgetId(budgetId))
                    .thenReturn(List.of(BudgetAccount.builder().budgetId(budgetId).accountId(includedAccountId).build()));
            lenient().when(accountRepository.sumBalancesByIds(any())).thenReturn(BigDecimal.ZERO);
        }

        @Test
        void monthlyRecurringItem_oneOccurrenceInPeriod() {
            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(subscriptionsCat));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            LocalDate anchor = currentPeriodStart.minusMonths(1).withDayOfMonth(15);
            RecurringItem ri = RecurringItem.builder()
                    .anchorDates(List.of(anchor.atStartOfDay()))
                    .frequencyGranularity(FrequencyGranularity.MONTH)
                    .frequencyQuantity(1)
                    .startDate(anchor.minusYears(1).atStartOfDay())
                    .status(RecurringItemStatus.ACTIVE)
                    .categoryId(subCatId)
                    .accountId(includedAccountId)
                    .amount(new BigDecimal("14.99"))
                    .build();
            when(recurringItemRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(ri));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getOutflow().getCategories().get(0).getRecurringExpected())
                    .isEqualByComparingTo("14.99");
            assertThat(result.getRecurringExpected()).isEqualByComparingTo("14.99");
        }

        @Test
        void multipleItemsSameCategory_sumsTogether() {
            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(subscriptionsCat));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            LocalDate anchor1 = currentPeriodStart.withDayOfMonth(10);
            LocalDate anchor2 = currentPeriodStart.withDayOfMonth(20);
            RecurringItem ri1 = RecurringItem.builder()
                    .anchorDates(List.of(anchor1.atStartOfDay()))
                    .frequencyGranularity(FrequencyGranularity.MONTH)
                    .frequencyQuantity(1)
                    .startDate(anchor1.minusYears(1).atStartOfDay())
                    .status(RecurringItemStatus.ACTIVE)
                    .categoryId(subCatId).accountId(includedAccountId)
                    .amount(new BigDecimal("50.00"))
                    .build();
            RecurringItem ri2 = RecurringItem.builder()
                    .anchorDates(List.of(anchor2.atStartOfDay()))
                    .frequencyGranularity(FrequencyGranularity.MONTH)
                    .frequencyQuantity(1)
                    .startDate(anchor2.minusYears(1).atStartOfDay())
                    .status(RecurringItemStatus.ACTIVE)
                    .categoryId(subCatId).accountId(includedAccountId)
                    .amount(new BigDecimal("30.00"))
                    .build();
            when(recurringItemRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(ri1, ri2));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getOutflow().getCategories().get(0).getRecurringExpected())
                    .isEqualByComparingTo("80.00");
        }

        @Test
        void twiceMonthly_twoAnchors() {
            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(subscriptionsCat));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            LocalDate anchor1 = currentPeriodStart.withDayOfMonth(1);
            LocalDate anchor2 = currentPeriodStart.withDayOfMonth(15);
            RecurringItem ri = RecurringItem.builder()
                    .anchorDates(List.of(anchor1.atStartOfDay(), anchor2.atStartOfDay()))
                    .frequencyGranularity(FrequencyGranularity.MONTH)
                    .frequencyQuantity(1)
                    .startDate(anchor1.minusYears(1).atStartOfDay())
                    .status(RecurringItemStatus.ACTIVE)
                    .categoryId(subCatId)
                    .accountId(includedAccountId)
                    .amount(new BigDecimal("25.00"))
                    .build();
            when(recurringItemRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(ri));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getOutflow().getCategories().get(0).getRecurringExpected())
                    .isEqualByComparingTo("50.00");
        }

        @Test
        void pausedItem_excluded() {
            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(subscriptionsCat));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            RecurringItem ri = RecurringItem.builder()
                    .anchorDates(List.of(currentPeriodStart.withDayOfMonth(10).atStartOfDay()))
                    .frequencyGranularity(FrequencyGranularity.MONTH)
                    .frequencyQuantity(1)
                    .startDate(currentPeriodStart.minusYears(1).atStartOfDay())
                    .status(RecurringItemStatus.PAUSED)
                    .categoryId(subCatId).accountId(includedAccountId)
                    .amount(new BigDecimal("50.00"))
                    .build();
            when(recurringItemRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(ri));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getOutflow().getCategories().get(0).getRecurringExpected())
                    .isEqualByComparingTo("0");
        }

        @Test
        void nonIncludedAccount_excluded() {
            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(subscriptionsCat));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            UUID otherAccountId = UUID.randomUUID();
            RecurringItem ri = RecurringItem.builder()
                    .anchorDates(List.of(currentPeriodStart.withDayOfMonth(10).atStartOfDay()))
                    .frequencyGranularity(FrequencyGranularity.MONTH)
                    .frequencyQuantity(1)
                    .startDate(currentPeriodStart.minusYears(1).atStartOfDay())
                    .status(RecurringItemStatus.ACTIVE)
                    .categoryId(subCatId).accountId(otherAccountId)
                    .amount(new BigDecimal("50.00"))
                    .build();
            when(recurringItemRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(ri));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getOutflow().getCategories().get(0).getRecurringExpected())
                    .isEqualByComparingTo("0");
        }

        @Test
        void nullCategory_excluded() {
            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(subscriptionsCat));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            RecurringItem ri = RecurringItem.builder()
                    .anchorDates(List.of(currentPeriodStart.withDayOfMonth(10).atStartOfDay()))
                    .frequencyGranularity(FrequencyGranularity.MONTH)
                    .frequencyQuantity(1)
                    .startDate(currentPeriodStart.minusYears(1).atStartOfDay())
                    .status(RecurringItemStatus.ACTIVE)
                    .categoryId(null).accountId(includedAccountId)
                    .amount(new BigDecimal("50.00"))
                    .build();
            when(recurringItemRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(ri));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getOutflow().getCategories().get(0).getRecurringExpected())
                    .isEqualByComparingTo("0");
        }

        @Test
        void parentAccumulatesChildRecurringExpected() {
            UUID parentCatId = UUID.randomUUID();
            UUID childCatId = UUID.randomUUID();

            Category parent = Category.builder()
                    .id(parentCatId).workspaceId(workspaceId).name("Bills")
                    .income(false).excludeFromBudget(false).build();
            Category child = Category.builder()
                    .id(childCatId).workspaceId(workspaceId).name("Internet")
                    .parentId(parentCatId).income(false).excludeFromBudget(false).build();

            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(parent, child));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            RecurringItem ri = RecurringItem.builder()
                    .anchorDates(List.of(currentPeriodStart.withDayOfMonth(5).atStartOfDay()))
                    .frequencyGranularity(FrequencyGranularity.MONTH)
                    .frequencyQuantity(1)
                    .startDate(currentPeriodStart.minusYears(1).atStartOfDay())
                    .status(RecurringItemStatus.ACTIVE)
                    .categoryId(childCatId).accountId(includedAccountId)
                    .amount(new BigDecimal("79.99"))
                    .build();
            when(recurringItemRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(ri));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            var parentView = result.getOutflow().getCategories().get(0);
            assertThat(parentView.getRecurringExpected()).isEqualByComparingTo("79.99");
            assertThat(parentView.getChildren().get(0).getRecurringExpected())
                    .isEqualByComparingTo("79.99");
        }

        @Test
        void yearlyItem_outsidePeriod_zero() {
            when(categoryRepository.findAllByWorkspaceId(workspaceId))
                    .thenReturn(List.of(subscriptionsCat));
            when(configRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            LocalDate farAnchor = currentPeriodStart.plusMonths(6).withDayOfMonth(1);
            RecurringItem ri = RecurringItem.builder()
                    .anchorDates(List.of(farAnchor.atStartOfDay()))
                    .frequencyGranularity(FrequencyGranularity.YEAR)
                    .frequencyQuantity(1)
                    .startDate(farAnchor.minusYears(2).atStartOfDay())
                    .status(RecurringItemStatus.ACTIVE)
                    .categoryId(subCatId).accountId(includedAccountId)
                    .amount(new BigDecimal("100.00"))
                    .build();
            when(recurringItemRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(ri));

            BudgetPeriodViewDto result = service.getView(workspaceId, budgetId, 0);

            assertThat(result.getOutflow().getCategories().get(0).getRecurringExpected())
                    .isEqualByComparingTo("0");
            assertThat(result.getRecurringExpected()).isEqualByComparingTo("0");
        }
    }
}
