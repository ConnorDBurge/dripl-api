package com.dripl.transaction.repository;

import com.dripl.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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

    long countByGroupId(UUID groupId);

    long countBySplitId(UUID splitId);

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
}
