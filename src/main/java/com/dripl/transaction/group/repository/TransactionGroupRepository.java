package com.dripl.transaction.group.repository;

import com.dripl.transaction.group.entity.TransactionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionGroupRepository extends JpaRepository<TransactionGroup, UUID> {

    List<TransactionGroup> findAllByWorkspaceId(UUID workspaceId);

    Optional<TransactionGroup> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
