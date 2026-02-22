package com.dripl.account.repository;

import com.dripl.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findAllByWorkspaceId(UUID workspaceId);

    Optional<Account> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.accountId = :accountId")
    BigDecimal sumTransactionAmounts(UUID accountId);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.id IN :accountIds")
    BigDecimal sumBalancesByIds(List<UUID> accountIds);
}
