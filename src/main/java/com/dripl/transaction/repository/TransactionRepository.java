package com.dripl.transaction.repository;

import com.dripl.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    List<Transaction> findAllByWorkspaceId(UUID workspaceId);

    Optional<Transaction> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
