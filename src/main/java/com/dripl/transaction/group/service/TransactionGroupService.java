package com.dripl.transaction.group.service;

import com.dripl.category.service.CategoryService;
import com.dripl.common.event.DomainEventPublisher;
import com.dripl.common.event.FieldChange;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.tag.service.TagService;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.enums.TransactionAction;
import com.dripl.transaction.group.dto.CreateTransactionGroupDto;
import com.dripl.transaction.group.dto.UpdateTransactionGroupDto;
import com.dripl.transaction.group.entity.TransactionGroup;
import com.dripl.transaction.group.repository.TransactionGroupRepository;
import com.dripl.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionGroupService {

    private final TransactionGroupRepository transactionGroupRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional(readOnly = true)
    public List<TransactionGroup> listAllByWorkspaceId(UUID workspaceId) {
        return transactionGroupRepository.findAllByWorkspaceId(workspaceId);
    }

    @Transactional(readOnly = true)
    public TransactionGroup getTransactionGroup(UUID groupId, UUID workspaceId) {
        return transactionGroupRepository.findByIdAndWorkspaceId(groupId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction group not found"));
    }

    @Transactional(readOnly = true)
    public List<Transaction> getGroupTransactions(UUID groupId, UUID workspaceId) {
        return transactionRepository.findAllByGroupIdAndWorkspaceId(groupId, workspaceId);
    }

    @Transactional
    public TransactionGroup createTransactionGroup(UUID workspaceId, CreateTransactionGroupDto dto) {
        UUID categoryId = null;
        if (dto.getCategoryId() != null) {
            categoryId = categoryService.getCategory(dto.getCategoryId(), workspaceId).getId();
        }

        Set<UUID> tagIds = dto.getTagIds() != null ? dto.getTagIds() : new HashSet<>();
        tagIds.forEach(tagId -> tagService.getTag(tagId, workspaceId));

        // Validate all transactions exist in the workspace and are not yet grouped
        Set<UUID> transactionIds = dto.getTransactionIds();
        validateTransactionsForGrouping(transactionIds, workspaceId);

        // Validate category polarity against all member transactions
        if (categoryId != null) {
            for (UUID txnId : transactionIds) {
                Transaction txn = transactionRepository.findByIdAndWorkspaceId(txnId, workspaceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + txnId));
                categoryService.validateCategoryPolarity(categoryId, txn.getAmount(), workspaceId);
            }
        }

        TransactionGroup group = TransactionGroup.builder()
                .workspaceId(workspaceId)
                .name(dto.getName())
                .categoryId(categoryId)
                .notes(dto.getNotes())
                .tagIds(tagIds)
                .build();

        group = transactionGroupRepository.save(group);

        transactionRepository.setGroupId(group.getId(), transactionIds, workspaceId);
        applyGroupOverrides(group, transactionIds, workspaceId);

        for (UUID txnId : transactionIds) {
            publishGroupedEvent(txnId, workspaceId, group.getId());
        }

        log.info("Created transaction group '{}' with {} transactions", group.getName(), transactionIds.size());
        return group;
    }

    @Transactional
    public TransactionGroup updateTransactionGroup(UUID groupId, UUID workspaceId, UpdateTransactionGroupDto dto) {
        TransactionGroup group = getTransactionGroup(groupId, workspaceId);
        // Eagerly initialize tagIds before JPQL clears persistence context
        if (group.getTagIds() != null) {
            group.getTagIds().size();
        }

        if (dto.getName() != null) {
            group.setName(dto.getName());
        }

        if (dto.isCategoryIdSpecified()) {
            if (dto.getCategoryId() != null) {
                categoryService.getCategory(dto.getCategoryId(), workspaceId);
                group.setCategoryId(dto.getCategoryId());
            } else {
                group.setCategoryId(null);
            }
        }

        if (dto.isNotesSpecified()) {
            group.setNotes(dto.getNotes());
        }

        if (dto.isTagIdsSpecified()) {
            Set<UUID> tagIds = dto.getTagIds() != null ? dto.getTagIds() : new HashSet<>();
            tagIds.forEach(tagId -> tagService.getTag(tagId, workspaceId));
            group.setTagIds(tagIds);
        }

        log.info("Updating transaction group '{}'", group.getName());
        group = transactionGroupRepository.save(group);

        // Reconcile transaction membership if transactionIds provided
        if (dto.getTransactionIds() != null) {
            Set<UUID> desired = dto.getTransactionIds();
            if (desired.size() < 2) {
                throw new BadRequestException("Transaction group must contain at least 2 transactions");
            }

            Set<UUID> current = transactionRepository.findAllByGroupIdAndWorkspaceId(groupId, workspaceId)
                    .stream().map(Transaction::getId).collect(Collectors.toSet());

            // Remove transactions no longer in the set
            Set<UUID> toRemove = new HashSet<>(current);
            toRemove.removeAll(desired);
            for (UUID txnId : toRemove) {
                Transaction txn = transactionRepository.findByIdAndWorkspaceId(txnId, workspaceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + txnId));
                txn.setGroupId(null);
                transactionRepository.save(txn);
                publishUngroupedEvent(txnId, workspaceId, groupId);
            }

            // Add new transactions
            Set<UUID> toAdd = new HashSet<>(desired);
            toAdd.removeAll(current);
            if (!toAdd.isEmpty()) {
                validateTransactionsForGrouping(toAdd, workspaceId);
                transactionRepository.setGroupId(groupId, toAdd, workspaceId);
                for (UUID txnId : toAdd) {
                    publishGroupedEvent(txnId, workspaceId, groupId);
                }
            }
        }

        // Validate category polarity against all member transactions after membership reconciliation
        List<Transaction> members = transactionRepository.findAllByGroupIdAndWorkspaceId(groupId, workspaceId);
        if (group.getCategoryId() != null) {
            for (Transaction txn : members) {
                categoryService.validateCategoryPolarity(group.getCategoryId(), txn.getAmount(), workspaceId);
            }
        }

        // Push overrides to all current member transactions
        for (Transaction txn : members) {
            if (group.getCategoryId() != null) {
                txn.setCategoryId(group.getCategoryId());
            }
            if (group.getNotes() != null) {
                txn.setNotes(group.getNotes());
            }
            if (group.getTagIds() != null && !group.getTagIds().isEmpty()) {
                txn.setTagIds(new HashSet<>(group.getTagIds()));
            }
            transactionRepository.save(txn);
        }

        return group;
    }

    @Transactional
    public void deleteTransactionGroup(UUID groupId, UUID workspaceId) {
        TransactionGroup group = getTransactionGroup(groupId, workspaceId);
        List<Transaction> members = transactionRepository.findAllByGroupIdAndWorkspaceId(groupId, workspaceId);
        transactionRepository.clearGroupId(groupId);
        transactionGroupRepository.delete(group);
        for (Transaction txn : members) {
            publishUngroupedEvent(txn.getId(), workspaceId, groupId);
        }
        log.info("Dissolved transaction group '{}'", group.getName());
    }

    private void validateTransactionsForGrouping(Set<UUID> transactionIds, UUID workspaceId) {
        for (UUID txnId : transactionIds) {
            Transaction txn = transactionRepository.findByIdAndWorkspaceId(txnId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + txnId));
            if (txn.getGroupId() != null) {
                throw new BadRequestException("Transaction " + txnId + " is already in a group");
            }
            if (txn.getRecurringItemId() != null) {
                throw new BadRequestException(
                        "Transaction " + txnId + " is linked to recurring item " + txn.getRecurringItemId() + ". Unlink it before adding to a group.");
            }
            if (txn.getSplitId() != null) {
                throw new BadRequestException(
                        "Transaction " + txnId + " is part of a split. Remove it from the split before adding to a group.");
            }
        }
    }

    private void applyGroupOverrides(TransactionGroup group, Set<UUID> transactionIds, UUID workspaceId) {
        UUID categoryId = group.getCategoryId();
        String notes = group.getNotes();
        Set<UUID> tagIds = group.getTagIds() != null ? new HashSet<>(group.getTagIds()) : Set.of();

        if (categoryId == null && notes == null && tagIds.isEmpty()) {
            return;
        }
        for (UUID txnId : transactionIds) {
            Transaction txn = transactionRepository.findByIdAndWorkspaceId(txnId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + txnId));
            if (categoryId != null) {
                txn.setCategoryId(categoryId);
            }
            if (notes != null) {
                txn.setNotes(notes);
            }
            if (!tagIds.isEmpty()) {
                txn.setTagIds(new HashSet<>(tagIds));
            }
            transactionRepository.save(txn);
        }
    }

    private void publishGroupedEvent(UUID transactionId, UUID workspaceId, UUID groupId) {
        domainEventPublisher.publish(TransactionAction.GROUPED, transactionId, workspaceId,
                List.of(new FieldChange("groupId", null, groupId.toString())));
    }

    private void publishUngroupedEvent(UUID transactionId, UUID workspaceId, UUID groupId) {
        domainEventPublisher.publish(TransactionAction.UNGROUPED, transactionId, workspaceId,
                List.of(new FieldChange("groupId", groupId.toString(), null)));
    }
}
