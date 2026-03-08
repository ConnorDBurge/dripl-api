package com.balanced.aggregation.service;

import com.balanced.account.dto.CreateAccountInput;
import com.balanced.account.entity.Account;
import com.balanced.account.enums.AccountSource;
import com.balanced.account.enums.AccountType;
import com.balanced.account.repository.AccountRepository;
import com.balanced.account.service.AccountService;
import com.balanced.aggregation.client.BankAggregatorClient;
import com.balanced.aggregation.dto.AggregatedAccount;
import com.balanced.aggregation.dto.AggregatedTransaction;
import com.balanced.aggregation.dto.SyncResult;
import com.balanced.aggregation.entity.BankConnection;
import com.balanced.aggregation.enums.AggregationProvider;
import com.balanced.aggregation.mapper.AggregatedAccountTypeMapper;
import com.balanced.aggregation.repository.BankConnectionRepository;
import com.balanced.common.enums.Status;
import com.balanced.common.exception.ResourceNotFoundException;
import com.balanced.merchant.service.MerchantService;
import com.balanced.transaction.dto.CreateTransactionInput;
import com.balanced.transaction.entity.Transaction;
import com.balanced.transaction.enums.TransactionSource;
import com.balanced.transaction.enums.TransactionStatus;
import com.balanced.transaction.repository.TransactionRepository;
import com.balanced.transaction.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AggregationService {

    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);
    static final int INITIAL_SYNC_DAYS = 30;
    static final int SYNC_OVERLAP_DAYS = 10;

    private final BankAggregatorClient aggregatorClient;
    private final BankConnectionRepository bankConnectionRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final MerchantService merchantService;
    private final AggregationProvider provider;

    public AggregationService(BankAggregatorClient aggregatorClient,
                               BankConnectionRepository bankConnectionRepository,
                               AccountRepository accountRepository,
                               AccountService accountService,
                               TransactionService transactionService,
                               TransactionRepository transactionRepository,
                               MerchantService merchantService,
                               @org.springframework.beans.factory.annotation.Value("${balanced.aggregation.provider:teller}") String providerName) {
        this.aggregatorClient = aggregatorClient;
        this.bankConnectionRepository = bankConnectionRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.merchantService = merchantService;
        this.provider = AggregationProvider.valueOf(providerName.toUpperCase());
    }

    @Transactional
    public BankConnection linkBank(UUID workspaceId, String accessToken) {
        List<AggregatedAccount> externalAccounts = aggregatorClient.getAccounts(accessToken);
        if (externalAccounts.isEmpty()) {
            throw new IllegalStateException("No accounts returned from provider");
        }

        String enrollmentId = externalAccounts.getFirst().enrollmentId();
        String institutionId = externalAccounts.getFirst().institutionId();

        BankConnection connection = bankConnectionRepository
                .findFirstByWorkspaceIdAndInstitutionIdAndStatus(workspaceId, institutionId, Status.CLOSED)
                .map(existing -> {
                    existing.setAccessToken(accessToken);
                    existing.setEnrollmentId(enrollmentId);
                    existing.setStatus(Status.ACTIVE);
                    existing.setLastSyncedAt(null);
                    log.info("Reactivated closed connection {} for institution '{}'",
                            existing.getId(), existing.getInstitutionName());
                    return existing;
                })
                .orElseGet(() -> BankConnection.builder()
                        .workspaceId(workspaceId)
                        .provider(provider)
                        .accessToken(accessToken)
                        .enrollmentId(enrollmentId)
                        .institutionId(institutionId)
                        .institutionName(externalAccounts.getFirst().institutionName())
                        .status(Status.ACTIVE)
                        .build());

        connection = bankConnectionRepository.save(connection);

        for (AggregatedAccount extAccount : externalAccounts) {
            createAccountFromExternal(workspaceId, connection.getId(), extAccount);
        }

        log.info("Linked bank '{}' with {} accounts for workspace {}",
                connection.getInstitutionName(), externalAccounts.size(), workspaceId);
        return connection;
    }

    @Transactional
    public SyncResult syncTransactions(UUID bankConnectionId, UUID workspaceId) {
        BankConnection connection = getConnection(bankConnectionId, workspaceId);
        List<Account> linkedAccounts = accountRepository.findAllByBankConnectionId(connection.getId());

        LocalDate endDate = LocalDate.now();
        LocalDate startDate;
        if (connection.getLastSyncedAt() == null) {
            startDate = endDate.minusDays(INITIAL_SYNC_DAYS);
        } else {
            startDate = connection.getLastSyncedAt().toLocalDate().minusDays(SYNC_OVERLAP_DAYS);
        }

        int totalAdded = 0;
        int totalModified = 0;

        for (Account account : linkedAccounts) {
            List<AggregatedTransaction> externalTxns = aggregatorClient.getTransactions(
                    connection.getAccessToken(), account.getExternalId(), startDate, endDate);

            for (AggregatedTransaction extTxn : externalTxns) {
                Optional<Transaction> existing = transactionRepository
                        .findByExternalIdAndWorkspaceId(extTxn.externalId(), workspaceId);

                if (existing.isPresent()) {
                    if (updateTransactionFromExternal(existing.get(), account, extTxn)) {
                        totalModified++;
                    }
                } else {
                    createTransactionFromExternal(workspaceId, account, extTxn);
                    totalAdded++;
                }
            }
        }

        connection.setLastSyncedAt(LocalDateTime.now());
        bankConnectionRepository.save(connection);

        log.info("Synced transactions for connection {} (added: {}, modified: {}, accounts: {})",
                bankConnectionId, totalAdded, totalModified, linkedAccounts.size());

        return new SyncResult(totalAdded, totalModified, 0, linkedAccounts.size());
    }

    @Transactional
    public void unlinkBank(UUID bankConnectionId, UUID workspaceId) {
        BankConnection connection = getConnection(bankConnectionId, workspaceId);

        try {
            aggregatorClient.removeConnection(connection.getAccessToken());
        } catch (Exception e) {
            log.warn("Failed to remove provider enrollment for connection {}: {}",
                    bankConnectionId, e.getMessage());
        }

        List<Account> linkedAccounts = accountRepository.findAllByBankConnectionId(connection.getId());
        for (Account account : linkedAccounts) {
            account.setSource(AccountSource.MANUAL);
            account.setBankConnectionId(null);
            accountRepository.save(account);
        }

        connection.setStatus(Status.CLOSED);
        bankConnectionRepository.save(connection);

        log.info("Unlinked bank connection {} ({} accounts converted to manual)",
                bankConnectionId, linkedAccounts.size());
    }

    @Transactional(readOnly = true)
    public List<BankConnection> listConnections(UUID workspaceId) {
        return bankConnectionRepository.findAllByWorkspaceId(workspaceId);
    }

    @Transactional(readOnly = true)
    public BankConnection getConnection(UUID bankConnectionId, UUID workspaceId) {
        return bankConnectionRepository.findByIdAndWorkspaceId(bankConnectionId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank connection not found"));
    }

    private Account createAccountFromExternal(UUID workspaceId, UUID bankConnectionId,
                                               AggregatedAccount extAccount) {
        // Primary match: same externalId (stable in production)
        Optional<Account> existing = accountRepository.findByExternalIdAndWorkspaceId(
                extAccount.externalId(), workspaceId);
        if (existing.isPresent()) {
            return relinkAccount(existing.get(), bankConnectionId, extAccount);
        }

        // Fallback match: delinked account with same institution + lastFour + type
        AccountType mappedType = AggregatedAccountTypeMapper.mapTellerType(extAccount.type(), extAccount.subtype());
        if (extAccount.lastFour() != null) {
            Optional<Account> fallback = accountRepository
                    .findByWorkspaceIdAndInstitutionNameAndLastFourAndTypeAndBankConnectionIdIsNull(
                            workspaceId, extAccount.institutionName(), extAccount.lastFour(), mappedType);
            if (fallback.isPresent()) {
                return relinkAccount(fallback.get(), bankConnectionId, extAccount);
            }
        }

        String accountName = resolveUniqueAccountName(workspaceId, extAccount.name(),
                extAccount.institutionName());

        CreateAccountInput input = CreateAccountInput.builder()
                .name(accountName)
                .type(mappedType)
                .subType(AggregatedAccountTypeMapper.mapTellerSubType(extAccount.type(), extAccount.subtype()))
                .institutionName(extAccount.institutionName())
                .source(AccountSource.AUTOMATIC)
                .build();

        Account account = accountService.createAccount(workspaceId, input);
        account.setExternalId(extAccount.externalId());
        account.setLastFour(extAccount.lastFour());
        account.setBankConnectionId(bankConnectionId);
        return accountRepository.save(account);
    }

    private Account relinkAccount(Account account, UUID bankConnectionId, AggregatedAccount extAccount) {
        account.setBankConnectionId(bankConnectionId);
        account.setSource(AccountSource.AUTOMATIC);
        account.setExternalId(extAccount.externalId());
        if (extAccount.lastFour() != null) {
            account.setLastFour(extAccount.lastFour());
        }
        log.info("Relinked existing account '{}' to connection {}", account.getName(), bankConnectionId);
        return accountRepository.save(account);
    }

    private String resolveUniqueAccountName(UUID workspaceId, String name, String institutionName) {
        if (!accountRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, name)) {
            return name;
        }

        String withInstitution = "%s (%s)".formatted(name, institutionName);
        if (!accountRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, withInstitution)) {
            return withInstitution;
        }

        for (int i = 2; i <= 100; i++) {
            String numbered = "%s (%d)".formatted(withInstitution, i);
            if (!accountRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, numbered)) {
                return numbered;
            }
        }

        throw new IllegalStateException("Unable to generate unique account name for: " + name);
    }

    private void createTransactionFromExternal(UUID workspaceId, Account account,
                                                AggregatedTransaction extTxn) {
        String merchantName = resolveMerchantName(extTxn);
        BigDecimal amount = resolveAmount(account, extTxn);

        CreateTransactionInput input = CreateTransactionInput.builder()
                .accountId(account.getId())
                .merchantName(merchantName)
                .amount(amount)
                .date(extTxn.date().atStartOfDay())
                .build();

        Transaction transaction = transactionService.createTransaction(workspaceId, input);
        transaction.setExternalId(extTxn.externalId());
        transaction.setSource(TransactionSource.AUTOMATIC);
        applyStatus(transaction, extTxn);
        transactionRepository.save(transaction);
    }

    /**
     * Updates an existing transaction if any synced fields have changed.
     * Returns true if the transaction was modified.
     */
    private boolean updateTransactionFromExternal(Transaction existing, Account account,
                                                   AggregatedTransaction extTxn) {
        boolean modified = false;

        BigDecimal newAmount = resolveAmount(account, extTxn);
        if (existing.getAmount().compareTo(newAmount) != 0) {
            existing.setAmount(newAmount);
            modified = true;
        }

        LocalDateTime newDate = extTxn.date().atStartOfDay();
        if (!existing.getDate().equals(newDate)) {
            existing.setDate(newDate);
            modified = true;
        }

        TransactionStatus newStatus = "posted".equals(extTxn.status())
                ? TransactionStatus.POSTED : TransactionStatus.PENDING;
        if (existing.getStatus() != newStatus) {
            existing.setStatus(newStatus);
            if (newStatus == TransactionStatus.POSTED) {
                existing.setPostedAt(extTxn.date().atStartOfDay());
            }
            modified = true;
        }

        String newMerchantName = resolveMerchantName(extTxn);
        UUID newMerchantId = merchantService.resolveMerchant(newMerchantName, existing.getWorkspaceId()).getId();
        if (!existing.getMerchantId().equals(newMerchantId)) {
            existing.setMerchantId(newMerchantId);
            modified = true;
        }

        if (modified) {
            transactionRepository.save(existing);
        }

        return modified;
    }

    private String resolveMerchantName(AggregatedTransaction extTxn) {
        return extTxn.counterpartyName() != null
                ? extTxn.counterpartyName()
                : extTxn.description();
    }

    // Teller sign convention for credit accounts is opposite to ours:
    // Teller: positive = purchase (expense), negative = payment (income)
    // Ours:   negative = expense, positive = income
    private BigDecimal resolveAmount(Account account, AggregatedTransaction extTxn) {
        return account.getType() == AccountType.CREDIT
                ? extTxn.amount().negate()
                : extTxn.amount();
    }

    private void applyStatus(Transaction transaction, AggregatedTransaction extTxn) {
        if ("posted".equals(extTxn.status())) {
            transaction.setStatus(TransactionStatus.POSTED);
            transaction.setPostedAt(extTxn.date().atStartOfDay());
        }
    }
}
