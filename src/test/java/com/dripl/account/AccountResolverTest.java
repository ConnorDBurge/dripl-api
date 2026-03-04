package com.dripl.account;

import com.dripl.account.controller.AccountResolver;
import com.dripl.account.dto.AccountDto;
import com.dripl.account.dto.CreateAccountDto;
import com.dripl.account.dto.UpdateAccountDto;
import com.dripl.account.entity.Account;
import com.dripl.account.enums.AccountSource;
import com.dripl.account.enums.AccountSubType;
import com.dripl.account.enums.AccountType;
import com.dripl.account.enums.CurrencyCode;
import com.dripl.account.mapper.AccountMapper;
import com.dripl.account.service.AccountService;
import com.dripl.common.enums.Status;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountResolverTest {

    @Mock
    private AccountService accountService;

    @Spy
    private AccountMapper accountMapper = Mappers.getMapper(AccountMapper.class);

    @InjectMocks
    private AccountResolver accountResolver;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

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
                .status(Status.ACTIVE)
                .build();
    }

    @Test
    void accounts_returnsList() {
        when(accountService.listAllByWorkspaceId(workspaceId))
                .thenReturn(List.of(buildAccount("Checking")));

        List<AccountDto> result = accountResolver.accounts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Checking");
    }

    @Test
    void accounts_emptyList() {
        when(accountService.listAllByWorkspaceId(workspaceId)).thenReturn(List.of());

        List<AccountDto> result = accountResolver.accounts();

        assertThat(result).isEmpty();
    }

    @Test
    void account_returnsById() {
        when(accountService.getAccount(accountId, workspaceId))
                .thenReturn(buildAccount("Checking"));

        AccountDto result = accountResolver.account(accountId);

        assertThat(result.getName()).isEqualTo("Checking");
    }

    @Test
    void createAccount_delegatesToService() {
        CreateAccountDto dto = CreateAccountDto.builder()
                .name("Savings")
                .type(AccountType.CASH)
                .subType(AccountSubType.SAVINGS)
                .build();
        when(accountService.createAccount(eq(workspaceId), any(CreateAccountDto.class)))
                .thenReturn(buildAccount("Savings"));

        AccountDto result = accountResolver.createAccount(dto);

        assertThat(result.getName()).isEqualTo("Savings");
    }

    @Test
    void updateAccount_delegatesToService() {
        UpdateAccountDto dto = UpdateAccountDto.builder().name("Updated").build();
        Account updated = buildAccount("Updated");
        when(accountService.updateAccount(eq(accountId), eq(workspaceId), any(UpdateAccountDto.class)))
                .thenReturn(updated);

        AccountDto result = accountResolver.updateAccount(accountId, dto);

        assertThat(result.getName()).isEqualTo("Updated");
    }

    @Test
    void deleteAccount_delegatesToService() {
        boolean result = accountResolver.deleteAccount(accountId);

        assertThat(result).isTrue();
        verify(accountService).deleteAccount(accountId, workspaceId);
    }
}
