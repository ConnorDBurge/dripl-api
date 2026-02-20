package com.dripl.transaction.split.service;

import com.dripl.category.service.CategoryService;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.merchant.service.MerchantService;
import com.dripl.tag.service.TagService;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.enums.TransactionSource;
import com.dripl.transaction.repository.TransactionRepository;
import com.dripl.transaction.split.dto.CreateTransactionSplitDto;
import com.dripl.transaction.split.dto.SplitChildDto;
import com.dripl.transaction.split.dto.UpdateSplitChildDto;
import com.dripl.transaction.split.dto.UpdateTransactionSplitDto;
import com.dripl.transaction.split.entity.TransactionSplit;
import com.dripl.transaction.split.repository.TransactionSplitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSplitService {

    private final TransactionSplitRepository transactionSplitRepository;
    private final TransactionRepository transactionRepository;
    private final MerchantService merchantService;
    private final CategoryService categoryService;
    private final TagService tagService;

    @Transactional(readOnly = true)
    public List<TransactionSplit> listAllByWorkspaceId(UUID workspaceId) {
        return transactionSplitRepository.findAllByWorkspaceId(workspaceId);
    }

    @Transactional(readOnly = true)
    public TransactionSplit getTransactionSplit(UUID splitId, UUID workspaceId) {
        return transactionSplitRepository.findByIdAndWorkspaceId(splitId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction split not found"));
    }

    @Transactional(readOnly = true)
    public List<Transaction> getSplitTransactions(UUID splitId, UUID workspaceId) {
        return transactionRepository.findAllBySplitIdAndWorkspaceId(splitId, workspaceId);
    }

    @Transactional
    public TransactionSplit createTransactionSplit(UUID workspaceId, CreateTransactionSplitDto dto) {
        // Validate source transaction
        Transaction source = transactionRepository.findByIdAndWorkspaceId(dto.getTransactionId(), workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Source transaction not found"));

        if (source.getGroupId() != null) {
            throw new BadRequestException("Transaction is in a group. Remove it from the group before splitting.");
        }
        if (source.getSplitId() != null) {
            throw new BadRequestException("Transaction is already part of a split.");
        }

        // Validate amounts sum to source
        validateAmountSum(dto.getChildren().stream().map(SplitChildDto::getAmount).toList(), source.getAmount());
        validateChildAmountSigns(dto.getChildren().stream().map(SplitChildDto::getAmount).toList(), source.getAmount());

        // Create a split entity from a source
        TransactionSplit split = TransactionSplit.builder()
                .workspaceId(workspaceId)
                .accountId(source.getAccountId())
                .totalAmount(source.getAmount())
                .currencyCode(source.getCurrencyCode())
                .date(source.getDate())
                .build();
        split = transactionSplitRepository.save(split);

        // Create child transactions
        for (SplitChildDto child : dto.getChildren()) {
            createChildTransaction(split, source, child, workspaceId);
        }

        // Delete the source transaction
        transactionRepository.delete(source);

        log.info("Created transaction split from transaction {} with {} children", dto.getTransactionId(), dto.getChildren().size());
        return split;
    }

    @Transactional
    public TransactionSplit updateTransactionSplit(UUID splitId, UUID workspaceId, UpdateTransactionSplitDto dto) {
        TransactionSplit split = getTransactionSplit(splitId, workspaceId);

        // Validate amounts sum to totalAmount
        validateAmountSum(dto.getChildren().stream().map(UpdateSplitChildDto::getAmount).toList(), split.getTotalAmount());
        validateChildAmountSigns(dto.getChildren().stream().map(UpdateSplitChildDto::getAmount).toList(), split.getTotalAmount());

        List<Transaction> currentChildren = transactionRepository.findAllBySplitIdAndWorkspaceId(splitId, workspaceId);
        Set<UUID> currentIds = currentChildren.stream().map(Transaction::getId).collect(Collectors.toSet());

        // Determine which children are being kept (have id), and which are new (no id)
        Set<UUID> desiredIds = dto.getChildren().stream()
                .map(UpdateSplitChildDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Delete children not in the desired set
        Set<UUID> toDelete = new HashSet<>(currentIds);
        toDelete.removeAll(desiredIds);
        for (UUID txnId : toDelete) {
            Transaction txn = transactionRepository.findByIdAndWorkspaceId(txnId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + txnId));
            transactionRepository.delete(txn);
        }

        // Update existing children and create new ones
        for (UpdateSplitChildDto child : dto.getChildren()) {
            if (child.getId() != null) {
                // Update existing
                if (!currentIds.contains(child.getId())) {
                    throw new BadRequestException("Transaction " + child.getId() + " is not part of this split");
                }
                Transaction txn = transactionRepository.findByIdAndWorkspaceId(child.getId(), workspaceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + child.getId()));
                txn.setAmount(child.getAmount());
                if (child.getMerchantName() != null) {
                    txn.setMerchantId(merchantService.resolveMerchant(child.getMerchantName(), workspaceId).getId());
                }
                if (child.getCategoryId() != null) {
                    categoryService.getCategory(child.getCategoryId(), workspaceId);
                    categoryService.validateCategoryPolarity(child.getCategoryId(), child.getAmount(), workspaceId);
                    txn.setCategoryId(child.getCategoryId());
                }
                if (child.getTagIds() != null) {
                    child.getTagIds().forEach(tagId -> tagService.getTag(tagId, workspaceId));
                    txn.setTagIds(new HashSet<>(child.getTagIds()));
                }
                if (child.getNotes() != null) {
                    txn.setNotes(child.getNotes());
                }
                transactionRepository.save(txn);
            } else {
                // Create a new child
                createChildFromUpdate(split, child, workspaceId);
            }
        }

        log.info("Updated transaction split {}", splitId);
        return split;
    }

    @Transactional
    public void deleteTransactionSplit(UUID splitId, UUID workspaceId) {
        TransactionSplit split = getTransactionSplit(splitId, workspaceId);
        transactionRepository.clearSplitId(splitId);
        transactionSplitRepository.delete(split);
        log.info("Dissolved transaction split {}", splitId);
    }

    private void createChildTransaction(TransactionSplit split, Transaction source, SplitChildDto child, UUID workspaceId) {
        UUID merchantId = child.getMerchantName() != null
                ? merchantService.resolveMerchant(child.getMerchantName(), workspaceId).getId()
                : source.getMerchantId();

        UUID categoryId = child.getCategoryId();
        if (categoryId != null) {
            categoryService.getCategory(categoryId, workspaceId);
            categoryService.validateCategoryPolarity(categoryId, child.getAmount(), workspaceId);
        }

        Set<UUID> tagIds = child.getTagIds() != null ? new HashSet<>(child.getTagIds()) : new HashSet<>();
        tagIds.forEach(tagId -> tagService.getTag(tagId, workspaceId));

        Transaction txn = Transaction.builder()
                .workspaceId(workspaceId)
                .accountId(split.getAccountId())
                .merchantId(merchantId)
                .splitId(split.getId())
                .categoryId(categoryId)
                .date(split.getDate())
                .amount(child.getAmount())
                .currencyCode(split.getCurrencyCode())
                .notes(child.getNotes())
                .status(source.getStatus())
                .source(TransactionSource.MANUAL)
                .pendingAt(source.getPendingAt())
                .postedAt(source.getPostedAt())
                .tagIds(tagIds)
                .build();

        transactionRepository.save(txn);
    }

    private void createChildFromUpdate(TransactionSplit split, UpdateSplitChildDto child, UUID workspaceId) {
        UUID merchantId = child.getMerchantName() != null
                ? merchantService.resolveMerchant(child.getMerchantName(), workspaceId).getId()
                : null;

        if (merchantId == null) {
            throw new BadRequestException("Merchant name must be provided for new split children");
        }

        UUID categoryId = child.getCategoryId();
        if (categoryId != null) {
            categoryService.getCategory(categoryId, workspaceId);
            categoryService.validateCategoryPolarity(categoryId, child.getAmount(), workspaceId);
        }

        Set<UUID> tagIds = child.getTagIds() != null ? new HashSet<>(child.getTagIds()) : new HashSet<>();
        tagIds.forEach(tagId -> tagService.getTag(tagId, workspaceId));

        Transaction txn = Transaction.builder()
                .workspaceId(workspaceId)
                .accountId(split.getAccountId())
                .merchantId(merchantId)
                .splitId(split.getId())
                .categoryId(categoryId)
                .date(split.getDate())
                .amount(child.getAmount())
                .currencyCode(split.getCurrencyCode())
                .notes(child.getNotes())
                .status(com.dripl.transaction.enums.TransactionStatus.PENDING)
                .source(TransactionSource.MANUAL)
                .pendingAt(java.time.LocalDateTime.now())
                .tagIds(tagIds)
                .build();

        transactionRepository.save(txn);
    }

    private void validateAmountSum(List<BigDecimal> childAmounts, BigDecimal totalAmount) {
        BigDecimal sum = childAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(totalAmount) != 0) {
            throw new BadRequestException(
                    "Child amounts sum to " + sum + " but must equal " + totalAmount);
        }
    }

    private void validateChildAmountSigns(List<BigDecimal> childAmounts, BigDecimal totalAmount) {
        boolean sourcePositive = totalAmount.compareTo(BigDecimal.ZERO) > 0;
        for (BigDecimal amount : childAmounts) {
            boolean childPositive = amount.compareTo(BigDecimal.ZERO) > 0;
            if (childPositive != sourcePositive) {
                String expected = sourcePositive ? "positive" : "negative";
                throw new BadRequestException(
                        "All child amounts must be " + expected + " to match the source transaction sign, but got " + amount);
            }
        }
    }
}
