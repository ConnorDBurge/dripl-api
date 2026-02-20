package com.dripl.transaction.split;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.split.controller.TransactionSplitController;
import com.dripl.transaction.split.dto.CreateTransactionSplitDto;
import com.dripl.transaction.split.dto.SplitChildDto;
import com.dripl.transaction.split.dto.UpdateTransactionSplitDto;
import com.dripl.transaction.split.dto.UpdateSplitChildDto;
import com.dripl.transaction.split.entity.TransactionSplit;
import com.dripl.transaction.split.mapper.TransactionSplitMapper;
import com.dripl.transaction.split.service.TransactionSplitService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionSplitControllerTest {

    @Mock private TransactionSplitService transactionSplitService;
    @Spy private TransactionSplitMapper transactionSplitMapper = Mappers.getMapper(TransactionSplitMapper.class);

    @InjectMocks
    private TransactionSplitController controller;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID splitId = UUID.randomUUID();

    private TransactionSplit buildSplit() {
        return TransactionSplit.builder()
                .id(splitId)
                .workspaceId(workspaceId)
                .accountId(UUID.randomUUID())
                .totalAmount(new BigDecimal("100.00"))
                .currencyCode(CurrencyCode.USD)
                .date(LocalDateTime.of(2025, 7, 14, 0, 0))
                .build();
    }

    private List<Transaction> buildTransactions() {
        return List.of(
                Transaction.builder().id(UUID.randomUUID()).amount(new BigDecimal("60.00")).build(),
                Transaction.builder().id(UUID.randomUUID()).amount(new BigDecimal("40.00")).build()
        );
    }

    @Test
    void listTransactionSplits_returns200() {
        when(transactionSplitService.listAllByWorkspaceId(workspaceId)).thenReturn(List.of(buildSplit()));
        when(transactionSplitService.getSplitTransactions(splitId, workspaceId)).thenReturn(buildTransactions());

        var response = controller.listTransactionSplits(workspaceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getTransactionIds()).hasSize(2);
    }

    @Test
    void getTransactionSplit_returns200() {
        when(transactionSplitService.getTransactionSplit(splitId, workspaceId)).thenReturn(buildSplit());
        when(transactionSplitService.getSplitTransactions(splitId, workspaceId)).thenReturn(buildTransactions());

        var response = controller.getTransactionSplit(workspaceId, splitId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTotalAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void createTransactionSplit_returns201() {
        CreateTransactionSplitDto dto = CreateTransactionSplitDto.builder()
                .transactionId(UUID.randomUUID())
                .children(List.of(
                        SplitChildDto.builder().amount(new BigDecimal("60.00")).build(),
                        SplitChildDto.builder().amount(new BigDecimal("40.00")).build()))
                .build();

        when(transactionSplitService.createTransactionSplit(eq(workspaceId), any())).thenReturn(buildSplit());
        when(transactionSplitService.getSplitTransactions(splitId, workspaceId)).thenReturn(buildTransactions());

        var response = controller.createTransactionSplit(workspaceId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void updateTransactionSplit_returns200() {
        UpdateTransactionSplitDto dto = UpdateTransactionSplitDto.builder()
                .children(List.of(
                        UpdateSplitChildDto.builder().id(UUID.randomUUID()).amount(new BigDecimal("50.00")).build(),
                        UpdateSplitChildDto.builder().id(UUID.randomUUID()).amount(new BigDecimal("50.00")).build()))
                .build();

        when(transactionSplitService.updateTransactionSplit(eq(splitId), eq(workspaceId), any())).thenReturn(buildSplit());
        when(transactionSplitService.getSplitTransactions(splitId, workspaceId)).thenReturn(buildTransactions());

        var response = controller.updateTransactionSplit(workspaceId, splitId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteTransactionSplit_returns204() {
        var response = controller.deleteTransactionSplit(workspaceId, splitId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(transactionSplitService).deleteTransactionSplit(splitId, workspaceId);
    }
}
