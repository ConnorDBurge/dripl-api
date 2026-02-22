package com.dripl.budget.service;

import com.dripl.budget.dto.SetExpectedAmountDto;
import com.dripl.budget.dto.UpdateBudgetCategoryConfigDto;
import com.dripl.budget.entity.Budget;
import com.dripl.budget.entity.BudgetCategoryConfig;
import com.dripl.budget.entity.BudgetPeriodEntry;
import com.dripl.budget.enums.RolloverType;
import com.dripl.budget.repository.BudgetCategoryConfigRepository;
import com.dripl.budget.repository.BudgetPeriodEntryRepository;
import com.dripl.budget.util.BudgetPeriodCalculator;
import com.dripl.budget.util.PeriodRange;
import com.dripl.category.repository.CategoryRepository;
import com.dripl.category.service.CategoryService;
import com.dripl.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetConfigService {

    private final BudgetCategoryConfigRepository configRepository;
    private final BudgetPeriodEntryRepository entryRepository;
    private final BudgetService budgetService;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;

    @Transactional
    public BudgetCategoryConfig updateConfig(UUID workspaceId, UUID budgetId, UUID categoryId,
                                             UpdateBudgetCategoryConfigDto dto) {
        categoryService.getCategory(categoryId, workspaceId);

        BudgetCategoryConfig config = configRepository.findByBudgetIdAndCategoryId(budgetId, categoryId)
                .orElseGet(() -> BudgetCategoryConfig.builder()
                        .workspaceId(workspaceId)
                        .budgetId(budgetId)
                        .categoryId(categoryId)
                        .build());

        config.setRolloverType(dto.getRolloverType());
        config = configRepository.save(config);

        log.info("Set rollover type {} for category {} in budget {}", dto.getRolloverType(), categoryId, budgetId);
        return config;
    }

    @Transactional
    public void setExpectedAmount(UUID workspaceId, UUID budgetId, UUID categoryId, LocalDate periodStart,
                                   SetExpectedAmountDto dto) {
        categoryService.getCategory(categoryId, workspaceId);

        if (categoryRepository.existsByParentId(categoryId)) {
            throw new BadRequestException("Cannot set expected amount on a parent category. Expected amounts roll up from children.");
        }

        Budget budget = budgetService.findBudget(workspaceId, budgetId);
        PeriodRange period = BudgetPeriodCalculator.computePeriod(budget, periodStart);
        if (!period.start().equals(periodStart)) {
            throw new BadRequestException(
                    "periodStart %s does not align with budget period configuration. Expected %s"
                            .formatted(periodStart, period.start()));
        }

        if (dto.getExpectedAmount() == null) {
            entryRepository.deleteByBudgetIdAndCategoryIdAndPeriodStart(budgetId, categoryId, periodStart);
            log.info("Cleared expected amount for category {} in period {} budget {}", categoryId, periodStart, budgetId);
            return;
        }

        BudgetPeriodEntry entry = entryRepository
                .findByBudgetIdAndCategoryIdAndPeriodStart(budgetId, categoryId, periodStart)
                .orElseGet(() -> BudgetPeriodEntry.builder()
                        .workspaceId(workspaceId)
                        .budgetId(budgetId)
                        .categoryId(categoryId)
                        .periodStart(periodStart)
                        .build());

        entry.setExpectedAmount(dto.getExpectedAmount());
        entryRepository.save(entry);

        log.info("Set expected amount {} for category {} in period {} budget {}",
                dto.getExpectedAmount(), categoryId, periodStart, budgetId);
    }

    @Transactional(readOnly = true)
    public RolloverType getRolloverType(UUID budgetId, UUID categoryId) {
        return configRepository.findByBudgetIdAndCategoryId(budgetId, categoryId)
                .map(BudgetCategoryConfig::getRolloverType)
                .orElse(RolloverType.NONE);
    }
}
