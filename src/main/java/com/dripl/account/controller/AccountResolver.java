package com.dripl.account.controller;

import com.dripl.account.dto.AccountDto;
import com.dripl.account.dto.CreateAccountDto;
import com.dripl.account.dto.UpdateAccountDto;
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
    public List<AccountDto> accounts() {
        UUID workspaceId = GraphQLContext.workspaceId();
        return accountMapper.toDtos(accountService.listAllByWorkspaceId(workspaceId));
    }

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public AccountDto account(@Argument UUID id) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return accountMapper.toDto(accountService.getAccount(id, workspaceId));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public AccountDto createAccount(@Argument @Valid CreateAccountDto input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return accountMapper.toDto(accountService.createAccount(workspaceId, input));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public AccountDto updateAccount(@Argument UUID id, @Argument @Valid UpdateAccountDto input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return accountMapper.toDto(accountService.updateAccount(id, workspaceId, input));
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @MutationMapping
    public boolean deleteAccount(@Argument UUID id) {
        UUID workspaceId = GraphQLContext.workspaceId();
        accountService.deleteAccount(id, workspaceId);
        return true;
    }
}
