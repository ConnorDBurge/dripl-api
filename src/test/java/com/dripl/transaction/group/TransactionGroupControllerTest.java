package com.dripl.transaction.group;

import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.group.controller.TransactionGroupController;
import com.dripl.transaction.group.dto.CreateTransactionGroupDto;
import com.dripl.transaction.group.dto.UpdateTransactionGroupDto;
import com.dripl.transaction.group.entity.TransactionGroup;
import com.dripl.transaction.group.mapper.TransactionGroupMapper;
import com.dripl.transaction.group.service.TransactionGroupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionGroupControllerTest {

    @Mock private TransactionGroupService transactionGroupService;
    @Spy private TransactionGroupMapper transactionGroupMapper = Mappers.getMapper(TransactionGroupMapper.class);

    @InjectMocks
    private TransactionGroupController controller;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();

    private TransactionGroup buildGroup() {
        return TransactionGroup.builder()
                .id(groupId)
                .workspaceId(workspaceId)
                .name("Beach Vacation")
                .build();
    }

    private List<Transaction> buildTransactions() {
        return List.of(
                Transaction.builder().id(UUID.randomUUID()).amount(new BigDecimal("-50.00")).build(),
                Transaction.builder().id(UUID.randomUUID()).amount(new BigDecimal("-50.00")).build()
        );
    }

    @Test
    void listTransactionGroups_returns200() {
        when(transactionGroupService.listAllByWorkspaceId(workspaceId)).thenReturn(List.of(buildGroup()));
        when(transactionGroupService.getGroupTransactions(groupId, workspaceId)).thenReturn(buildTransactions());

        var response = controller.listTransactionGroups(workspaceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getTotalAmount()).isEqualByComparingTo(new BigDecimal("-100.00"));
        assertThat(response.getBody().get(0).getTransactionIds()).hasSize(2);
    }

    @Test
    void getTransactionGroup_returns200() {
        when(transactionGroupService.getTransactionGroup(groupId, workspaceId)).thenReturn(buildGroup());
        when(transactionGroupService.getGroupTransactions(groupId, workspaceId)).thenReturn(buildTransactions());

        var response = controller.getTransactionGroup(workspaceId, groupId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Beach Vacation");
    }

    @Test
    void createTransactionGroup_returns201() {
        CreateTransactionGroupDto dto = CreateTransactionGroupDto.builder()
                .name("Trip")
                .transactionIds(Set.of(UUID.randomUUID(), UUID.randomUUID()))
                .build();

        when(transactionGroupService.createTransactionGroup(eq(workspaceId), any())).thenReturn(buildGroup());
        when(transactionGroupService.getGroupTransactions(groupId, workspaceId)).thenReturn(buildTransactions());

        var response = controller.createTransactionGroup(workspaceId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void updateTransactionGroup_returns200() {
        UpdateTransactionGroupDto dto = UpdateTransactionGroupDto.builder().name("Updated").build();

        when(transactionGroupService.updateTransactionGroup(eq(groupId), eq(workspaceId), any())).thenReturn(buildGroup());
        when(transactionGroupService.getGroupTransactions(groupId, workspaceId)).thenReturn(buildTransactions());

        var response = controller.updateTransactionGroup(workspaceId, groupId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteTransactionGroup_returns204() {
        var response = controller.deleteTransactionGroup(workspaceId, groupId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(transactionGroupService).deleteTransactionGroup(groupId, workspaceId);
    }
}
