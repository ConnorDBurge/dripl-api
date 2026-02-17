package com.dripl.account.controller;

import com.dripl.account.dto.AccountDto;
import com.dripl.account.dto.CreateAccountDto;
import com.dripl.account.dto.UpdateAccountDto;
import com.dripl.account.entity.Account;
import com.dripl.account.mapper.AccountMapper;
import com.dripl.account.service.AccountService;
import com.dripl.common.annotation.WorkspaceId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    private final AccountMapper accountMapper;

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<AccountDto>> listAccounts(@WorkspaceId UUID workspaceId) {
        List<Account> accounts = accountService.listAllByWorkspaceId(workspaceId);
        return ResponseEntity.ok(accountMapper.toDtos(accounts));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountDto> createAccount(
            @WorkspaceId UUID workspaceId, @Valid @RequestBody CreateAccountDto dto) {
        Account account = accountService.createAccount(workspaceId, dto);
        return ResponseEntity.status(201).body(accountMapper.toDto(account));
    }

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/{accountId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountDto> getAccount(
            @WorkspaceId UUID workspaceId, @PathVariable UUID accountId) {
        Account account = accountService.getAccount(accountId, workspaceId);
        return ResponseEntity.ok(accountMapper.toDto(account));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(value = "/{accountId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountDto> updateAccount(
            @WorkspaceId UUID workspaceId, @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountDto dto) {
        Account account = accountService.updateAccount(accountId, workspaceId, dto);
        return ResponseEntity.ok(accountMapper.toDto(account));
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> deleteAccount(
            @WorkspaceId UUID workspaceId, @PathVariable UUID accountId) {
        accountService.deleteAccount(accountId, workspaceId);
        return ResponseEntity.noContent().build();
    }
}
