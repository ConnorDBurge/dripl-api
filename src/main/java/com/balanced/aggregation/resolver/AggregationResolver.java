package com.balanced.aggregation.resolver;

import com.balanced.aggregation.dto.BankConnectionResponse;
import com.balanced.aggregation.dto.LinkBankInput;
import com.balanced.aggregation.dto.SyncResult;
import com.balanced.aggregation.entity.BankConnection;
import com.balanced.aggregation.mapper.BankConnectionMapper;
import com.balanced.aggregation.service.AggregationService;
import com.balanced.common.graphql.GraphQLContext;
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
public class AggregationResolver {

    private final AggregationService aggregationService;
    private final BankConnectionMapper bankConnectionMapper;

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public List<BankConnectionResponse> bankConnections() {
        UUID workspaceId = GraphQLContext.workspaceId();
        return bankConnectionMapper.toDtos(aggregationService.listConnections(workspaceId));
    }

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public BankConnectionResponse bankConnection(@Argument UUID bankConnectionId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return bankConnectionMapper.toDto(aggregationService.getConnection(bankConnectionId, workspaceId));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public BankConnectionResponse linkBank(@Argument @Valid LinkBankInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        BankConnection connection = aggregationService.linkBank(workspaceId, input.getAccessToken());
        return bankConnectionMapper.toDto(connection);
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @MutationMapping
    public boolean unlinkBank(@Argument UUID bankConnectionId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        aggregationService.unlinkBank(bankConnectionId, workspaceId);
        return true;
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public SyncResult syncTransactions(@Argument UUID bankConnectionId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return aggregationService.syncTransactions(bankConnectionId, workspaceId);
    }
}
