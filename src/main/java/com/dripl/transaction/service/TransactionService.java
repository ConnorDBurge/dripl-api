package com.dripl.transaction.service;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.account.service.AccountService;
import com.dripl.category.service.CategoryService;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.merchant.service.MerchantService;
import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.service.RecurringItemService;
import com.dripl.tag.service.TagService;
import com.dripl.transaction.dto.CreateTransactionDto;
import com.dripl.transaction.dto.UpdateTransactionDto;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.enums.TransactionSource;
import com.dripl.transaction.enums.TransactionStatus;
import com.dripl.transaction.mapper.TransactionMapper;
import com.dripl.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final MerchantService merchantService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final RecurringItemService recurringItemService;
    private final TransactionMapper transactionMapper;

    @Transactional(readOnly = true)
    public List<Transaction> listAll(Specification<Transaction> spec) {
        return transactionRepository.findAll(spec);
    }

    @Transactional(readOnly = true)
    public Transaction getTransaction(UUID transactionId, UUID workspaceId) {
        return transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
    }

    @Transactional
    public Transaction createTransaction(UUID workspaceId, CreateTransactionDto dto) {
        RecurringItem ri = dto.getRecurringItemId() != null
                ? recurringItemService.getRecurringItem(dto.getRecurringItemId(), workspaceId)
                : null;

        UUID accountId = resolveRequired(dto.getAccountId(), ri, RecurringItem::getAccountId, "Account ID");
        accountService.getAccount(accountId, workspaceId);

        UUID merchantId = dto.getMerchantName() != null
                ? merchantService.resolveMerchant(dto.getMerchantName(), workspaceId).getId()
                : resolveRequired(null, ri, RecurringItem::getMerchantId, "Merchant name");

        UUID categoryId = resolveOptional(dto.getCategoryId(), ri, RecurringItem::getCategoryId, null);
        if (categoryId != null) {
            categoryService.getCategory(categoryId, workspaceId);
        }

        BigDecimal amount = resolveRequired(dto.getAmount(), ri, RecurringItem::getAmount, "Amount");
        CurrencyCode currencyCode = resolveOptional(dto.getCurrencyCode(), ri, RecurringItem::getCurrencyCode, CurrencyCode.USD);

        Set<UUID> tagIds = resolveOptional(dto.getTagIds(), ri, r -> new HashSet<>(r.getTagIds()), new HashSet<>());
        tagIds.forEach(tagId -> tagService.getTag(tagId, workspaceId));

        Transaction transaction = Transaction.builder()
                .workspaceId(workspaceId)
                .accountId(accountId)
                .merchantId(merchantId)
                .categoryId(categoryId)
                .date(dto.getDate())
                .amount(amount)
                .currencyCode(currencyCode)
                .notes(dto.getNotes())
                .status(TransactionStatus.PENDING)
                .source(TransactionSource.MANUAL)
                .pendingAt(LocalDateTime.now())
                .recurringItemId(ri != null ? ri.getId() : null)
                .tagIds(tagIds)
                .build();

        log.info("Created transaction for merchant '{}'", merchantId);
        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction updateTransaction(UUID transactionId, UUID workspaceId, UpdateTransactionDto dto) {
        Transaction transaction = getTransaction(transactionId, workspaceId);

        if (dto.isRecurringItemIdSpecified()) {
            if (dto.getRecurringItemId() != null) {
                var ri = recurringItemService.getRecurringItem(dto.getRecurringItemId(), workspaceId);
                transaction.setRecurringItemId(ri.getId());
                // Inherit defaults for fields not explicitly provided
                if (dto.getAccountId() == null) transaction.setAccountId(ri.getAccountId());
                if (dto.getMerchantName() == null) transaction.setMerchantId(ri.getMerchantId());
                if (!dto.isCategoryIdSpecified()) transaction.setCategoryId(ri.getCategoryId());
                if (!dto.isTagIdsSpecified()) transaction.setTagIds(new HashSet<>(ri.getTagIds()));
                if (dto.getCurrencyCode() == null) transaction.setCurrencyCode(ri.getCurrencyCode());
                if (dto.getAmount() == null) transaction.setAmount(ri.getAmount());
            } else {
                transaction.setRecurringItemId(null);
            }
        }

        if (dto.getAccountId() != null) {
            var account = accountService.getAccount(dto.getAccountId(), workspaceId);
            transaction.setAccountId(account.getId());
        }

        // MapStruct handles simple fields: date, amount, currencyCode
        transactionMapper.updateEntity(dto, transaction);

        if (dto.getMerchantName() != null) {
            var merchant = merchantService.resolveMerchant(dto.getMerchantName(), workspaceId);
            transaction.setMerchantId(merchant.getId());
        }

        if (dto.isCategoryIdSpecified()) {
            if (dto.getCategoryId() != null) {
                var category = categoryService.getCategory(dto.getCategoryId(), workspaceId);
                transaction.setCategoryId(category.getId());
            } else {
                transaction.setCategoryId(null);
            }
        }

        if (dto.isNotesSpecified()) {
            transaction.setNotes(dto.getNotes());
        }

        if (dto.getStatus() != null) {
            applyStatusTransition(transaction, dto.getStatus());
        }

        if (dto.isTagIdsSpecified()) {
            Set<UUID> tagIds = dto.getTagIds() != null ? dto.getTagIds() : new HashSet<>();
            tagIds.forEach(tagId -> tagService.getTag(tagId, workspaceId));
            transaction.setTagIds(tagIds);
        }

        log.info("Updated transaction {}", transactionId);
        return transactionRepository.save(transaction);
    }

    @Transactional
    public void deleteTransaction(UUID transactionId, UUID workspaceId) {
        Transaction transaction = getTransaction(transactionId, workspaceId);
        log.info("Deleting transaction {}", transactionId);
        transactionRepository.delete(transaction);
    }

    private void applyStatusTransition(Transaction transaction, TransactionStatus newStatus) {
        LocalDateTime now = LocalDateTime.now();
        if (newStatus == TransactionStatus.PENDING) {
            transaction.setPendingAt(now);
        } else if (newStatus == TransactionStatus.POSTED) {
            transaction.setPostedAt(now);
        }
        transaction.setStatus(newStatus);
    }

    // Return DTO value if provided, or recurring item value, or throw exception
    private <T> T resolveRequired(T dtoValue, RecurringItem ri, Function<RecurringItem, T> fallback, String fieldName) {
        return Optional.ofNullable(dtoValue)
                .or(() -> Optional.ofNullable(ri).map(fallback))
                .orElseThrow(() -> new BadRequestException(fieldName + " must be provided"));
    }

    // Return DTO value if provided, or recurring item value, or default value
    private <T> T resolveOptional(T dtoValue, RecurringItem ri, Function<RecurringItem, T> fallback, T defaultValue) {
        return Optional.ofNullable(dtoValue)
                .or(() -> Optional.ofNullable(ri).map(fallback))
                .orElse(defaultValue);
    }
}
