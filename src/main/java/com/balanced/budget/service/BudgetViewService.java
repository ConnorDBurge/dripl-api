package com.balanced.budget.service;

import com.balanced.account.repository.AccountRepository;
import com.balanced.budget.dto.BudgetCategoryViewResponse;
import com.balanced.budget.dto.BudgetPeriodViewResponse;
import com.balanced.budget.dto.BudgetSectionResponse;
import com.balanced.budget.entity.Budget;
import com.balanced.budget.entity.BudgetAccount;
import com.balanced.budget.entity.BudgetCategoryConfig;
import com.balanced.budget.entity.BudgetPeriodEntry;
import com.balanced.budget.enums.RolloverType;
import com.balanced.budget.repository.BudgetAccountRepository;
import com.balanced.budget.repository.BudgetCategoryConfigRepository;
import com.balanced.budget.repository.BudgetPeriodEntryRepository;
import com.balanced.budget.util.BudgetPeriodCalculator;
import com.balanced.budget.util.PeriodRange;
import com.balanced.category.entity.Category;
import com.balanced.category.repository.CategoryRepository;
import com.balanced.common.exception.BadRequestException;
import com.balanced.recurring.entity.RecurringItem;
import com.balanced.recurring.entity.RecurringItemOverride;
import com.balanced.recurring.enums.RecurringItemStatus;
import com.balanced.recurring.repository.RecurringItemOverrideRepository;
import com.balanced.recurring.repository.RecurringItemRepository;
import com.balanced.recurring.util.RecurringOccurrenceCalculator;
import com.balanced.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetViewService {

    private static final int MAX_ROLLOVER_DEPTH = 24;

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringItemRepository recurringItemRepository;
    private final RecurringItemOverrideRepository recurringItemOverrideRepository;
    private final AccountRepository accountRepository;
    private final BudgetAccountRepository budgetAccountRepository;
    private final BudgetCategoryConfigRepository configRepository;
    private final BudgetPeriodEntryRepository entryRepository;
    private final BudgetService budgetService;

    @Transactional
    public BudgetPeriodViewResponse getView(UUID workspaceId, UUID budgetId, int periodOffset) {
        Budget budget = budgetService.findBudget(workspaceId, budgetId);
        requireBudgetConfigured(budget);
        PeriodRange period = BudgetPeriodCalculator.computePeriodByOffset(budget, periodOffset);
        return buildPeriodView(budget, period);
    }

    @Transactional
    public BudgetPeriodViewResponse getView(UUID workspaceId, UUID budgetId, LocalDate periodStart) {
        Budget budget = budgetService.findBudget(workspaceId, budgetId);
        requireBudgetConfigured(budget);
        PeriodRange period = BudgetPeriodCalculator.computePeriod(budget, periodStart);
        return buildPeriodView(budget, period);
    }

    private BudgetPeriodViewResponse buildPeriodView(Budget budget, PeriodRange period) {
        UUID budgetId = budget.getId();
        UUID workspaceId = budget.getWorkspaceId();

        List<Category> allCategories = categoryRepository.findAllByWorkspaceId(workspaceId);
        List<BudgetPeriodEntry> entries = entryRepository.findAllByBudgetIdAndPeriodStart(budgetId, period.start());
        List<BudgetCategoryConfig> configs = configRepository.findAllByBudgetId(budgetId);

        // Build lookup maps
        Map<UUID, BigDecimal> expectedMap = entries.stream()
                .collect(Collectors.toMap(BudgetPeriodEntry::getCategoryId, BudgetPeriodEntry::getExpectedAmount));
        Map<UUID, RolloverType> rolloverMap = configs.stream()
                .collect(Collectors.toMap(BudgetCategoryConfig::getCategoryId, BudgetCategoryConfig::getRolloverType));

        // Filter out excluded categories
        List<Category> budgetCategories = allCategories.stream()
                .filter(c -> !c.isExcludeFromBudget())
                .toList();

        // Get budget's included account IDs for recurring expected computation
        Set<UUID> includedAccountIds = budgetAccountRepository.findAllByBudgetId(budgetId).stream()
                .map(BudgetAccount::getAccountId)
                .collect(Collectors.toSet());
        Map<UUID, BigDecimal> recurringExpectedMap = computeRecurringExpected(workspaceId, period, includedAccountIds);

        // Build a flat view for each leaf/root category
        Map<UUID, BudgetCategoryViewResponse> viewMap = new HashMap<>();
        for (Category cat : budgetCategories) {
            BigDecimal expected = expectedMap.getOrDefault(cat.getId(), BigDecimal.ZERO);
            BigDecimal recurringExpected = recurringExpectedMap.getOrDefault(cat.getId(), BigDecimal.ZERO);
            BigDecimal activity = computeActivity(budgetId, cat.getId(), period);
            RolloverType rolloverType = rolloverMap.getOrDefault(cat.getId(), RolloverType.NONE);
            BigDecimal rolledOver = computeRolledOver(budget, cat.getId(), period, rolloverType, 0);

            BigDecimal available;
            if (cat.isIncome()) {
                available = expected.add(rolledOver).subtract(activity);
            } else {
                available = expected.add(rolledOver).add(activity);
            }

            viewMap.put(cat.getId(), BudgetCategoryViewResponse.builder()
                    .categoryId(cat.getId())
                    .name(cat.getName())
                    .parentId(cat.getParentId())
                    .displayOrder(cat.getDisplayOrder())
                    .expected(expected)
                    .recurringExpected(recurringExpected)
                    .activity(activity)
                    .available(available)
                    .rolledOver(rolledOver)
                    .rolloverType(rolloverType)
                    .children(new ArrayList<>())
                    .build());
        }

        // Build tree — nest children under parents
        List<BudgetCategoryViewResponse> roots = new ArrayList<>();
        for (BudgetCategoryViewResponse view : viewMap.values()) {
            if (view.getParentId() != null && viewMap.containsKey(view.getParentId())) {
                viewMap.get(view.getParentId()).getChildren().add(view);
            } else {
                roots.add(view);
            }
        }

        // Zero out direct expected on parents — parents roll up from children only
        for (BudgetCategoryViewResponse view : viewMap.values()) {
            if (!view.getChildren().isEmpty()) {
                view.setExpected(BigDecimal.ZERO);
                view.setRecurringExpected(BigDecimal.ZERO);
                view.setActivity(BigDecimal.ZERO);
                view.setAvailable(BigDecimal.ZERO);
                view.setRolledOver(BigDecimal.ZERO);
                view.getChildren().sort(Comparator.comparingInt(BudgetCategoryViewResponse::getDisplayOrder));
            }
        }
        roots.sort(Comparator.comparingInt(BudgetCategoryViewResponse::getDisplayOrder));

        // Accumulate parent totals from children
        for (BudgetCategoryViewResponse root : roots) {
            accumulateChildren(root);
        }

        // Split into inflow/outflow
        Map<Boolean, List<Category>> catByIncome = budgetCategories.stream()
                .filter(c -> c.getParentId() == null || !viewMap.containsKey(c.getParentId()))
                .collect(Collectors.partitioningBy(Category::isIncome));

        List<BudgetCategoryViewResponse> inflowRoots = roots.stream()
                .filter(v -> catByIncome.getOrDefault(true, List.of()).stream()
                        .anyMatch(c -> c.getId().equals(v.getCategoryId())))
                .toList();
        List<BudgetCategoryViewResponse> outflowRoots = roots.stream()
                .filter(v -> catByIncome.getOrDefault(false, List.of()).stream()
                        .anyMatch(c -> c.getId().equals(v.getCategoryId())))
                .toList();

        // Compute available pool from AVAILABLE_POOL rollovers in previous period
        BigDecimal availablePool = computeAvailablePool(budget, period, budgetCategories, rolloverMap);

        BudgetSectionResponse inflowSection = buildSection(new ArrayList<>(inflowRoots));
        BudgetSectionResponse outflowSection = buildSection(new ArrayList<>(outflowRoots));

        // Inject uncategorized transaction rows
        BigDecimal uncategorizedInflow = transactionRepository.sumPositiveUncategorizedByBudgetIdAndDateBetween(
                budgetId, period.start().atStartOfDay(), period.end().atTime(LocalTime.MAX));
        BigDecimal uncategorizedOutflow = transactionRepository.sumNegativeUncategorizedByBudgetIdAndDateBetween(
                budgetId, period.start().atStartOfDay(), period.end().atTime(LocalTime.MAX));

        if (uncategorizedInflow.signum() != 0) {
            BudgetCategoryViewResponse uncatInflow = buildUncategorizedRow(uncategorizedInflow);
            inflowSection.getCategories().add(uncatInflow);
            inflowSection.setActivity(inflowSection.getActivity().add(uncategorizedInflow));
            inflowSection.setAvailable(inflowSection.getAvailable().add(uncategorizedInflow));
        }
        if (uncategorizedOutflow.signum() != 0) {
            BudgetCategoryViewResponse uncatOutflow = buildUncategorizedRow(uncategorizedOutflow);
            outflowSection.getCategories().add(uncatOutflow);
            outflowSection.setActivity(outflowSection.getActivity().add(uncategorizedOutflow));
            outflowSection.setAvailable(outflowSection.getAvailable().add(uncategorizedOutflow));
        }

        BigDecimal totalRolledOver = roots.stream()
                .map(BudgetCategoryViewResponse::getRolledOver)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(availablePool);

        BigDecimal totalRecurringExpected = roots.stream()
                .map(BudgetCategoryViewResponse::getRecurringExpected)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal budgetable = inflowSection.getExpected().add(availablePool);
        BigDecimal totalBudgeted = outflowSection.getExpected();
        BigDecimal leftToBudget = budgetable.subtract(totalBudgeted);

        // netTotalAvailable = sum of linked account balances
        List<UUID> linkedAccountIds = new ArrayList<>(includedAccountIds);
        BigDecimal netTotalAvailable = linkedAccountIds.isEmpty()
                ? BigDecimal.ZERO
                : accountRepository.sumBalancesByIds(linkedAccountIds);

        return BudgetPeriodViewResponse.builder()
                .periodStart(period.start())
                .periodEnd(period.end())
                .budgetable(budgetable)
                .totalBudgeted(totalBudgeted)
                .leftToBudget(leftToBudget)
                .netTotalAvailable(netTotalAvailable)
                .recurringExpected(totalRecurringExpected)
                .availablePool(availablePool)
                .totalRolledOver(totalRolledOver)
                .inflow(inflowSection)
                .outflow(outflowSection)
                .build();
    }

    private void accumulateChildren(BudgetCategoryViewResponse parent) {
        for (BudgetCategoryViewResponse child : parent.getChildren()) {
            accumulateChildren(child);
            parent.setExpected(parent.getExpected().add(child.getExpected()));
            parent.setRecurringExpected(parent.getRecurringExpected().add(child.getRecurringExpected()));
            parent.setActivity(parent.getActivity().add(child.getActivity()));
            parent.setAvailable(parent.getAvailable().add(child.getAvailable()));
            parent.setRolledOver(parent.getRolledOver().add(child.getRolledOver()));
        }
    }

    private BudgetSectionResponse buildSection(List<BudgetCategoryViewResponse> roots) {
        BigDecimal totalExpected = BigDecimal.ZERO;
        BigDecimal totalActivity = BigDecimal.ZERO;
        BigDecimal totalAvailable = BigDecimal.ZERO;

        for (BudgetCategoryViewResponse root : roots) {
            totalExpected = totalExpected.add(root.getExpected());
            totalActivity = totalActivity.add(root.getActivity());
            totalAvailable = totalAvailable.add(root.getAvailable());
        }

        return BudgetSectionResponse.builder()
                .expected(totalExpected)
                .activity(totalActivity)
                .available(totalAvailable)
                .categories(new ArrayList<>(roots))
                .build();
    }

    BigDecimal computeActivity(UUID budgetId, UUID categoryId, PeriodRange period) {
        return transactionRepository.sumAmountByBudgetIdAndCategoryIdAndDateBetween(
                budgetId, categoryId,
                period.start().atStartOfDay(),
                period.end().atTime(LocalTime.MAX));
    }

    BigDecimal computeRolledOver(Budget budget, UUID categoryId, PeriodRange currentPeriod,
                                  RolloverType rolloverType, int depth) {
        if (rolloverType == RolloverType.NONE || depth >= MAX_ROLLOVER_DEPTH) {
            return BigDecimal.ZERO;
        }

        PeriodRange prevPeriod = BudgetPeriodCalculator.computePreviousPeriod(budget, currentPeriod);

        BigDecimal prevExpected = entryRepository
                .findByBudgetIdAndCategoryIdAndPeriodStart(budget.getId(), categoryId, prevPeriod.start())
                .map(BudgetPeriodEntry::getExpectedAmount)
                .orElse(BigDecimal.ZERO);
        BigDecimal prevActivity = computeActivity(budget.getId(), categoryId, prevPeriod);
        BigDecimal prevRolledOver = computeRolledOver(budget, categoryId, prevPeriod, rolloverType, depth + 1);

        BigDecimal prevAvailable = prevExpected.add(prevRolledOver).add(prevActivity);

        if (rolloverType == RolloverType.SAME_CATEGORY) {
            return prevAvailable;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal computeAvailablePool(Budget budget, PeriodRange currentPeriod,
                                             List<Category> categories, Map<UUID, RolloverType> rolloverMap) {
        BigDecimal pool = BigDecimal.ZERO;
        PeriodRange prevPeriod = BudgetPeriodCalculator.computePreviousPeriod(budget, currentPeriod);

        for (Category cat : categories) {
            RolloverType rolloverType = rolloverMap.getOrDefault(cat.getId(), RolloverType.NONE);
            if (rolloverType != RolloverType.AVAILABLE_POOL) {
                continue;
            }

            BigDecimal prevExpected = entryRepository
                    .findByBudgetIdAndCategoryIdAndPeriodStart(budget.getId(), cat.getId(), prevPeriod.start())
                    .map(BudgetPeriodEntry::getExpectedAmount)
                    .orElse(BigDecimal.ZERO);
            BigDecimal prevActivity = computeActivity(budget.getId(), cat.getId(), prevPeriod);
            BigDecimal prevRolledOverToPool = computeRolledOverForPool(budget, cat.getId(), prevPeriod, 0);
            BigDecimal prevAvailable = prevExpected.add(prevRolledOverToPool).add(prevActivity);
            pool = pool.add(prevAvailable);
        }

        return pool;
    }

    private BigDecimal computeRolledOverForPool(Budget budget, UUID categoryId,
                                                 PeriodRange currentPeriod, int depth) {
        if (depth >= MAX_ROLLOVER_DEPTH) {
            return BigDecimal.ZERO;
        }
        PeriodRange prevPeriod = BudgetPeriodCalculator.computePreviousPeriod(budget, currentPeriod);
        BigDecimal prevExpected = entryRepository
                .findByBudgetIdAndCategoryIdAndPeriodStart(budget.getId(), categoryId, prevPeriod.start())
                .map(BudgetPeriodEntry::getExpectedAmount)
                .orElse(BigDecimal.ZERO);
        BigDecimal prevActivity = computeActivity(budget.getId(), categoryId, prevPeriod);
        BigDecimal prevRolled = computeRolledOverForPool(budget, categoryId, prevPeriod, depth + 1);
        return prevExpected.add(prevRolled).add(prevActivity);
    }

    /**
     * Computes the total recurring expected amount per category for a budget period.
     * Only includes recurring items whose account is in the budget's included accounts.
     */
    Map<UUID, BigDecimal> computeRecurringExpected(UUID workspaceId, PeriodRange period, Set<UUID> includedAccountIds) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        List<RecurringItem> items = recurringItemRepository.findAllByWorkspaceId(workspaceId);

        // Batch-load overrides for this period
        Map<String, RecurringItemOverride> overrideMap = recurringItemOverrideRepository
                .findByWorkspaceIdAndOccurrenceDateBetween(workspaceId, period.start(), period.end())
                .stream()
                .collect(Collectors.toMap(
                        o -> o.getRecurringItemId() + ":" + o.getOccurrenceDate(),
                        o -> o));

        for (RecurringItem ri : items) {
            if (ri.getStatus() != RecurringItemStatus.ACTIVE) continue;
            if (ri.getCategoryId() == null) continue;
            if (!includedAccountIds.contains(ri.getAccountId())) continue;

            List<LocalDate> dates = RecurringOccurrenceCalculator.computeOccurrences(ri, period.start(), period.end());

            BigDecimal total = BigDecimal.ZERO;
            for (LocalDate date : dates) {
                String key = ri.getId() + ":" + date;
                RecurringItemOverride override = overrideMap.get(key);

                // recurringExpected always uses the expected amount (override ?? default),
                // never the transaction amount — that's captured in activity
                if (override != null && override.getAmount() != null) {
                    total = total.add(override.getAmount());
                } else {
                    total = total.add(ri.getAmount());
                }
            }

            if (total.signum() != 0) {
                result.merge(ri.getCategoryId(), total, BigDecimal::add);
            }
        }
        return result;
    }

    private void requireBudgetConfigured(Budget budget) {
        if (!budget.isBudgetConfigured()) {
            throw new BadRequestException("Budget period is not configured.");
        }
    }

    private BudgetCategoryViewResponse buildUncategorizedRow(BigDecimal activity) {
        return BudgetCategoryViewResponse.builder()
                .categoryId(null)
                .name("Uncategorized")
                .parentId(null)
                .displayOrder(Integer.MAX_VALUE)
                .expected(BigDecimal.ZERO)
                .recurringExpected(BigDecimal.ZERO)
                .activity(activity)
                .available(activity)
                .rolledOver(BigDecimal.ZERO)
                .rolloverType(RolloverType.NONE)
                .children(new ArrayList<>())
                .build();
    }
}
