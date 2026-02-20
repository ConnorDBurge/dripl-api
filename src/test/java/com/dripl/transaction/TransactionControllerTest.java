package com.dripl.transaction;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.transaction.controller.TransactionController;
import com.dripl.transaction.dto.CreateTransactionDto;
import com.dripl.transaction.dto.UpdateTransactionDto;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.enums.TransactionSource;
import com.dripl.transaction.enums.TransactionStatus;
import com.dripl.transaction.mapper.TransactionMapper;
import com.dripl.transaction.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionService transactionService;

    @Spy
    private TransactionMapper transactionMapper = Mappers.getMapper(TransactionMapper.class);

    @InjectMocks
    private TransactionController transactionController;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();

    private Transaction buildTransaction() {
        return Transaction.builder()
                .id(transactionId)
                .workspaceId(workspaceId)
                .accountId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .categoryId(UUID.randomUUID())
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .amount(new BigDecimal("-42.50"))
                .currencyCode(CurrencyCode.USD)
                .status(TransactionStatus.PENDING)
                .source(TransactionSource.MANUAL)
                .pendingAt(LocalDateTime.now())
                .tagIds(Set.of())
                .build();
    }

    @Test
    void listTransactions_returns200() {
        when(transactionService.listAll(any())).thenReturn(List.of(buildTransaction()));

        var response = transactionController.listTransactions(workspaceId, null, null, null, null, null, null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void listTransactions_empty_returns200() {
        when(transactionService.listAll(any())).thenReturn(List.of());

        var response = transactionController.listTransactions(workspaceId, null, null, null, null, null, null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getTransaction_returns200() {
        when(transactionService.getTransaction(transactionId, workspaceId)).thenReturn(buildTransaction());

        var response = transactionController.getTransaction(workspaceId, transactionId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getAmount()).isEqualByComparingTo(new BigDecimal("-42.50"));
    }

    @Test
    void createTransaction_returns201() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .accountId(UUID.randomUUID())
                .merchantName("Kroger")
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .amount(new BigDecimal("-55.00"))
                .build();
        when(transactionService.createTransaction(eq(workspaceId), any(CreateTransactionDto.class)))
                .thenReturn(buildTransaction());

        var response = transactionController.createTransaction(workspaceId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void updateTransaction_returns200() {
        UpdateTransactionDto dto = UpdateTransactionDto.builder().amount(new BigDecimal("-99.99")).build();
        when(transactionService.updateTransaction(eq(transactionId), eq(workspaceId), any(UpdateTransactionDto.class)))
                .thenReturn(buildTransaction());

        var response = transactionController.updateTransaction(workspaceId, transactionId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void deleteTransaction_returns204() {
        var response = transactionController.deleteTransaction(workspaceId, transactionId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(transactionService).deleteTransaction(transactionId, workspaceId);
    }
}
