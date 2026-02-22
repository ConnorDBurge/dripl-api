package com.dripl.budget.repository;

import com.dripl.budget.entity.BudgetPeriodEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetPeriodEntryRepository extends JpaRepository<BudgetPeriodEntry, UUID> {

    List<BudgetPeriodEntry> findAllByBudgetIdAndPeriodStart(UUID budgetId, LocalDate periodStart);

    Optional<BudgetPeriodEntry> findByBudgetIdAndCategoryIdAndPeriodStart(
            UUID budgetId, UUID categoryId, LocalDate periodStart);

    void deleteByBudgetIdAndCategoryIdAndPeriodStart(
            UUID budgetId, UUID categoryId, LocalDate periodStart);

    void deleteByCategoryId(UUID categoryId);
}
