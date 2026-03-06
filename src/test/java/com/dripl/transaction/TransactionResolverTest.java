package com.dripl.transaction;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.dto.PagedResponse;
import com.dripl.transaction.dto.CreateTransactionInput;
import com.dripl.transaction.dto.TransactionFilter;
import com.dripl.transaction.dto.TransactionResponse;
import com.dripl.transaction.dto.TransactionSort;
import com.dripl.transaction.dto.UpdateTransactionInput;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.enums.TransactionSource;
import com.dripl.transaction.enums.TransactionStatus;
import com.dripl.transaction.event.dto.TransactionEventResponse;
import com.dripl.transaction.event.service.TransactionEventService;
import com.dripl.transaction.mapper.TransactionMapper;
import com.dripl.transaction.resolver.TransactionResolver;
import com.dripl.transaction.service.TransactionService;
import graphql.schema.DataFetchingEnvironment;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionResolverTest {

    @Mock
    private TransactionService transactionService;

    @Mock
    private TransactionEventService transactionEventService;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionResolver transactionResolver;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();

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

    private Transaction buildTransaction() {
        return Transaction.builder()
                .id(transactionId)
                .workspaceId(workspaceId)
                .accountId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("25.00"))
                .currencyCode(CurrencyCode.USD)
                .date(LocalDateTime.now())
                .status(TransactionStatus.PENDING)
                .source(TransactionSource.MANUAL)
                .build();
    }

    private TransactionResponse buildResponse() {
        return TransactionResponse.builder()
                .id(transactionId)
                .workspaceId(workspaceId)
                .amount(new BigDecimal("25.00"))
                .currencyCode(CurrencyCode.USD)
                .status(TransactionStatus.PENDING)
                .source(TransactionSource.MANUAL)
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void transactions_returnsPagedResults() {
        Transaction txn = buildTransaction();
        TransactionResponse dto = buildResponse();
        Page<Transaction> page = new PageImpl<>(List.of(txn));

        when(transactionService.listAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(transactionMapper.toDto(txn)).thenReturn(dto);

        PagedResponse<TransactionResponse> result = transactionResolver.transactions(null, null, 0, 25);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    void transaction_returnsById() {
        Transaction txn = buildTransaction();
        TransactionResponse dto = buildResponse();

        when(transactionService.getTransaction(transactionId, workspaceId)).thenReturn(txn);
        when(transactionMapper.toDto(txn)).thenReturn(dto);

        TransactionResponse result = transactionResolver.transaction(transactionId);

        assertThat(result.getId()).isEqualTo(transactionId);
    }

    @Test
    void transactionEvents_returnsEventsForTransaction() {
        Transaction txn = buildTransaction();
        TransactionEventResponse event = TransactionEventResponse.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .eventType("transaction.created")
                .build();

        when(transactionService.getTransaction(transactionId, workspaceId)).thenReturn(txn);
        when(transactionEventService.getEventsForTransaction(transactionId, workspaceId))
                .thenReturn(List.of(event));

        List<TransactionEventResponse> result = transactionResolver.transactionEvents(transactionId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo("transaction.created");
    }

    @Test
    void createTransaction_delegatesToService() {
        CreateTransactionInput input = CreateTransactionInput.builder()
                .merchantName("Amazon")
                .amount(new BigDecimal("25.00"))
                .build();
        Transaction txn = buildTransaction();
        TransactionResponse dto = buildResponse();

        when(transactionService.createTransaction(eq(workspaceId), any(CreateTransactionInput.class)))
                .thenReturn(txn);
        when(transactionMapper.toDto(txn)).thenReturn(dto);

        TransactionResponse result = transactionResolver.createTransaction(input);

        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    void updateTransaction_delegatesToService() {
        UpdateTransactionInput input = UpdateTransactionInput.builder()
                .amount(new BigDecimal("50.00"))
                .build();
        Transaction txn = buildTransaction();
        TransactionResponse dto = buildResponse();

        when(transactionService.updateTransaction(eq(transactionId), eq(workspaceId), any(UpdateTransactionInput.class)))
                .thenReturn(txn);
        when(transactionMapper.toDto(txn)).thenReturn(dto);

        DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
        when(env.getArgument("input")).thenReturn(null);

        TransactionResponse result = transactionResolver.updateTransaction(transactionId, input, env);

        assertThat(result).isNotNull();
    }

    @Test
    void deleteTransaction_delegatesToService() {
        boolean result = transactionResolver.deleteTransaction(transactionId);

        assertThat(result).isTrue();
        verify(transactionService).deleteTransaction(transactionId, workspaceId);
    }
}
