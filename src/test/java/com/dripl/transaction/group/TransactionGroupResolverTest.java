package com.dripl.transaction.group;

import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.group.dto.CreateTransactionGroupInput;
import com.dripl.transaction.group.dto.TransactionGroupResponse;
import com.dripl.transaction.group.dto.UpdateTransactionGroupInput;
import com.dripl.transaction.group.entity.TransactionGroup;
import com.dripl.transaction.group.mapper.TransactionGroupMapper;
import com.dripl.transaction.group.resolver.TransactionGroupResolver;
import com.dripl.transaction.group.service.TransactionGroupService;
import graphql.schema.DataFetchingEnvironment;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionGroupResolverTest {

    @Mock
    private TransactionGroupService transactionGroupService;

    @Mock
    private TransactionGroupMapper transactionGroupMapper;

    @InjectMocks
    private TransactionGroupResolver transactionGroupResolver;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();

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

    private TransactionGroup buildGroup(String name) {
        return TransactionGroup.builder()
                .id(groupId)
                .workspaceId(workspaceId)
                .name(name)
                .build();
    }

    private TransactionGroupResponse buildResponse(String name) {
        return TransactionGroupResponse.builder()
                .id(groupId)
                .workspaceId(workspaceId)
                .name(name)
                .build();
    }

    @Test
    void transactionGroups_returnsList() {
        TransactionGroup group = buildGroup("Monthly Bills");
        TransactionGroupResponse dto = buildResponse("Monthly Bills");

        when(transactionGroupService.listAllByWorkspaceId(workspaceId)).thenReturn(List.of(group));
        when(transactionGroupMapper.toDtos(eq(List.of(group)), any(), eq(workspaceId))).thenReturn(List.of(dto));

        List<TransactionGroupResponse> result = transactionGroupResolver.transactionGroups();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Monthly Bills");
    }

    @Test
    void transactionGroup_returnsById() {
        TransactionGroup group = buildGroup("Monthly Bills");
        List<Transaction> transactions = List.of();
        TransactionGroupResponse dto = buildResponse("Monthly Bills");

        when(transactionGroupService.getTransactionGroup(groupId, workspaceId)).thenReturn(group);
        when(transactionGroupService.getGroupTransactions(groupId, workspaceId)).thenReturn(transactions);
        when(transactionGroupMapper.toDto(group, transactions)).thenReturn(dto);

        TransactionGroupResponse result = transactionGroupResolver.transactionGroup(groupId);

        assertThat(result.getName()).isEqualTo("Monthly Bills");
    }

    @Test
    void createTransactionGroup_delegatesToService() {
        CreateTransactionGroupInput input = CreateTransactionGroupInput.builder()
                .name("Utilities")
                .transactionIds(Set.of(UUID.randomUUID(), UUID.randomUUID()))
                .build();
        TransactionGroup group = buildGroup("Utilities");
        List<Transaction> transactions = List.of();
        TransactionGroupResponse dto = buildResponse("Utilities");

        when(transactionGroupService.createTransactionGroup(eq(workspaceId), any(CreateTransactionGroupInput.class)))
                .thenReturn(group);
        when(transactionGroupService.getGroupTransactions(groupId, workspaceId)).thenReturn(transactions);
        when(transactionGroupMapper.toDto(group, transactions)).thenReturn(dto);

        TransactionGroupResponse result = transactionGroupResolver.createTransactionGroup(input);

        assertThat(result.getName()).isEqualTo("Utilities");
    }

    @Test
    void updateTransactionGroup_delegatesToService() {
        UpdateTransactionGroupInput input = UpdateTransactionGroupInput.builder()
                .name("Updated")
                .build();
        TransactionGroup group = buildGroup("Updated");
        List<Transaction> transactions = List.of();
        TransactionGroupResponse dto = buildResponse("Updated");

        when(transactionGroupService.updateTransactionGroup(eq(groupId), eq(workspaceId), any(UpdateTransactionGroupInput.class)))
                .thenReturn(group);
        when(transactionGroupService.getGroupTransactions(groupId, workspaceId)).thenReturn(transactions);
        when(transactionGroupMapper.toDto(group, transactions)).thenReturn(dto);

        DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
        when(env.getArgument("input")).thenReturn(null);

        TransactionGroupResponse result = transactionGroupResolver.updateTransactionGroup(groupId, input, env);

        assertThat(result.getName()).isEqualTo("Updated");
    }

    @Test
    void deleteTransactionGroup_delegatesToService() {
        boolean result = transactionGroupResolver.deleteTransactionGroup(groupId);

        assertThat(result).isTrue();
        verify(transactionGroupService).deleteTransactionGroup(groupId, workspaceId);
    }
}
