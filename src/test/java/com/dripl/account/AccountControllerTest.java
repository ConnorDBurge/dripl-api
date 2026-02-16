package com.dripl.account;

import com.dripl.account.controller.AccountController;
import com.dripl.account.dto.AccountDto;
import com.dripl.account.dto.CreateAccountDto;
import com.dripl.account.dto.UpdateAccountDto;
import com.dripl.account.entity.Account;
import com.dripl.account.enums.AccountSource;
import com.dripl.account.enums.AccountStatus;
import com.dripl.account.enums.AccountSubType;
import com.dripl.account.enums.AccountType;
import com.dripl.account.enums.CurrencyCode;
import com.dripl.account.mapper.AccountMapper;
import com.dripl.account.service.AccountService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @Spy
    private AccountMapper accountMapper = Mappers.getMapper(AccountMapper.class);

    @InjectMocks
    private AccountController accountController;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    private Account buildAccount(String name) {
        return Account.builder()
                .id(accountId)
                .workspaceId(workspaceId)
                .name(name)
                .type(AccountType.CASH)
                .subType(AccountSubType.CHECKING)
                .balance(BigDecimal.ZERO)
                .currency(CurrencyCode.USD)
                .source(AccountSource.MANUAL)
                .status(AccountStatus.ACTIVE)
                .excludeFromTransactions(false)
                .build();
    }

    @Test
    void listAccounts_returns200() {
        when(accountService.listAllByWorkspaceId(workspaceId)).thenReturn(List.of(buildAccount("Checking")));

        var response = accountController.listAccounts(workspaceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void listAccounts_empty_returns200() {
        when(accountService.listAllByWorkspaceId(workspaceId)).thenReturn(List.of());

        var response = accountController.listAccounts(workspaceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getAccount_returns200() {
        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount("Checking"));

        var response = accountController.getAccount(workspaceId, accountId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Checking");
    }

    @Test
    void createAccount_returns201() {
        CreateAccountDto dto = CreateAccountDto.builder()
                .name("Checking")
                .type(AccountType.CASH)
                .subType(AccountSubType.CHECKING)
                .build();
        when(accountService.createAccount(eq(workspaceId), any(CreateAccountDto.class)))
                .thenReturn(buildAccount("Checking"));

        var response = accountController.createAccount(workspaceId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getName()).isEqualTo("Checking");
    }

    @Test
    void updateAccount_returns200() {
        UpdateAccountDto dto = UpdateAccountDto.builder().name("Updated").build();
        Account updated = buildAccount("Updated");
        when(accountService.updateAccount(eq(accountId), eq(workspaceId), any(UpdateAccountDto.class)))
                .thenReturn(updated);

        var response = accountController.updateAccount(workspaceId, accountId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Updated");
    }

    @Test
    void deleteAccount_returns204() {
        var response = accountController.deleteAccount(workspaceId, accountId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(accountService).deleteAccount(accountId, workspaceId);
    }
}
