package com.dripl.transaction.split.repository;

import com.dripl.transaction.split.entity.TransactionSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionSplitRepository extends JpaRepository<TransactionSplit, UUID> {

    List<TransactionSplit> findAllByWorkspaceId(UUID workspaceId);

    Optional<TransactionSplit> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
