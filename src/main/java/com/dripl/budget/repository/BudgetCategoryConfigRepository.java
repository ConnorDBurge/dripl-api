package com.dripl.budget.repository;

import com.dripl.budget.entity.BudgetCategoryConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetCategoryConfigRepository extends JpaRepository<BudgetCategoryConfig, UUID> {

    List<BudgetCategoryConfig> findAllByWorkspaceId(UUID workspaceId);

    List<BudgetCategoryConfig> findAllByBudgetId(UUID budgetId);

    Optional<BudgetCategoryConfig> findByBudgetIdAndCategoryId(UUID budgetId, UUID categoryId);
}
