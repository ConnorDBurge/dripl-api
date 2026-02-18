package com.dripl.transaction.service;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.account.service.AccountService;
import com.dripl.category.service.CategoryService;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.common.enums.Status;
import com.dripl.merchant.entity.Merchant;
import com.dripl.merchant.repository.MerchantRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final MerchantRepository merchantRepository;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final TransactionMapper transactionMapper;

    @Transactional(readOnly = true)
    public List<Transaction> listAllByWorkspaceId(UUID workspaceId) {
        return transactionRepository.findAllByWorkspaceId(workspaceId);
    }

    @Transactional(readOnly = true)
    public Transaction getTransaction(UUID transactionId, UUID workspaceId) {
        return transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
    }

    @Transactional
    public Transaction createTransaction(UUID workspaceId, CreateTransactionDto dto) {
        var account = accountService.getAccount(dto.getAccountId(), workspaceId);
        var merchant = resolveMerchant(dto.getMerchantName(), workspaceId);
        UUID categoryId = null;
        if (dto.getCategoryId() != null) {
            categoryId = categoryService.getCategory(dto.getCategoryId(), workspaceId).getId();
        }
        Set<UUID> tagIds = dto.getTagIds() != null ? dto.getTagIds() : new HashSet<>();
        tagIds.forEach(tagId -> tagService.getTag(tagId, workspaceId));

        LocalDateTime now = LocalDateTime.now();

        Transaction transaction = Transaction.builder()
                .workspaceId(workspaceId)
                .accountId(account.getId())
                .merchantId(merchant.getId())
                .categoryId(categoryId)
                .date(dto.getDate())
                .amount(dto.getAmount())
                .currencyCode(dto.getCurrencyCode() != null ? dto.getCurrencyCode() : CurrencyCode.USD)
                .notes(dto.getNotes())
                .status(TransactionStatus.PENDING)
                .source(TransactionSource.MANUAL)
                .pendingAt(now)
                .tagIds(tagIds)
                .build();

        log.info("Created transaction for merchant '{}' in workspace {}", merchant.getName(), workspaceId);
        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction updateTransaction(UUID transactionId, UUID workspaceId, UpdateTransactionDto dto) {
        Transaction transaction = getTransaction(transactionId, workspaceId);

        if (dto.getAccountId() != null) {
            var account = accountService.getAccount(dto.getAccountId(), workspaceId);
            transaction.setAccountId(account.getId());
        }

        // MapStruct handles simple fields: date, amount, currencyCode
        transactionMapper.updateEntity(dto, transaction);

        if (dto.getMerchantName() != null) {
            var merchant = resolveMerchant(dto.getMerchantName(), workspaceId);
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

        log.info("Updated transaction {} in workspace {}", transactionId, workspaceId);
        return transactionRepository.save(transaction);
    }

    @Transactional
    public void deleteTransaction(UUID transactionId, UUID workspaceId) {
        Transaction transaction = getTransaction(transactionId, workspaceId);
        log.info("Deleting transaction {} in workspace {}", transactionId, workspaceId);
        transactionRepository.delete(transaction);
    }

    private Merchant resolveMerchant(String merchantName, UUID workspaceId) {
        return merchantRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, merchantName)
                .orElseGet(() -> {
                    Merchant merchant = Merchant.builder()
                            .workspaceId(workspaceId)
                            .name(merchantName)
                            .status(Status.ACTIVE)
                            .build();
                    Merchant saved = merchantRepository.save(merchant);
                    log.info("Auto-created merchant '{}' in workspace {}", merchantName, workspaceId);
                    return saved;
                });
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
}
