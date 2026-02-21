package com.dripl.transaction.event.service;

import com.dripl.common.event.DomainEvent;
import com.dripl.transaction.event.dto.TransactionEventDto;
import com.dripl.transaction.event.entity.TransactionEvent;
import com.dripl.transaction.event.repository.TransactionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventService {

    private final TransactionEventRepository transactionEventRepository;

    private static final int MAX_RETRIES = 3;

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleDomainEvent(DomainEvent event) {
        if (!"transaction".equals(event.domain())) {
            return;
        }

        TransactionEvent entity = TransactionEvent.builder()
                .transactionId(event.entityId())
                .workspaceId(event.workspaceId())
                .eventType(event.eventType())
                .changes(event.changes())
                .performedBy(event.performedBy())
                .performedAt(event.performedAt())
                .build();

        int attempt = 0;
        while (true) {
            try {
                attempt++;
                transactionEventRepository.save(entity);
                log.debug("Persisted {} event for transaction {}", event.eventType(), event.entityId());
                return;
            } catch (Exception e) {
                if (attempt >= MAX_RETRIES) {
                    throw e;
                }
                log.warn("Retry {}/{} for {} event on transaction {}: {}",
                        attempt, MAX_RETRIES, event.eventType(), event.entityId(), e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionEventDto> getEventsForTransaction(UUID transactionId, UUID workspaceId) {
        return transactionEventRepository
                .findAllByTransactionIdAndWorkspaceIdOrderByPerformedAtDesc(transactionId, workspaceId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private TransactionEventDto toDto(TransactionEvent entity) {
        return TransactionEventDto.builder()
                .id(entity.getId())
                .transactionId(entity.getTransactionId())
                .eventType(entity.getEventType())
                .changes(entity.getChanges())
                .performedBy(entity.getPerformedBy())
                .performedAt(entity.getPerformedAt())
                .build();
    }
}
