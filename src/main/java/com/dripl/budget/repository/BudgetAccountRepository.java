package com.dripl.budget.repository;

import com.dripl.budget.entity.BudgetAccount;
import com.dripl.budget.entity.BudgetAccountId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetAccountRepository extends JpaRepository<BudgetAccount, BudgetAccountId> {

    List<BudgetAccount> findAllByBudgetId(UUID budgetId);

    List<BudgetAccount> findAllByBudgetIdIn(List<UUID> budgetIds);

    void deleteAllByBudgetId(UUID budgetId);
}
