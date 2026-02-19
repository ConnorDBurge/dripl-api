package com.dripl.account.service;

import com.dripl.account.dto.CreateAccountDto;
import com.dripl.account.dto.UpdateAccountDto;
import com.dripl.account.entity.Account;
import com.dripl.account.enums.AccountSubType;
import com.dripl.account.enums.AccountType;
import com.dripl.account.mapper.AccountMapper;
import com.dripl.account.repository.AccountRepository;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ConflictException;
import com.dripl.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    @Transactional(readOnly = true)
    public List<Account> listAllByWorkspaceId(UUID workspaceId) {
        return accountRepository.findAllByWorkspaceId(workspaceId);
    }

    @Transactional(readOnly = true)
    public Account getAccount(UUID accountId, UUID workspaceId) {
        return accountRepository.findByIdAndWorkspaceId(accountId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    @Transactional
    public Account createAccount(UUID workspaceId, CreateAccountDto dto) {
        if (accountRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, dto.getName())) {
            throw new ConflictException("An account named '" + dto.getName() + "' already exists");
        }

        // Check the subtype is valid for an account type
        if (dto.getType() != null && dto.getSubType() != null && !dto.getType().supportsSubType(dto.getSubType())) {
            throw typeSubTypeMismatch(dto.getType(), dto.getSubType());
        }

        Account account = accountRepository.save(Account.builder()
                .workspaceId(workspaceId)
                .name(dto.getName())

                .type(dto.getType())
                .subType(dto.getSubType())
                .balance(dto.getBalance() != null ? dto.getBalance() : BigDecimal.ZERO)
                .currency(dto.getCurrency() != null ? dto.getCurrency() : com.dripl.account.enums.CurrencyCode.USD)
                .institutionName(dto.getInstitutionName())
                .source(dto.getSource() != null ? dto.getSource() : com.dripl.account.enums.AccountSource.MANUAL)
                .build());

        log.info("Created account '{}' ({})", account.getName(), account.getId());
        return account;
    }

    @Transactional
    public Account updateAccount(UUID accountId, UUID workspaceId, UpdateAccountDto dto) {
        Account account = getAccount(accountId, workspaceId);

        if (dto.getName() != null
                && !dto.getName().equalsIgnoreCase(account.getName())
                && accountRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, dto.getName())) {
            throw new ConflictException("An account named '" + dto.getName() + "' already exists");
        }

        accountMapper.updateEntity(dto, account);

        if (account.getType() != null && account.getSubType() != null
                && !account.getType().supportsSubType(account.getSubType())) {
            throw typeSubTypeMismatch(account.getType(), account.getSubType());
        }

        log.info("Updating account '{}' ({})", account.getName(), accountId);
        return accountRepository.save(account);
    }

    @Transactional
    public void deleteAccount(UUID accountId, UUID workspaceId) {
        Account account = getAccount(accountId, workspaceId);
        log.info("Deleting account '{}' ({})", account.getName(), accountId);
        accountRepository.delete(account);
    }

    private BadRequestException typeSubTypeMismatch(AccountType type, AccountSubType subType) {
        String allowed = type.allowedSubTypes().stream()
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        return new BadRequestException(String.format(
                "Sub-type '%s' is not valid for account type '%s'. Allowed sub-types: [%s]",
                subType, type, allowed));
    }
}
