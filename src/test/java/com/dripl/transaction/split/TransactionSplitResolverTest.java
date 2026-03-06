package com.dripl.transaction.split;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.split.dto.CreateTransactionSplitInput;
import com.dripl.transaction.split.dto.TransactionSplitResponse;
import com.dripl.transaction.split.dto.UpdateTransactionSplitInput;
import com.dripl.transaction.split.entity.TransactionSplit;
import com.dripl.transaction.split.mapper.TransactionSplitMapper;
import com.dripl.transaction.split.resolver.TransactionSplitResolver;
import com.dripl.transaction.split.service.TransactionSplitService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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
class TransactionSplitResolverTest {

    @Mock
    private TransactionSplitService transactionSplitService;

    @Mock
    private TransactionSplitMapper transactionSplitMapper;

    @InjectMocks
    private TransactionSplitResolver transactionSplitResolver;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID splitId = UUID.randomUUID();

    @BeforeEach
    void setUpSecurityContext() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.get("workspace_id", String.class)).thenReturn(workspaceId.toString());
        var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private TransactionSplit buildSplit() {
        return TransactionSplit.builder()
                .id(splitId)
                .workspaceId(workspaceId)
                .accountId(UUID.randomUUID())
                .totalAmount(new BigDecimal("100.00"))
                .currencyCode(CurrencyCode.USD)
                .date(LocalDateTime.now())
                .build();
    }

    private TransactionSplitResponse buildResponse() {
        return TransactionSplitResponse.builder()
                .id(splitId)
                .workspaceId(workspaceId)
                .totalAmount(new BigDecimal("100.00"))
                .currencyCode(CurrencyCode.USD)
                .build();
    }

    @Test
    void transactionSplits_returnsList() {
        TransactionSplit split = buildSplit();
        List<Transaction> transactions = List.of();
        TransactionSplitResponse dto = buildResponse();

        when(transactionSplitService.listAllByWorkspaceId(workspaceId)).thenReturn(List.of(split));
        when(transactionSplitService.getSplitTransactions(splitId, workspaceId)).thenReturn(transactions);
        when(transactionSplitMapper.toDto(split, transactions)).thenReturn(dto);

        List<TransactionSplitResponse> result = transactionSplitResolver.transactionSplits();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void transactionSplit_returnsById() {
        TransactionSplit split = buildSplit();
        List<Transaction> transactions = List.of();
        TransactionSplitResponse dto = buildResponse();

        when(transactionSplitService.getTransactionSplit(splitId, workspaceId)).thenReturn(split);
        when(transactionSplitService.getSplitTransactions(splitId, workspaceId)).thenReturn(transactions);
        when(transactionSplitMapper.toDto(split, transactions)).thenReturn(dto);

        TransactionSplitResponse result = transactionSplitResolver.transactionSplit(splitId);

        assertThat(result.getId()).isEqualTo(splitId);
    }

    @Test
    void createTransactionSplit_delegatesToService() {
        CreateTransactionSplitInput input = CreateTransactionSplitInput.builder()
                .transactionId(UUID.randomUUID())
                .build();
        TransactionSplit split = buildSplit();
        List<Transaction> transactions = List.of();
        TransactionSplitResponse dto = buildResponse();

        when(transactionSplitService.createTransactionSplit(eq(workspaceId), any(CreateTransactionSplitInput.class)))
                .thenReturn(split);
        when(transactionSplitService.getSplitTransactions(splitId, workspaceId)).thenReturn(transactions);
        when(transactionSplitMapper.toDto(split, transactions)).thenReturn(dto);

        TransactionSplitResponse result = transactionSplitResolver.createTransactionSplit(input);

        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void updateTransactionSplit_delegatesToService() {
        UpdateTransactionSplitInput input = UpdateTransactionSplitInput.builder().build();
        TransactionSplit split = buildSplit();
        List<Transaction> transactions = List.of();
        TransactionSplitResponse dto = buildResponse();

        when(transactionSplitService.updateTransactionSplit(eq(splitId), eq(workspaceId), any(UpdateTransactionSplitInput.class)))
                .thenReturn(split);
        when(transactionSplitService.getSplitTransactions(splitId, workspaceId)).thenReturn(transactions);
        when(transactionSplitMapper.toDto(split, transactions)).thenReturn(dto);

        TransactionSplitResponse result = transactionSplitResolver.updateTransactionSplit(splitId, input);

        assertThat(result).isNotNull();
    }

    @Test
    void deleteTransactionSplit_delegatesToService() {
        boolean result = transactionSplitResolver.deleteTransactionSplit(splitId);

        assertThat(result).isTrue();
        verify(transactionSplitService).deleteTransactionSplit(splitId, workspaceId);
    }
}
