package com.dripl.transaction.event;

import com.dripl.common.event.DomainEvent;
import com.dripl.common.event.FieldChange;
import com.dripl.transaction.event.dto.TransactionEventDto;
import com.dripl.transaction.event.entity.TransactionEvent;
import com.dripl.transaction.event.repository.TransactionEventRepository;
import com.dripl.transaction.event.service.TransactionEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionEventServiceTest {

    @Mock
    private TransactionEventRepository transactionEventRepository;

    @InjectMocks
    private TransactionEventService transactionEventService;

    private final UUID transactionId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();

    @Test
    void handleDomainEvent_transactionDomain_persistsEvent() {
        DomainEvent event = new DomainEvent(
                "transaction", "created", transactionId, workspaceId,
                List.of(new FieldChange("amount", null, "-42.50")),
                "user@test.com", LocalDateTime.now()
        );

        transactionEventService.handleDomainEvent(event);

        ArgumentCaptor<TransactionEvent> captor = ArgumentCaptor.forClass(TransactionEvent.class);
        verify(transactionEventRepository).save(captor.capture());
        TransactionEvent saved = captor.getValue();
        assertThat(saved.getTransactionId()).isEqualTo(transactionId);
        assertThat(saved.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(saved.getEventType()).isEqualTo("transaction.created");
        assertThat(saved.getChanges()).hasSize(1);
        assertThat(saved.getChanges().get(0).field()).isEqualTo("amount");
        assertThat(saved.getPerformedBy()).isEqualTo("user@test.com");
    }

    @Test
    void handleDomainEvent_nonTransactionDomain_ignored() {
        DomainEvent event = new DomainEvent(
                "account", "updated", UUID.randomUUID(), workspaceId,
                List.of(), "user@test.com", LocalDateTime.now()
        );

        transactionEventService.handleDomainEvent(event);

        verify(transactionEventRepository, never()).save(any());
    }

    @Test
    void handleDomainEvent_persistenceFailure_retriesThenThrows() {
        DomainEvent event = new DomainEvent(
                "transaction", "updated", transactionId, workspaceId,
                List.of(new FieldChange("amount", "-10.00", "-20.00")),
                "user@test.com", LocalDateTime.now()
        );
        when(transactionEventRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> transactionEventService.handleDomainEvent(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");

        verify(transactionEventRepository, times(3)).save(any());
    }

    @Test
    void getEventsForTransaction_returnsOrderedDtos() {
        TransactionEvent event1 = TransactionEvent.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .workspaceId(workspaceId)
                .eventType("transaction.created")
                .changes(List.of(new FieldChange("amount", null, "-42.50")))
                .performedBy("user@test.com")
                .performedAt(LocalDateTime.now().minusHours(2))
                .build();

        TransactionEvent event2 = TransactionEvent.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .workspaceId(workspaceId)
                .eventType("transaction.updated")
                .changes(List.of(new FieldChange("amount", "-42.50", "-50.00")))
                .performedBy("user@test.com")
                .performedAt(LocalDateTime.now())
                .build();

        when(transactionEventRepository.findAllByTransactionIdAndWorkspaceIdOrderByPerformedAtDesc(transactionId, workspaceId))
                .thenReturn(List.of(event2, event1));

        List<TransactionEventDto> result = transactionEventService.getEventsForTransaction(transactionId, workspaceId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEventType()).isEqualTo("transaction.updated");
        assertThat(result.get(1).getEventType()).isEqualTo("transaction.created");
        assertThat(result.get(0).getChanges()).hasSize(1);
        assertThat(result.get(0).getChanges().get(0).field()).isEqualTo("amount");
        assertThat(result.get(0).getChanges().get(0).oldValue()).isEqualTo("-42.50");
        assertThat(result.get(0).getChanges().get(0).newValue()).isEqualTo("-50.00");
    }

    @Test
    void getEventsForTransaction_noEvents_returnsEmptyList() {
        when(transactionEventRepository.findAllByTransactionIdAndWorkspaceIdOrderByPerformedAtDesc(transactionId, workspaceId))
                .thenReturn(List.of());

        List<TransactionEventDto> result = transactionEventService.getEventsForTransaction(transactionId, workspaceId);

        assertThat(result).isEmpty();
    }

    @Test
    void handleDomainEvent_updatedWithMultipleChanges_persistsAll() {
        DomainEvent event = new DomainEvent(
                "transaction", "updated", transactionId, workspaceId,
                List.of(
                        new FieldChange("amount", "-10.00", "-20.00"),
                        new FieldChange("categoryId", "old-cat", "new-cat"),
                        new FieldChange("notes", null, "New note")
                ),
                "user@test.com", LocalDateTime.now()
        );

        transactionEventService.handleDomainEvent(event);

        ArgumentCaptor<TransactionEvent> captor = ArgumentCaptor.forClass(TransactionEvent.class);
        verify(transactionEventRepository).save(captor.capture());
        assertThat(captor.getValue().getChanges()).hasSize(3);
    }
}
