package com.dripl.account;

import com.dripl.account.dto.CreateAccountDto;
import com.dripl.account.dto.UpdateAccountDto;
import com.dripl.account.entity.Account;
import com.dripl.account.enums.AccountSource;
import com.dripl.common.enums.Status;
import com.dripl.account.enums.AccountSubType;
import com.dripl.account.enums.AccountType;
import com.dripl.account.enums.CurrencyCode;
import com.dripl.account.mapper.AccountMapper;
import com.dripl.account.repository.AccountRepository;
import com.dripl.account.service.AccountService;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ConflictException;
import com.dripl.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Spy
    private AccountMapper accountMapper = Mappers.getMapper(AccountMapper.class);

    @InjectMocks
    private AccountService accountService;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    private Account buildAccount(String name, AccountType type, AccountSubType subType) {
        return Account.builder()
                .id(accountId)
                .workspaceId(workspaceId)
                .name(name)
                .type(type)
                .subType(subType)
                .balance(BigDecimal.ZERO)
                .currency(CurrencyCode.USD)
                .source(AccountSource.MANUAL)
                .status(Status.ACTIVE)
                .build();
    }

    // --- listAllByWorkspaceId ---

    @Test
    void listAllByWorkspaceId_returnsAccounts() {
        Account account = buildAccount("Checking", AccountType.CASH, AccountSubType.CHECKING);
        when(accountRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(account));

        List<Account> result = accountService.listAllByWorkspaceId(workspaceId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Checking");
    }

    @Test
    void listAllByWorkspaceId_emptyList() {
        when(accountRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of());

        List<Account> result = accountService.listAllByWorkspaceId(workspaceId);

        assertThat(result).isEmpty();
    }

    // --- getAccount ---

    @Test
    void getAccount_found() {
        Account account = buildAccount("Checking", AccountType.CASH, AccountSubType.CHECKING);
        when(accountRepository.findByIdAndWorkspaceId(accountId, workspaceId)).thenReturn(Optional.of(account));

        Account result = accountService.getAccount(accountId, workspaceId);

        assertThat(result.getName()).isEqualTo("Checking");
    }

    @Test
    void getAccount_notFound_throws() {
        when(accountRepository.findByIdAndWorkspaceId(accountId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(accountId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Account not found");
    }

    // --- createAccount ---

    @Test
    void createAccount_success() {
        CreateAccountDto dto = CreateAccountDto.builder()
                .name("Chase Checking")
                .type(AccountType.CASH)
                .subType(AccountSubType.CHECKING)
                .build();

        when(accountRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, "Chase Checking")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account result = accountService.createAccount(workspaceId, dto);

        assertThat(result.getName()).isEqualTo("Chase Checking");
        assertThat(result.getType()).isEqualTo(AccountType.CASH);
        assertThat(result.getSubType()).isEqualTo(AccountSubType.CHECKING);
        assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getCurrency()).isEqualTo(CurrencyCode.USD);
        assertThat(result.getSource()).isEqualTo(AccountSource.MANUAL);
    }

    @Test
    void createAccount_withAllFields() {
        CreateAccountDto dto = CreateAccountDto.builder()
                .name("Amex Gold")
                .type(AccountType.CREDIT)
                .subType(AccountSubType.CREDIT_CARD)
                .startingBalance(new BigDecimal("-1500.00"))
                .currency(CurrencyCode.EUR)
                .institutionName("American Express")
                .source(AccountSource.AUTOMATIC)
                .build();

        when(accountRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, "Amex Gold")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account result = accountService.createAccount(workspaceId, dto);

        assertThat(result.getName()).isEqualTo("Amex Gold");
        assertThat(result.getStartingBalance()).isEqualByComparingTo(new BigDecimal("-1500.00"));
        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("-1500.00"));
        assertThat(result.getCurrency()).isEqualTo(CurrencyCode.EUR);
        assertThat(result.getInstitutionName()).isEqualTo("American Express");
        assertThat(result.getSource()).isEqualTo(AccountSource.AUTOMATIC);
    }

    @Test
    void createAccount_duplicateName_throws() {
        CreateAccountDto dto = CreateAccountDto.builder()
                .name("Checking")
                .type(AccountType.CASH)
                .subType(AccountSubType.CHECKING)
                .build();

        when(accountRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, "Checking")).thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(workspaceId, dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
        verify(accountRepository, never()).save(any());
    }

    @Test
    void createAccount_invalidTypeSubType_throws() {
        CreateAccountDto dto = CreateAccountDto.builder()
                .name("Bad Account")
                .type(AccountType.CASH)
                .subType(AccountSubType.CREDIT_CARD)
                .build();

        when(accountRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, "Bad Account")).thenReturn(false);

        assertThatThrownBy(() -> accountService.createAccount(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("is not valid for account type")
                .hasMessageContaining("Allowed sub-types:");
        verify(accountRepository, never()).save(any());
    }

    // --- updateAccount ---

    @Test
    void updateAccount_name() {
        Account account = buildAccount("Old Name", AccountType.CASH, AccountSubType.CHECKING);
        when(accountRepository.findByIdAndWorkspaceId(accountId, workspaceId)).thenReturn(Optional.of(account));
        when(accountRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, "New Name")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAccountDto dto = UpdateAccountDto.builder().name("New Name").build();
        Account result = accountService.updateAccount(accountId, workspaceId, dto);

        assertThat(result.getName()).isEqualTo("New Name");
    }

    @Test
    void updateAccount_duplicateName_throws() {
        Account account = buildAccount("Old Name", AccountType.CASH, AccountSubType.CHECKING);
        when(accountRepository.findByIdAndWorkspaceId(accountId, workspaceId)).thenReturn(Optional.of(account));
        when(accountRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, "Existing")).thenReturn(true);

        UpdateAccountDto dto = UpdateAccountDto.builder().name("Existing").build();

        assertThatThrownBy(() -> accountService.updateAccount(accountId, workspaceId, dto))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateAccount_sameName_allowed() {
        Account account = buildAccount("Checking", AccountType.CASH, AccountSubType.CHECKING);
        when(accountRepository.findByIdAndWorkspaceId(accountId, workspaceId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAccountDto dto = UpdateAccountDto.builder().name("Checking").build();
        Account result = accountService.updateAccount(accountId, workspaceId, dto);

        assertThat(result.getName()).isEqualTo("Checking");
    }

    @Test
    void updateAccount_startingBalance_triggersRecompute() {
        Account account = buildAccount("Checking", AccountType.CASH, AccountSubType.CHECKING);
        when(accountRepository.findByIdAndWorkspaceId(accountId, workspaceId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.sumTransactionAmounts(accountId)).thenReturn(new BigDecimal("-200.00"));

        UpdateAccountDto dto = UpdateAccountDto.builder().startingBalance(new BigDecimal("1000.00")).build();
        Account result = accountService.updateAccount(accountId, workspaceId, dto);

        assertThat(result.getStartingBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(result.getBalanceLastUpdated()).isNotNull();
    }

    @Test
    void updateAccount_statusClosed_setsClosedAt() {
        Account account = buildAccount("Checking", AccountType.CASH, AccountSubType.CHECKING);
        when(accountRepository.findByIdAndWorkspaceId(accountId, workspaceId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAccountDto dto = UpdateAccountDto.builder().status(Status.CLOSED).build();
        Account result = accountService.updateAccount(accountId, workspaceId, dto);

        assertThat(result.getStatus()).isEqualTo(Status.CLOSED);
        assertThat(result.getClosedAt()).isNotNull();
    }

    @Test
    void updateAccount_statusReactivated_clearsClosedAt() {
        Account account = buildAccount("Checking", AccountType.CASH, AccountSubType.CHECKING);
        account.setStatus(Status.CLOSED);
        account.setClosedAt(java.time.LocalDateTime.now());
        when(accountRepository.findByIdAndWorkspaceId(accountId, workspaceId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAccountDto dto = UpdateAccountDto.builder().status(Status.ACTIVE).build();
        Account result = accountService.updateAccount(accountId, workspaceId, dto);

        assertThat(result.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(result.getClosedAt()).isNull();
    }

    @Test
    void updateAccount_invalidTypeSubType_throws() {
        Account account = buildAccount("Checking", AccountType.CASH, AccountSubType.CHECKING);
        when(accountRepository.findByIdAndWorkspaceId(accountId, workspaceId)).thenReturn(Optional.of(account));

        UpdateAccountDto dto = UpdateAccountDto.builder().subType(AccountSubType.CREDIT_CARD).build();

        assertThatThrownBy(() -> accountService.updateAccount(accountId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("is not valid for account type")
                .hasMessageContaining("Allowed sub-types:");
    }

    @Test
    void updateAccount_notFound_throws() {
        when(accountRepository.findByIdAndWorkspaceId(accountId, workspaceId)).thenReturn(Optional.empty());

        UpdateAccountDto dto = UpdateAccountDto.builder().name("X").build();

        assertThatThrownBy(() -> accountService.updateAccount(accountId, workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- deleteAccount ---

    @Test
    void deleteAccount_success() {
        Account account = buildAccount("Checking", AccountType.CASH, AccountSubType.CHECKING);
        when(accountRepository.findByIdAndWorkspaceId(accountId, workspaceId)).thenReturn(Optional.of(account));

        accountService.deleteAccount(accountId, workspaceId);

        verify(accountRepository).delete(account);
    }

    @Test
    void deleteAccount_notFound_throws() {
        when(accountRepository.findByIdAndWorkspaceId(accountId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.deleteAccount(accountId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- recomputeBalance ---

    @Test
    void recomputeBalance_addsTransactionSumToStartingBalance() {
        Account account = buildAccount("Checking", AccountType.CASH, AccountSubType.CHECKING);
        account.setStartingBalance(new BigDecimal("5000.00"));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.sumTransactionAmounts(accountId)).thenReturn(new BigDecimal("-1200.50"));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        accountService.recomputeBalance(accountId);

        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("3799.50"));
        assertThat(account.getBalanceLastUpdated()).isNotNull();
    }

    @Test
    void recomputeBalance_noTransactions_balanceEqualsStarting() {
        Account account = buildAccount("Checking", AccountType.CASH, AccountSubType.CHECKING);
        account.setStartingBalance(new BigDecimal("2500.00"));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.sumTransactionAmounts(accountId)).thenReturn(BigDecimal.ZERO);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        accountService.recomputeBalance(accountId);

        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("2500.00"));
    }

    @Test
    void recomputeBalance_accountNotFound_throws() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.recomputeBalance(accountId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
