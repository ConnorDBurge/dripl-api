package com.dripl.transaction.split.resolver;

import com.dripl.common.graphql.GraphQLContext;
import com.dripl.transaction.split.dto.CreateTransactionSplitInput;
import com.dripl.transaction.split.dto.TransactionSplitResponse;
import com.dripl.transaction.split.dto.UpdateTransactionSplitInput;
import com.dripl.transaction.split.entity.TransactionSplit;
import com.dripl.transaction.split.mapper.TransactionSplitMapper;
import com.dripl.transaction.split.service.TransactionSplitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class TransactionSplitResolver {

    private final TransactionSplitService transactionSplitService;
    private final TransactionSplitMapper transactionSplitMapper;

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public List<TransactionSplitResponse> transactionSplits() {
        UUID workspaceId = GraphQLContext.workspaceId();
        return transactionSplitService.listAllByWorkspaceId(workspaceId).stream()
                .map(split -> transactionSplitMapper.toDto(
                        split,
                        transactionSplitService.getSplitTransactions(split.getId(), workspaceId)))
                .toList();
    }

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public TransactionSplitResponse transactionSplit(@Argument UUID transactionSplitId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        TransactionSplit split = transactionSplitService.getTransactionSplit(transactionSplitId, workspaceId);
        return transactionSplitMapper.toDto(
                split, transactionSplitService.getSplitTransactions(transactionSplitId, workspaceId));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public TransactionSplitResponse createTransactionSplit(@Argument @Valid CreateTransactionSplitInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        TransactionSplit split = transactionSplitService.createTransactionSplit(workspaceId, input);
        return transactionSplitMapper.toDto(
                split, transactionSplitService.getSplitTransactions(split.getId(), workspaceId));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public TransactionSplitResponse updateTransactionSplit(
            @Argument UUID transactionSplitId, @Argument @Valid UpdateTransactionSplitInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        TransactionSplit split = transactionSplitService.updateTransactionSplit(transactionSplitId, workspaceId, input);
        return transactionSplitMapper.toDto(
                split, transactionSplitService.getSplitTransactions(split.getId(), workspaceId));
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @MutationMapping
    public boolean deleteTransactionSplit(@Argument UUID transactionSplitId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        transactionSplitService.deleteTransactionSplit(transactionSplitId, workspaceId);
        return true;
    }
}
