package com.dripl.budget.service;

import com.dripl.account.repository.AccountRepository;
import com.dripl.budget.dto.BudgetCategoryViewDto;
import com.dripl.budget.dto.BudgetPeriodViewDto;
import com.dripl.budget.dto.BudgetSectionDto;
import com.dripl.budget.entity.Budget;
import com.dripl.budget.entity.BudgetAccount;
import com.dripl.budget.entity.BudgetCategoryConfig;
import com.dripl.budget.entity.BudgetPeriodEntry;
import com.dripl.budget.enums.RolloverType;
import com.dripl.budget.repository.BudgetAccountRepository;
import com.dripl.budget.repository.BudgetCategoryConfigRepository;
import com.dripl.budget.repository.BudgetPeriodEntryRepository;
import com.dripl.budget.util.BudgetPeriodCalculator;
import com.dripl.budget.util.PeriodRange;
import com.dripl.category.entity.Category;
import com.dripl.category.repository.CategoryRepository;
import com.dripl.common.exception.BadRequestException;
import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.enums.RecurringItemStatus;
import com.dripl.recurring.repository.RecurringItemRepository;
import com.dripl.recurring.util.RecurringOccurrenceCalculator;
import com.dripl.transaction.repository.TransactionRepository;
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
    private final AccountRepository accountRepository;
    private final BudgetAccountRepository budgetAccountRepository;
    private final BudgetCategoryConfigRepository configRepository;
    private final BudgetPeriodEntryRepository entryRepository;
    private final BudgetService budgetService;

    @Transactional
    public BudgetPeriodViewDto getView(UUID workspaceId, UUID budgetId, int periodOffset) {
        Budget budget = budgetService.findBudget(workspaceId, budgetId);
        requireBudgetConfigured(budget);
        PeriodRange period = BudgetPeriodCalculator.computePeriodByOffset(budget, periodOffset);
        return buildPeriodView(budget, period);
    }

    @Transactional
    public BudgetPeriodViewDto getView(UUID workspaceId, UUID budgetId, LocalDate periodStart) {
        Budget budget = budgetService.findBudget(workspaceId, budgetId);
        requireBudgetConfigured(budget);
        PeriodRange period = BudgetPeriodCalculator.computePeriod(budget, periodStart);
        return buildPeriodView(budget, period);
    }

    private BudgetPeriodViewDto buildPeriodView(Budget budget, PeriodRange period) {
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
        Map<UUID, BudgetCategoryViewDto> viewMap = new HashMap<>();
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

            viewMap.put(cat.getId(), BudgetCategoryViewDto.builder()
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
        List<BudgetCategoryViewDto> roots = new ArrayList<>();
        for (BudgetCategoryViewDto view : viewMap.values()) {
            if (view.getParentId() != null && viewMap.containsKey(view.getParentId())) {
                viewMap.get(view.getParentId()).getChildren().add(view);
            } else {
                roots.add(view);
            }
        }

        // Zero out direct expected on parents — parents roll up from children only
        for (BudgetCategoryViewDto view : viewMap.values()) {
            if (!view.getChildren().isEmpty()) {
                view.setExpected(BigDecimal.ZERO);
                view.setRecurringExpected(BigDecimal.ZERO);
                view.setActivity(BigDecimal.ZERO);
                view.setAvailable(BigDecimal.ZERO);
                view.setRolledOver(BigDecimal.ZERO);
                view.getChildren().sort(Comparator.comparingInt(BudgetCategoryViewDto::getDisplayOrder));
            }
        }
        roots.sort(Comparator.comparingInt(BudgetCategoryViewDto::getDisplayOrder));

        // Accumulate parent totals from children
        for (BudgetCategoryViewDto root : roots) {
            accumulateChildren(root);
        }

        // Split into inflow/outflow
        Map<Boolean, List<Category>> catByIncome = budgetCategories.stream()
                .filter(c -> c.getParentId() == null || !viewMap.containsKey(c.getParentId()))
                .collect(Collectors.partitioningBy(Category::isIncome));

        List<BudgetCategoryViewDto> inflowRoots = roots.stream()
                .filter(v -> catByIncome.getOrDefault(true, List.of()).stream()
                        .anyMatch(c -> c.getId().equals(v.getCategoryId())))
                .toList();
        List<BudgetCategoryViewDto> outflowRoots = roots.stream()
                .filter(v -> catByIncome.getOrDefault(false, List.of()).stream()
                        .anyMatch(c -> c.getId().equals(v.getCategoryId())))
                .toList();

        // Compute available pool from AVAILABLE_POOL rollovers in previous period
        BigDecimal availablePool = computeAvailablePool(budget, period, budgetCategories, rolloverMap);

        BudgetSectionDto inflowSection = buildSection(inflowRoots);
        BudgetSectionDto outflowSection = buildSection(outflowRoots);

        BigDecimal totalRolledOver = roots.stream()
                .map(BudgetCategoryViewDto::getRolledOver)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(availablePool);

        BigDecimal totalRecurringExpected = roots.stream()
                .map(BudgetCategoryViewDto::getRecurringExpected)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal budgetable = inflowSection.getExpected().add(availablePool);
        BigDecimal totalBudgeted = outflowSection.getExpected();
        BigDecimal leftToBudget = budgetable.subtract(totalBudgeted);

        // netTotalAvailable = sum of linked account balances
        List<UUID> linkedAccountIds = new ArrayList<>(includedAccountIds);
        BigDecimal netTotalAvailable = linkedAccountIds.isEmpty()
                ? BigDecimal.ZERO
                : accountRepository.sumBalancesByIds(linkedAccountIds);

        return BudgetPeriodViewDto.builder()
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

    private void accumulateChildren(BudgetCategoryViewDto parent) {
        for (BudgetCategoryViewDto child : parent.getChildren()) {
            accumulateChildren(child);
            parent.setExpected(parent.getExpected().add(child.getExpected()));
            parent.setRecurringExpected(parent.getRecurringExpected().add(child.getRecurringExpected()));
            parent.setActivity(parent.getActivity().add(child.getActivity()));
            parent.setAvailable(parent.getAvailable().add(child.getAvailable()));
            parent.setRolledOver(parent.getRolledOver().add(child.getRolledOver()));
        }
    }

    private BudgetSectionDto buildSection(List<BudgetCategoryViewDto> roots) {
        BigDecimal totalExpected = BigDecimal.ZERO;
        BigDecimal totalActivity = BigDecimal.ZERO;
        BigDecimal totalAvailable = BigDecimal.ZERO;

        for (BudgetCategoryViewDto root : roots) {
            totalExpected = totalExpected.add(root.getExpected());
            totalActivity = totalActivity.add(root.getActivity());
            totalAvailable = totalAvailable.add(root.getAvailable());
        }

        return BudgetSectionDto.builder()
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

        for (RecurringItem ri : items) {
            if (ri.getStatus() != RecurringItemStatus.ACTIVE) continue;
            if (ri.getCategoryId() == null) continue;
            if (!includedAccountIds.contains(ri.getAccountId())) continue;

            int occurrences = RecurringOccurrenceCalculator.countOccurrences(ri, period.start(), period.end());
            if (occurrences > 0) {
                BigDecimal total = ri.getAmount().multiply(BigDecimal.valueOf(occurrences));
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
}
