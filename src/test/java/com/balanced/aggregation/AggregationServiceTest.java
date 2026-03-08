package com.balanced.aggregation;

import com.balanced.account.dto.CreateAccountInput;
import com.balanced.account.entity.Account;
import com.balanced.account.enums.AccountSource;
import com.balanced.account.enums.AccountSubType;
import com.balanced.account.enums.AccountType;
import com.balanced.account.repository.AccountRepository;
import com.balanced.account.service.AccountService;
import com.balanced.aggregation.client.BankAggregatorClient;
import com.balanced.aggregation.dto.AggregatedAccount;
import com.balanced.aggregation.dto.AggregatedTransaction;
import com.balanced.aggregation.dto.SyncResult;
import com.balanced.aggregation.entity.BankConnection;
import com.balanced.aggregation.enums.AggregationProvider;
import com.balanced.aggregation.repository.BankConnectionRepository;
import com.balanced.aggregation.service.AggregationService;
import com.balanced.common.enums.Status;
import com.balanced.common.exception.ResourceNotFoundException;
import com.balanced.merchant.entity.Merchant;
import com.balanced.merchant.service.MerchantService;
import com.balanced.transaction.entity.Transaction;
import com.balanced.transaction.enums.TransactionSource;
import com.balanced.transaction.enums.TransactionStatus;
import com.balanced.transaction.repository.TransactionRepository;
import com.balanced.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregationServiceTest {

    @Mock private BankAggregatorClient aggregatorClient;
    @Mock private BankConnectionRepository bankConnectionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountService accountService;
    @Mock private TransactionService transactionService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private MerchantService merchantService;

    private AggregationService aggregationService;

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID CONNECTION_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final String ACCESS_TOKEN = "test_access_token";

    @BeforeEach
    void initService() {
        aggregationService = new AggregationService(
                aggregatorClient, bankConnectionRepository, accountRepository,
                accountService, transactionService, transactionRepository,
                merchantService, "teller");
    }

    private AggregatedAccount mockAccount(String externalId, String type, String subtype) {
        return new AggregatedAccount(externalId, "enr_001", "Test Account",
                type, subtype, "Test Bank", "test_bank", "USD", "1234", "open");
    }

    private AggregatedTransaction mockTransaction(String externalId) {
        return new AggregatedTransaction(externalId, "acc_001",
                new BigDecimal("-42.50"), LocalDate.of(2026, 1, 15),
                "AMAZON PURCHASE", "Amazon", "shopping", "posted", "card_payment");
    }

    private AggregatedTransaction mockTransaction(String externalId, BigDecimal amount,
                                                   LocalDate date, String status) {
        return new AggregatedTransaction(externalId, "acc_001",
                amount, date, "PURCHASE", "Merchant", "shopping", status, "card_payment");
    }

    private BankConnection mockConnection() {
        return BankConnection.builder()
                .id(CONNECTION_ID)
                .workspaceId(WORKSPACE_ID)
                .provider(AggregationProvider.TELLER)
                .accessToken(ACCESS_TOKEN)
                .enrollmentId("enr_001")
                .institutionName("Test Bank")
                .status(Status.ACTIVE)
                .build();
    }

    private Account mockAccountEntity() {
        return Account.builder()
                .id(ACCOUNT_ID)
                .workspaceId(WORKSPACE_ID)
                .name("Test Checking")
                .type(AccountType.CASH)
                .subType(AccountSubType.CHECKING)
                .externalId("acc_ext_001")
                .bankConnectionId(CONNECTION_ID)
                .source(AccountSource.AUTOMATIC)
                .balance(BigDecimal.ZERO)
                .build();
    }

    private Merchant mockMerchant() {
        Merchant m = new Merchant();
        m.setId(MERCHANT_ID);
        m.setName("Amazon");
        return m;
    }

    @Nested
    class LinkBank {

        @Test
        void createsConnectionAndAccounts() {
            var accounts = List.of(
                    mockAccount("acc_001", "depository", "checking"),
                    mockAccount("acc_002", "depository", "savings")
            );
            when(aggregatorClient.getAccounts(ACCESS_TOKEN)).thenReturn(accounts);
            when(bankConnectionRepository.findFirstByWorkspaceIdAndInstitutionIdAndStatus(
                    WORKSPACE_ID, "test_bank", Status.CLOSED)).thenReturn(Optional.empty());
            when(bankConnectionRepository.save(any(BankConnection.class)))
                    .thenAnswer(inv -> {
                        BankConnection c = inv.getArgument(0);
                        c.setId(CONNECTION_ID);
                        return c;
                    });
            when(accountRepository.findByExternalIdAndWorkspaceId(anyString(), eq(WORKSPACE_ID)))
                    .thenReturn(Optional.empty());
            when(accountService.createAccount(eq(WORKSPACE_ID), any()))
                    .thenReturn(Account.builder().id(UUID.randomUUID()).build());
            when(accountRepository.save(any(Account.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            BankConnection result = aggregationService.linkBank(WORKSPACE_ID, ACCESS_TOKEN);

            assertThat(result.getProvider()).isEqualTo(AggregationProvider.TELLER);
            assertThat(result.getEnrollmentId()).isEqualTo("enr_001");
            assertThat(result.getInstitutionName()).isEqualTo("Test Bank");
            assertThat(result.getStatus()).isEqualTo(Status.ACTIVE);

            verify(accountService, org.mockito.Mockito.times(2)).createAccount(eq(WORKSPACE_ID), any());
            verify(accountRepository, org.mockito.Mockito.times(2)).save(any(Account.class));
        }

        @Test
        void throwsWhenNoAccountsReturned() {
            when(aggregatorClient.getAccounts(ACCESS_TOKEN)).thenReturn(List.of());

            assertThatThrownBy(() -> aggregationService.linkBank(WORKSPACE_ID, ACCESS_TOKEN))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No accounts returned");
        }

        @Test
        void relinksExistingAccountByExternalId() {
            Account existingAccount = Account.builder()
                    .id(ACCOUNT_ID)
                    .workspaceId(WORKSPACE_ID)
                    .name("My Checking")
                    .source(AccountSource.MANUAL)
                    .externalId("acc_001")
                    .build();

            when(aggregatorClient.getAccounts(ACCESS_TOKEN))
                    .thenReturn(List.of(mockAccount("acc_001", "depository", "checking")));
            when(bankConnectionRepository.findFirstByWorkspaceIdAndInstitutionIdAndStatus(
                    WORKSPACE_ID, "test_bank", Status.CLOSED)).thenReturn(Optional.empty());
            when(bankConnectionRepository.save(any(BankConnection.class)))
                    .thenAnswer(inv -> {
                        BankConnection c = inv.getArgument(0);
                        c.setId(CONNECTION_ID);
                        return c;
                    });
            when(accountRepository.findByExternalIdAndWorkspaceId("acc_001", WORKSPACE_ID))
                    .thenReturn(Optional.of(existingAccount));
            when(accountRepository.save(any(Account.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            aggregationService.linkBank(WORKSPACE_ID, ACCESS_TOKEN);

            ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(captor.capture());
            assertThat(captor.getValue().getSource()).isEqualTo(AccountSource.AUTOMATIC);
            assertThat(captor.getValue().getBankConnectionId()).isEqualTo(CONNECTION_ID);
            verify(accountService, never()).createAccount(any(), any());
        }

        @Test
        void autoRenamesWhenNameClashes() {
            when(aggregatorClient.getAccounts(ACCESS_TOKEN))
                    .thenReturn(List.of(mockAccount("acc_new", "depository", "checking")));
            when(bankConnectionRepository.findFirstByWorkspaceIdAndInstitutionIdAndStatus(
                    WORKSPACE_ID, "test_bank", Status.CLOSED)).thenReturn(Optional.empty());
            when(bankConnectionRepository.save(any(BankConnection.class)))
                    .thenAnswer(inv -> {
                        BankConnection c = inv.getArgument(0);
                        c.setId(CONNECTION_ID);
                        return c;
                    });
            when(accountRepository.findByExternalIdAndWorkspaceId("acc_new", WORKSPACE_ID))
                    .thenReturn(Optional.empty());
            // "Test Account" already exists
            when(accountRepository.existsByWorkspaceIdAndNameIgnoreCase(WORKSPACE_ID, "Test Account"))
                    .thenReturn(true);
            when(accountRepository.existsByWorkspaceIdAndNameIgnoreCase(WORKSPACE_ID, "Test Account (Test Bank)"))
                    .thenReturn(false);
            when(accountService.createAccount(eq(WORKSPACE_ID), any()))
                    .thenReturn(Account.builder().id(UUID.randomUUID()).build());
            when(accountRepository.save(any(Account.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            aggregationService.linkBank(WORKSPACE_ID, ACCESS_TOKEN);

            ArgumentCaptor<CreateAccountInput> captor = ArgumentCaptor.forClass(CreateAccountInput.class);
            verify(accountService).createAccount(eq(WORKSPACE_ID), captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Test Account (Test Bank)");
        }

        @Test
        void reactivatesClosedConnectionOnRelink() {
            BankConnection closedConnection = BankConnection.builder()
                    .id(CONNECTION_ID)
                    .workspaceId(WORKSPACE_ID)
                    .provider(AggregationProvider.TELLER)
                    .accessToken("old_token")
                    .enrollmentId("enr_old")
                    .institutionId("test_bank")
                    .institutionName("Test Bank")
                    .status(Status.CLOSED)
                    .lastSyncedAt(LocalDateTime.now().minusDays(5))
                    .build();

            when(aggregatorClient.getAccounts(ACCESS_TOKEN))
                    .thenReturn(List.of(mockAccount("acc_001", "depository", "checking")));
            when(bankConnectionRepository.findFirstByWorkspaceIdAndInstitutionIdAndStatus(
                    WORKSPACE_ID, "test_bank", Status.CLOSED))
                    .thenReturn(Optional.of(closedConnection));
            when(bankConnectionRepository.save(any(BankConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(accountRepository.findByExternalIdAndWorkspaceId("acc_001", WORKSPACE_ID))
                    .thenReturn(Optional.empty());
            when(accountService.createAccount(eq(WORKSPACE_ID), any()))
                    .thenReturn(Account.builder().id(UUID.randomUUID()).build());
            when(accountRepository.save(any(Account.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            BankConnection result = aggregationService.linkBank(WORKSPACE_ID, ACCESS_TOKEN);

            assertThat(result.getId()).isEqualTo(CONNECTION_ID);
            assertThat(result.getAccessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.getEnrollmentId()).isEqualTo("enr_001");
            assertThat(result.getStatus()).isEqualTo(Status.ACTIVE);
            assertThat(result.getLastSyncedAt()).isNull();

            ArgumentCaptor<BankConnection> connCaptor = ArgumentCaptor.forClass(BankConnection.class);
            verify(bankConnectionRepository).save(connCaptor.capture());
            assertThat(connCaptor.getValue().getId()).isEqualTo(CONNECTION_ID);
        }

        @Test
        void relinksDelinkedAccountByLastFour() {
            Account delinkedAccount = Account.builder()
                    .id(ACCOUNT_ID)
                    .workspaceId(WORKSPACE_ID)
                    .name("My Checking")
                    .type(AccountType.CASH)
                    .institutionName("Test Bank")
                    .lastFour("1234")
                    .source(AccountSource.MANUAL)
                    .bankConnectionId(null)
                    .build();

            // New enrollment generates a different externalId
            when(aggregatorClient.getAccounts(ACCESS_TOKEN))
                    .thenReturn(List.of(mockAccount("acc_new_id", "depository", "checking")));
            when(bankConnectionRepository.findFirstByWorkspaceIdAndInstitutionIdAndStatus(
                    WORKSPACE_ID, "test_bank", Status.CLOSED)).thenReturn(Optional.empty());
            when(bankConnectionRepository.save(any(BankConnection.class)))
                    .thenAnswer(inv -> {
                        BankConnection c = inv.getArgument(0);
                        c.setId(CONNECTION_ID);
                        return c;
                    });
            // externalId miss
            when(accountRepository.findByExternalIdAndWorkspaceId("acc_new_id", WORKSPACE_ID))
                    .thenReturn(Optional.empty());
            // lastFour fallback hits
            when(accountRepository.findByWorkspaceIdAndInstitutionNameAndLastFourAndTypeAndBankConnectionIdIsNull(
                    WORKSPACE_ID, "Test Bank", "1234", AccountType.CASH))
                    .thenReturn(Optional.of(delinkedAccount));
            when(accountRepository.save(any(Account.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            aggregationService.linkBank(WORKSPACE_ID, ACCESS_TOKEN);

            ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(captor.capture());
            Account saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(ACCOUNT_ID);
            assertThat(saved.getSource()).isEqualTo(AccountSource.AUTOMATIC);
            assertThat(saved.getBankConnectionId()).isEqualTo(CONNECTION_ID);
            assertThat(saved.getExternalId()).isEqualTo("acc_new_id");
            verify(accountService, never()).createAccount(any(), any());
        }
    }

    @Nested
    class SyncTransactionsTests {

        private BankConnection connection;
        private Account account;

        @BeforeEach
        void setUp() {
            connection = mockConnection();
            account = mockAccountEntity();
        }

        @Test
        void importsNewTransactions() {
            when(bankConnectionRepository.findByIdAndWorkspaceId(CONNECTION_ID, WORKSPACE_ID))
                    .thenReturn(Optional.of(connection));
            when(accountRepository.findAllByBankConnectionId(CONNECTION_ID))
                    .thenReturn(List.of(account));
            when(aggregatorClient.getTransactions(eq(ACCESS_TOKEN), eq("acc_ext_001"), any(), any()))
                    .thenReturn(List.of(mockTransaction("txn_001"), mockTransaction("txn_002")));
            when(transactionRepository.findByExternalIdAndWorkspaceId(anyString(), eq(WORKSPACE_ID)))
                    .thenReturn(Optional.empty());
            when(transactionService.createTransaction(eq(WORKSPACE_ID), any()))
                    .thenReturn(Transaction.builder().id(UUID.randomUUID()).build());
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(bankConnectionRepository.save(any(BankConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SyncResult result = aggregationService.syncTransactions(CONNECTION_ID, WORKSPACE_ID);

            assertThat(result.transactionsAdded()).isEqualTo(2);
            assertThat(result.transactionsModified()).isEqualTo(0);
            assertThat(result.accountsSynced()).isEqualTo(1);
            verify(transactionService, org.mockito.Mockito.times(2)).createTransaction(eq(WORKSPACE_ID), any());
        }

        @Test
        void updatesExistingTransactionWhenFieldsChange() {
            Transaction existing = Transaction.builder()
                    .id(UUID.randomUUID())
                    .workspaceId(WORKSPACE_ID)
                    .accountId(ACCOUNT_ID)
                    .merchantId(MERCHANT_ID)
                    .amount(new BigDecimal("-30.00"))
                    .date(LocalDate.of(2026, 1, 14).atStartOfDay())
                    .status(TransactionStatus.PENDING)
                    .build();

            when(bankConnectionRepository.findByIdAndWorkspaceId(CONNECTION_ID, WORKSPACE_ID))
                    .thenReturn(Optional.of(connection));
            when(accountRepository.findAllByBankConnectionId(CONNECTION_ID))
                    .thenReturn(List.of(account));
            when(aggregatorClient.getTransactions(eq(ACCESS_TOKEN), eq("acc_ext_001"), any(), any()))
                    .thenReturn(List.of(mockTransaction("txn_existing", new BigDecimal("-42.50"),
                            LocalDate.of(2026, 1, 15), "posted")));
            when(transactionRepository.findByExternalIdAndWorkspaceId("txn_existing", WORKSPACE_ID))
                    .thenReturn(Optional.of(existing));
            when(merchantService.resolveMerchant(anyString(), eq(WORKSPACE_ID)))
                    .thenReturn(mockMerchant());
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(bankConnectionRepository.save(any(BankConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SyncResult result = aggregationService.syncTransactions(CONNECTION_ID, WORKSPACE_ID);

            assertThat(result.transactionsAdded()).isEqualTo(0);
            assertThat(result.transactionsModified()).isEqualTo(1);

            assertThat(existing.getAmount()).isEqualByComparingTo(new BigDecimal("-42.50"));
            assertThat(existing.getDate()).isEqualTo(LocalDate.of(2026, 1, 15).atStartOfDay());
            assertThat(existing.getStatus()).isEqualTo(TransactionStatus.POSTED);
            assertThat(existing.getPostedAt()).isEqualTo(LocalDate.of(2026, 1, 15).atStartOfDay());
        }

        @Test
        void skipsUpdateWhenNothingChanged() {
            Transaction existing = Transaction.builder()
                    .id(UUID.randomUUID())
                    .workspaceId(WORKSPACE_ID)
                    .accountId(ACCOUNT_ID)
                    .merchantId(MERCHANT_ID)
                    .amount(new BigDecimal("-42.50"))
                    .date(LocalDate.of(2026, 1, 15).atStartOfDay())
                    .status(TransactionStatus.POSTED)
                    .build();

            when(bankConnectionRepository.findByIdAndWorkspaceId(CONNECTION_ID, WORKSPACE_ID))
                    .thenReturn(Optional.of(connection));
            when(accountRepository.findAllByBankConnectionId(CONNECTION_ID))
                    .thenReturn(List.of(account));
            when(aggregatorClient.getTransactions(eq(ACCESS_TOKEN), eq("acc_ext_001"), any(), any()))
                    .thenReturn(List.of(mockTransaction("txn_unchanged")));
            when(transactionRepository.findByExternalIdAndWorkspaceId("txn_unchanged", WORKSPACE_ID))
                    .thenReturn(Optional.of(existing));
            when(merchantService.resolveMerchant("Amazon", WORKSPACE_ID))
                    .thenReturn(mockMerchant());
            when(bankConnectionRepository.save(any(BankConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SyncResult result = aggregationService.syncTransactions(CONNECTION_ID, WORKSPACE_ID);

            assertThat(result.transactionsAdded()).isEqualTo(0);
            assertThat(result.transactionsModified()).isEqualTo(0);
            verify(transactionService, never()).createTransaction(any(), any());
        }

        @Test
        void usesInitialSyncWindowWhenNeverSynced() {
            when(bankConnectionRepository.findByIdAndWorkspaceId(CONNECTION_ID, WORKSPACE_ID))
                    .thenReturn(Optional.of(connection));
            when(accountRepository.findAllByBankConnectionId(CONNECTION_ID))
                    .thenReturn(List.of(account));
            when(aggregatorClient.getTransactions(eq(ACCESS_TOKEN), eq("acc_ext_001"), any(), any()))
                    .thenReturn(List.of());
            when(bankConnectionRepository.save(any(BankConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            aggregationService.syncTransactions(CONNECTION_ID, WORKSPACE_ID);

            ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(aggregatorClient).getTransactions(eq(ACCESS_TOKEN), eq("acc_ext_001"),
                    startCaptor.capture(), endCaptor.capture());

            assertThat(startCaptor.getValue()).isEqualTo(LocalDate.now().minusDays(30));
            assertThat(endCaptor.getValue()).isEqualTo(LocalDate.now());
        }

        @Test
        void usesOverlapWindowOnSubsequentSync() {
            LocalDateTime lastSync = LocalDateTime.of(2026, 1, 10, 12, 0);
            connection.setLastSyncedAt(lastSync);

            when(bankConnectionRepository.findByIdAndWorkspaceId(CONNECTION_ID, WORKSPACE_ID))
                    .thenReturn(Optional.of(connection));
            when(accountRepository.findAllByBankConnectionId(CONNECTION_ID))
                    .thenReturn(List.of(account));
            when(aggregatorClient.getTransactions(eq(ACCESS_TOKEN), eq("acc_ext_001"), any(), any()))
                    .thenReturn(List.of());
            when(bankConnectionRepository.save(any(BankConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            aggregationService.syncTransactions(CONNECTION_ID, WORKSPACE_ID);

            ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(aggregatorClient).getTransactions(eq(ACCESS_TOKEN), eq("acc_ext_001"),
                    startCaptor.capture(), any());

            assertThat(startCaptor.getValue()).isEqualTo(
                    lastSync.toLocalDate().minusDays(10));
        }

        @Test
        void throwsWhenConnectionNotFound() {
            when(bankConnectionRepository.findByIdAndWorkspaceId(CONNECTION_ID, WORKSPACE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> aggregationService.syncTransactions(CONNECTION_ID, WORKSPACE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class UnlinkBank {

        @Test
        void closesConnectionAndConvertsAccounts() {
            var connection = mockConnection();
            var account = mockAccountEntity();

            when(bankConnectionRepository.findByIdAndWorkspaceId(CONNECTION_ID, WORKSPACE_ID))
                    .thenReturn(Optional.of(connection));
            when(accountRepository.findAllByBankConnectionId(CONNECTION_ID))
                    .thenReturn(List.of(account));
            when(accountRepository.save(any(Account.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(bankConnectionRepository.save(any(BankConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            aggregationService.unlinkBank(CONNECTION_ID, WORKSPACE_ID);

            verify(aggregatorClient).removeConnection(ACCESS_TOKEN);

            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(accountCaptor.capture());
            assertThat(accountCaptor.getValue().getSource()).isEqualTo(AccountSource.MANUAL);
            assertThat(accountCaptor.getValue().getBankConnectionId()).isNull();

            ArgumentCaptor<BankConnection> connCaptor = ArgumentCaptor.forClass(BankConnection.class);
            verify(bankConnectionRepository).save(connCaptor.capture());
            assertThat(connCaptor.getValue().getStatus()).isEqualTo(Status.CLOSED);
        }

        @Test
        void continuesWhenProviderRemovalFails() {
            var connection = mockConnection();

            when(bankConnectionRepository.findByIdAndWorkspaceId(CONNECTION_ID, WORKSPACE_ID))
                    .thenReturn(Optional.of(connection));
            org.mockito.Mockito.doThrow(new RuntimeException("API error"))
                    .when(aggregatorClient).removeConnection(ACCESS_TOKEN);
            when(accountRepository.findAllByBankConnectionId(CONNECTION_ID))
                    .thenReturn(List.of());
            when(bankConnectionRepository.save(any(BankConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            aggregationService.unlinkBank(CONNECTION_ID, WORKSPACE_ID);

            ArgumentCaptor<BankConnection> connCaptor = ArgumentCaptor.forClass(BankConnection.class);
            verify(bankConnectionRepository).save(connCaptor.capture());
            assertThat(connCaptor.getValue().getStatus()).isEqualTo(Status.CLOSED);
        }
    }

    @Nested
    class ListConnections {

        @Test
        void delegatesToRepository() {
            var connection = mockConnection();
            when(bankConnectionRepository.findAllByWorkspaceId(WORKSPACE_ID))
                    .thenReturn(List.of(connection));

            List<BankConnection> result = aggregationService.listConnections(WORKSPACE_ID);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getId()).isEqualTo(CONNECTION_ID);
        }
    }

    @Nested
    class GetConnection {

        @Test
        void returnsConnectionWhenFound() {
            var connection = mockConnection();
            when(bankConnectionRepository.findByIdAndWorkspaceId(CONNECTION_ID, WORKSPACE_ID))
                    .thenReturn(Optional.of(connection));

            BankConnection result = aggregationService.getConnection(CONNECTION_ID, WORKSPACE_ID);

            assertThat(result.getId()).isEqualTo(CONNECTION_ID);
        }

        @Test
        void throwsWhenNotFound() {
            when(bankConnectionRepository.findByIdAndWorkspaceId(CONNECTION_ID, WORKSPACE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> aggregationService.getConnection(CONNECTION_ID, WORKSPACE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Bank connection not found");
        }
    }
}
