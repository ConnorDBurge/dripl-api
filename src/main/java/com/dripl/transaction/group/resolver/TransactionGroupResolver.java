package com.dripl.transaction.group.resolver;

import com.dripl.common.graphql.GraphQLContext;
import com.dripl.transaction.group.dto.CreateTransactionGroupInput;
import com.dripl.transaction.group.dto.TransactionGroupResponse;
import com.dripl.transaction.group.dto.UpdateTransactionGroupInput;
import com.dripl.transaction.group.entity.TransactionGroup;
import com.dripl.transaction.group.mapper.TransactionGroupMapper;
import com.dripl.transaction.group.service.TransactionGroupService;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class TransactionGroupResolver {

    private final TransactionGroupService transactionGroupService;
    private final TransactionGroupMapper transactionGroupMapper;

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public List<TransactionGroupResponse> transactionGroups() {
        UUID workspaceId = GraphQLContext.workspaceId();
        return transactionGroupMapper.toDtos(
                transactionGroupService.listAllByWorkspaceId(workspaceId),
                transactionGroupService::getGroupTransactions,
                workspaceId);
    }

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public TransactionGroupResponse transactionGroup(@Argument UUID transactionGroupId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        TransactionGroup group = transactionGroupService.getTransactionGroup(transactionGroupId, workspaceId);
        return transactionGroupMapper.toDto(
                group, transactionGroupService.getGroupTransactions(transactionGroupId, workspaceId));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public TransactionGroupResponse createTransactionGroup(@Argument @Valid CreateTransactionGroupInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        TransactionGroup group = transactionGroupService.createTransactionGroup(workspaceId, input);
        return transactionGroupMapper.toDto(
                group, transactionGroupService.getGroupTransactions(group.getId(), workspaceId));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    @SuppressWarnings("unchecked")
    public TransactionGroupResponse updateTransactionGroup(
            @Argument UUID transactionGroupId, @Argument @Valid UpdateTransactionGroupInput input,
            DataFetchingEnvironment env) {
        UUID workspaceId = GraphQLContext.workspaceId();

        Map<String, Object> rawInput = env.getArgument("input");
        if (rawInput != null) {
            if (rawInput.containsKey("categoryId") && !input.isCategoryIdSpecified()) {
                input.setCategoryId(null);
            }
            if (rawInput.containsKey("notes") && !input.isNotesSpecified()) {
                input.setNotes(null);
            }
            if (rawInput.containsKey("tagIds") && !input.isTagIdsSpecified()) {
                input.setTagIds(null);
            }
        }

        TransactionGroup group = transactionGroupService.updateTransactionGroup(transactionGroupId, workspaceId, input);
        return transactionGroupMapper.toDto(
                group, transactionGroupService.getGroupTransactions(group.getId(), workspaceId));
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @MutationMapping
    public boolean deleteTransactionGroup(@Argument UUID transactionGroupId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        transactionGroupService.deleteTransactionGroup(transactionGroupId, workspaceId);
        return true;
    }
}
