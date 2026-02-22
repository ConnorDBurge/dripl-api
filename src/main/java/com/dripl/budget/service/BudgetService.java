package com.dripl.budget.service;

import com.dripl.account.repository.AccountRepository;
import com.dripl.budget.dto.CreateBudgetDto;
import com.dripl.budget.dto.UpdateBudgetDto;
import com.dripl.budget.entity.Budget;
import com.dripl.budget.entity.BudgetAccount;
import com.dripl.budget.repository.BudgetAccountRepository;
import com.dripl.budget.repository.BudgetRepository;
import com.dripl.common.audit.BaseEntity;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final BudgetAccountRepository budgetAccountRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<Budget> listBudgets(UUID workspaceId) {
        List<Budget> budgets = budgetRepository.findAllByWorkspaceId(workspaceId);
        List<UUID> budgetIds = budgets.stream().map(Budget::getId).toList();
        Map<UUID, List<UUID>> accountIdsByBudgetId = budgetAccountRepository.findAllByBudgetIdIn(budgetIds).stream()
                .collect(Collectors.groupingBy(
                        BudgetAccount::getBudgetId,
                        Collectors.mapping(BudgetAccount::getAccountId, Collectors.toList())));
        budgets.forEach(b -> b.setAccountIds(accountIdsByBudgetId.getOrDefault(b.getId(), List.of())));
        return budgets;
    }

    @Transactional(readOnly = true)
    public Budget getBudget(UUID workspaceId, UUID budgetId) {
        Budget budget = findBudget(workspaceId, budgetId);
        budget.setAccountIds(loadAccountIds(budgetId));
        return budget;
    }

    @Transactional
    public Budget createBudget(UUID workspaceId, CreateBudgetDto dto) {
        if (budgetRepository.existsByWorkspaceIdAndName(workspaceId, dto.getName())) {
            throw new BadRequestException("A budget with name '%s' already exists".formatted(dto.getName()));
        }
        validatePeriodConfig(dto.getAnchorDay1(), dto.getAnchorDay2(), dto.getIntervalDays(), dto.getAnchorDate());

        Budget budget = Budget.builder()
                .workspaceId(workspaceId)
                .name(dto.getName())
                .anchorDay1(dto.getAnchorDay1())
                .anchorDay2(dto.getAnchorDay2())
                .intervalDays(dto.getIntervalDays())
                .anchorDate(dto.getAnchorDate())
                .build();
        budget = budgetRepository.save(budget);
        log.info("Created budget '{}' ({})", budget.getName(), budget.getId());

        if (dto.getAccountIds() != null && !dto.getAccountIds().isEmpty()) {
            validateAccountIds(workspaceId, dto.getAccountIds());
            saveAccountLinks(budget.getId(), dto.getAccountIds());
            budget.setAccountIds(dto.getAccountIds());
        }

        return budget;
    }

    @Transactional
    public Budget updateBudget(UUID workspaceId, UUID budgetId, UpdateBudgetDto dto) {
        Budget budget = findBudget(workspaceId, budgetId);

        if (dto.getName() != null) {
            if (!dto.getName().equals(budget.getName())
                    && budgetRepository.existsByWorkspaceIdAndName(workspaceId, dto.getName())) {
                throw new BadRequestException("A budget with name '%s' already exists".formatted(dto.getName()));
            }
            budget.setName(dto.getName());
        }
        if (dto.getAnchorDay1() != null) budget.setAnchorDay1(dto.getAnchorDay1());
        if (dto.getAnchorDay2() != null) budget.setAnchorDay2(dto.getAnchorDay2());
        if (dto.getIntervalDays() != null) budget.setIntervalDays(dto.getIntervalDays());
        if (dto.getAnchorDate() != null) budget.setAnchorDate(dto.getAnchorDate());

        validatePeriodConfig(budget.getAnchorDay1(), budget.getAnchorDay2(),
                budget.getIntervalDays(), budget.getAnchorDate());

        budget = budgetRepository.save(budget);

        if (dto.getAccountIds() != null) {
            if (!dto.getAccountIds().isEmpty()) {
                validateAccountIds(workspaceId, dto.getAccountIds());
            }
            budgetAccountRepository.deleteAllByBudgetId(budgetId);
            if (!dto.getAccountIds().isEmpty()) {
                saveAccountLinks(budgetId, dto.getAccountIds());
            }
            budget.setAccountIds(dto.getAccountIds());
        } else {
            budget.setAccountIds(loadAccountIds(budgetId));
        }

        log.info("Updated budget '{}' ({})", budget.getName(), budget.getId());
        return budget;
    }

    @Transactional
    public void deleteBudget(UUID workspaceId, UUID budgetId) {
        Budget budget = findBudget(workspaceId, budgetId);
        budgetRepository.delete(budget);
        log.info("Deleted budget '{}' ({})", budget.getName(), budget.getId());
    }

    public Budget findBudget(UUID workspaceId, UUID budgetId) {
        return budgetRepository.findByIdAndWorkspaceId(budgetId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found"));
    }

    private void validatePeriodConfig(Integer anchorDay1, Integer anchorDay2,
                                       Integer intervalDays, LocalDate anchorDate) {
        // Must have at least one mode configured
        boolean hasAnchorMode = anchorDay1 != null;
        boolean hasFixedMode = anchorDate != null && intervalDays != null;

        if (!hasAnchorMode && !hasFixedMode) {
            throw new BadRequestException("Budget must have period configuration: either anchorDay1 or anchorDate + intervalDays");
        }
        if (hasAnchorMode && hasFixedMode) {
            throw new BadRequestException("Cannot mix anchor-in-month and fixed-interval modes");
        }
        if (hasAnchorMode) {
            if (anchorDay1 < 1 || anchorDay1 > 31) {
                throw new BadRequestException("anchorDay1 must be between 1 and 31");
            }
            if (anchorDay2 != null) {
                if (anchorDay2 < 1 || anchorDay2 > 31) {
                    throw new BadRequestException("anchorDay2 must be between 1 and 31");
                }
                if (anchorDay1.equals(anchorDay2)) {
                    throw new BadRequestException("anchorDay1 and anchorDay2 must be different");
                }
            }
        }
        if (hasFixedMode && intervalDays < 1) {
            throw new BadRequestException("intervalDays must be at least 1");
        }
    }

    private void validateAccountIds(UUID workspaceId, List<UUID> accountIds) {
        List<UUID> workspaceAccountIds = accountRepository.findAllByWorkspaceId(workspaceId).stream()
                .map(BaseEntity::getId)
                .toList();
        List<UUID> invalid = accountIds.stream()
                .filter(id -> !workspaceAccountIds.contains(id))
                .toList();
        if (!invalid.isEmpty()) {
            throw new BadRequestException("Account IDs not found in workspace: %s".formatted(invalid));
        }
    }

    private void saveAccountLinks(UUID budgetId, List<UUID> accountIds) {
        List<BudgetAccount> links = accountIds.stream()
                .map(accountId -> BudgetAccount.builder()
                        .budgetId(budgetId)
                        .accountId(accountId)
                        .build())
                .toList();
        budgetAccountRepository.saveAll(links);
    }

    private List<UUID> loadAccountIds(UUID budgetId) {
        return budgetAccountRepository.findAllByBudgetId(budgetId).stream()
                .map(BudgetAccount::getAccountId)
                .toList();
    }
}
