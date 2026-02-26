package com.dripl.transaction.repository;

import com.dripl.account.entity.Account;
import com.dripl.budget.entity.BudgetAccount;
import com.dripl.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    List<Transaction> findAllByWorkspaceId(UUID workspaceId);

    Optional<Transaction> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<Transaction> findAllByGroupIdAndWorkspaceId(UUID groupId, UUID workspaceId);

    List<Transaction> findAllBySplitIdAndWorkspaceId(UUID splitId, UUID workspaceId);

    List<Transaction> findAllByRecurringItemIdAndWorkspaceId(UUID recurringItemId, UUID workspaceId);

    @Query("SELECT t FROM Transaction t WHERE t.workspaceId = :workspaceId " +
           "AND t.recurringItemId IS NOT NULL AND t.date >= :startDate AND t.date <= :endDate")
    List<Transaction> findLinkedToRecurringItemsInDateRange(UUID workspaceId, LocalDateTime startDate, LocalDateTime endDate);

    long countByGroupId(UUID groupId);

    // Set group ID for multiple transactions at once
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Transaction t SET t.groupId = :groupId WHERE t.id IN :transactionIds AND t.workspaceId = :workspaceId")
    int setGroupId(UUID groupId, Set<UUID> transactionIds, UUID workspaceId);

    // Clear group ID for all transactions in a group
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Transaction t SET t.groupId = NULL WHERE t.groupId = :groupId")
    int clearGroupId(UUID groupId);

    // Clear split ID for all transactions in a split
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Transaction t SET t.splitId = NULL WHERE t.splitId = :splitId")
    int clearSplitId(UUID splitId);

    // Sum transaction amounts to a category scoped to a budget's included accounts
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "JOIN BudgetAccount ba ON t.accountId = ba.accountId " +
           "WHERE ba.budgetId = :budgetId AND t.categoryId = :categoryId " +
           "AND t.date >= :startDate AND t.date <= :endDate")
    BigDecimal sumAmountByBudgetIdAndCategoryIdAndDateBetween(
            UUID budgetId, UUID categoryId, LocalDateTime startDate, LocalDateTime endDate);
}
