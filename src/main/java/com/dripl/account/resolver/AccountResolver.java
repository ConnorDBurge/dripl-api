package com.dripl.account.resolver;

import com.dripl.account.dto.AccountResponse;
import com.dripl.account.dto.CreateAccountInput;
import com.dripl.account.dto.UpdateAccountInput;
import com.dripl.account.mapper.AccountMapper;
import com.dripl.account.service.AccountService;
import com.dripl.common.graphql.GraphQLContext;
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
public class AccountResolver {

    private final AccountService accountService;
    private final AccountMapper accountMapper;

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public List<AccountResponse> accounts() {
        UUID workspaceId = GraphQLContext.workspaceId();
        return accountMapper.toDtos(accountService.listAllByWorkspaceId(workspaceId));
    }

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public AccountResponse account(@Argument UUID accountId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return accountMapper.toDto(accountService.getAccount(accountId, workspaceId));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public AccountResponse createAccount(@Argument @Valid CreateAccountInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return accountMapper.toDto(accountService.createAccount(workspaceId, input));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public AccountResponse updateAccount(@Argument UUID accountId, @Argument @Valid UpdateAccountInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return accountMapper.toDto(accountService.updateAccount(accountId, workspaceId, input));
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @MutationMapping
    public boolean deleteAccount(@Argument UUID accountId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        accountService.deleteAccount(accountId, workspaceId);
        return true;
    }
}
