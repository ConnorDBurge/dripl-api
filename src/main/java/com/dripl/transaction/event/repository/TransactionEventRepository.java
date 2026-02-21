package com.dripl.transaction.event.repository;

import com.dripl.transaction.event.entity.TransactionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionEventRepository extends JpaRepository<TransactionEvent, UUID> {

    List<TransactionEvent> findAllByTransactionIdAndWorkspaceIdOrderByPerformedAtDesc(UUID transactionId, UUID workspaceId);
}
