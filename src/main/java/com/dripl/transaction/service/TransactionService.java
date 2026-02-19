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

        UUID accountId, merchantId, categoryId;
        CurrencyCode currencyCode;
        Set<UUID> tagIds;
        String notes;

        if (ri != null) {
            // Locked fields always come from the recurring item
            accountId = ri.getAccountId();
            merchantId = ri.getMerchantId();
            categoryId = ri.getCategoryId();
            currencyCode = ri.getCurrencyCode();
            tagIds = new HashSet<>(ri.getTagIds());
            notes = ri.getNotes();
        } else {
            accountId = dto.getAccountId();
            if (accountId == null) throw new BadRequestException("Account ID must be provided");
            accountService.getAccount(accountId, workspaceId);
            merchantId = dto.getMerchantName() != null
                    ? merchantService.resolveMerchant(dto.getMerchantName(), workspaceId).getId()
                    : null;
            categoryId = dto.getCategoryId();
            currencyCode = dto.getCurrencyCode() != null ? dto.getCurrencyCode() : CurrencyCode.USD;
            tagIds = dto.getTagIds() != null ? dto.getTagIds() : new HashSet<>();
            notes = dto.getNotes();
        }

        if (ri != null) accountService.getAccount(accountId, workspaceId);
        if (merchantId == null) throw new BadRequestException("Merchant name must be provided");
        if (categoryId != null) categoryService.getCategory(categoryId, workspaceId);
        tagIds.forEach(tagId -> tagService.getTag(tagId, workspaceId));

        // Amount: DTO wins, then RI default
        BigDecimal amount = resolveRequired(dto.getAmount(), ri, RecurringItem::getAmount, "Amount");

        Transaction transaction = Transaction.builder()
                .workspaceId(workspaceId)
                .accountId(accountId)
                .merchantId(merchantId)
                .categoryId(categoryId)
                .date(dto.getDate())
                .amount(amount)
                .currencyCode(currencyCode)
                .notes(notes)
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

        // Enforce mutual exclusivity: can't link to recurring item if grouped
        if (dto.isRecurringItemIdSpecified() && dto.getRecurringItemId() != null && transaction.getGroupId() != null && !isUnlinkingGroup(dto)) {
            throw new BadRequestException(
                    "Transaction is in group " + transaction.getGroupId() + ". Remove it from the group before linking to a recurring item.");
        }

        // Enforce mutual exclusivity: can't set groupId-managed fields via a recurring item link if grouped
        // (groupId is managed by TransactionGroupService, not here)

        // Enforce locked fields when linked to a recurring item
        if (transaction.getRecurringItemId() != null && !isUnlinkingRecurringItem(dto)) {
            rejectLockedFieldsForRecurringItem(dto);
        }

        // Enforce locked fields when in a group (skip if unlinking from a group)
        if (transaction.getGroupId() != null && !isUnlinkingGroup(dto)) {
            rejectLockedFieldsForGroup(dto);
        }

        // Handle groupId unlink
        if (dto.isGroupIdSpecified()) {
            if (dto.getGroupId() != null) {
                throw new BadRequestException("Cannot assign a transaction to a group via this endpoint. Use the transaction-groups API.");
            }
            UUID oldGroupId = transaction.getGroupId();
            if (oldGroupId != null) {
                long remaining = transactionRepository.countByGroupId(oldGroupId);
                if (remaining < 3) {
                    throw new BadRequestException(
                            "Removing this transaction would leave group " + oldGroupId + " with fewer than 2 transactions. Dissolve the group instead.");
                }
            }
            transaction.setGroupId(null);
        }

        if (dto.isRecurringItemIdSpecified()) {
            if (dto.getRecurringItemId() != null) {
                var ri = recurringItemService.getRecurringItem(dto.getRecurringItemId(), workspaceId);
                transaction.setRecurringItemId(ri.getId());
                // Locked fields always come from the recurring item
                transaction.setAccountId(ri.getAccountId());
                transaction.setMerchantId(ri.getMerchantId());
                transaction.setCategoryId(ri.getCategoryId());
                transaction.setTagIds(new HashSet<>(ri.getTagIds()));
                transaction.setCurrencyCode(ri.getCurrencyCode());
                if (ri.getNotes() != null) transaction.setNotes(ri.getNotes());
                // Non-locked fields: use RI as default if not provided
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

        log.info("Updating transaction {}", transactionId);
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

    private boolean isUnlinkingRecurringItem(UpdateTransactionDto dto) {
        return dto.isRecurringItemIdSpecified() && dto.getRecurringItemId() == null;
    }

    private boolean isUnlinkingGroup(UpdateTransactionDto dto) {
        return dto.isGroupIdSpecified() && dto.getGroupId() == null;
    }

    private void rejectLockedFieldsForRecurringItem(UpdateTransactionDto dto) {
        List<String> locked = new java.util.ArrayList<>();
        if (dto.getAccountId() != null) locked.add("accountId");
        if (dto.getMerchantName() != null) locked.add("merchantName");
        if (dto.isCategoryIdSpecified()) locked.add("categoryId");
        if (dto.isTagIdsSpecified()) locked.add("tagIds");
        if (dto.isNotesSpecified()) locked.add("notes");
        if (dto.getCurrencyCode() != null) locked.add("currencyCode");
        if (!locked.isEmpty()) {
            throw new BadRequestException(
                    "Cannot modify " + String.join(", ", locked) + " while linked to a recurring item. Unlink the recurring item first.");
        }
    }

    private void rejectLockedFieldsForGroup(UpdateTransactionDto dto) {
        List<String> locked = new java.util.ArrayList<>();
        if (dto.isCategoryIdSpecified()) locked.add("categoryId");
        if (dto.isTagIdsSpecified()) locked.add("tagIds");
        if (dto.isNotesSpecified()) locked.add("notes");
        if (!locked.isEmpty()) {
            throw new BadRequestException(
                    "Cannot modify " + String.join(", ", locked) + " while in a transaction group. Update the group metadata instead.");
        }
    }
}
