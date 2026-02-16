package com.dripl.account.repository;

import com.dripl.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findAllByWorkspaceId(UUID workspaceId);

    Optional<Account> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);
}
