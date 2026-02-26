package com.dripl.recurring.service;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.account.service.AccountService;
import com.dripl.category.service.CategoryService;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.merchant.service.MerchantService;
import com.dripl.recurring.dto.CreateRecurringItemDto;
import com.dripl.recurring.dto.SetOccurrenceOverrideDto;
import com.dripl.recurring.dto.UpdateOccurrenceOverrideDto;
import com.dripl.recurring.dto.UpdateRecurringItemDto;
import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.entity.RecurringItemOverride;
import com.dripl.recurring.enums.RecurringItemStatus;
import com.dripl.recurring.mapper.RecurringItemMapper;
import com.dripl.recurring.repository.RecurringItemOverrideRepository;
import com.dripl.recurring.repository.RecurringItemRepository;
import com.dripl.recurring.util.RecurringOccurrenceCalculator;
import com.dripl.tag.service.TagService;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringItemService {

    private final RecurringItemRepository recurringItemRepository;
    private final RecurringItemOverrideRepository overrideRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final MerchantService merchantService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final RecurringItemMapper recurringItemMapper;

    @Transactional(readOnly = true)
    public List<RecurringItem> listAllByWorkspaceId(UUID workspaceId) {
        return recurringItemRepository.findAllByWorkspaceId(workspaceId);
    }

    @Transactional(readOnly = true)
    public RecurringItem getRecurringItem(UUID recurringItemId, UUID workspaceId) {
        return recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurring item not found"));
    }

    @Transactional
    public RecurringItem createRecurringItem(UUID workspaceId, CreateRecurringItemDto dto) {
        var account = accountService.getAccount(dto.getAccountId(), workspaceId);
        var merchant = merchantService.resolveMerchant(dto.getMerchantName(), workspaceId);
        UUID categoryId = null;
        if (dto.getCategoryId() != null) {
            categoryId = categoryService.getCategory(dto.getCategoryId(), workspaceId).getId();
            categoryService.validateNotGroup(categoryId);
            categoryService.validateCategoryPolarity(categoryId, dto.getAmount(), workspaceId);
        }
        Set<UUID> tagIds = dto.getTagIds() != null ? dto.getTagIds() : new HashSet<>();
        tagIds.forEach(tagId -> tagService.getTag(tagId, workspaceId));

        RecurringItem recurringItem = RecurringItem.builder()
                .workspaceId(workspaceId)
                .merchantId(merchant.getId())
                .accountId(account.getId())
                .categoryId(categoryId)
                .amount(dto.getAmount())
                .currencyCode(dto.getCurrencyCode() != null ? dto.getCurrencyCode() : CurrencyCode.USD)
                .description(dto.getDescription())
                .notes(dto.getNotes())
                .frequencyGranularity(dto.getFrequencyGranularity())
                .frequencyQuantity(dto.getFrequencyQuantity() != null ? dto.getFrequencyQuantity() : 1)
                .anchorDates(dto.getAnchorDates())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .status(RecurringItemStatus.ACTIVE)
                .tagIds(tagIds)
                .build();

        log.info("Creating recurring item for merchant '{}'", merchant.getName());
        return recurringItemRepository.save(recurringItem);
    }

    @Transactional
    public RecurringItem updateRecurringItem(UUID recurringItemId, UUID workspaceId, UpdateRecurringItemDto dto) {
        RecurringItem recurringItem = getRecurringItem(recurringItemId, workspaceId);

        if (dto.getAccountId() != null) {
            var account = accountService.getAccount(dto.getAccountId(), workspaceId);
            recurringItem.setAccountId(account.getId());
        }

        // MapStruct handles: description, amount, currencyCode, frequencyGranularity, frequencyQuantity, anchorDates, startDate, status
        recurringItemMapper.updateEntity(dto, recurringItem);

        if (dto.getMerchantName() != null) {
            var merchant = merchantService.resolveMerchant(dto.getMerchantName(), workspaceId);
            recurringItem.setMerchantId(merchant.getId());
        }

        if (dto.isCategoryIdSpecified()) {
            if (dto.getCategoryId() != null) {
                var category = categoryService.getCategory(dto.getCategoryId(), workspaceId);
                categoryService.validateNotGroup(category.getId());
                categoryService.validateCategoryPolarity(category.getId(), recurringItem.getAmount(), workspaceId);
                recurringItem.setCategoryId(category.getId());
            } else {
                recurringItem.setCategoryId(null);
            }
        }

        // Re-validate polarity if the amount changed but the category stayed the same
        if (dto.getAmount() != null && !dto.isCategoryIdSpecified() && recurringItem.getCategoryId() != null) {
            categoryService.validateCategoryPolarity(recurringItem.getCategoryId(), recurringItem.getAmount(), workspaceId);
        }

        if (dto.isNotesSpecified()) {
            recurringItem.setNotes(dto.getNotes());
        }

        if (dto.isEndDateSpecified()) {
            recurringItem.setEndDate(dto.getEndDate());
        }

        if (dto.isTagIdsSpecified()) {
            Set<UUID> tagIds = dto.getTagIds() != null ? dto.getTagIds() : new HashSet<>();
            tagIds.forEach(tagId -> tagService.getTag(tagId, workspaceId));
            recurringItem.setTagIds(tagIds);
        }

        log.info("Updating recurring item {}", recurringItemId);
        RecurringItem saved = recurringItemRepository.save(recurringItem);

        // Clear all overrides if schedule-defining fields changed
        if (dto.getFrequencyGranularity() != null || dto.getFrequencyQuantity() != null
                || dto.getAnchorDates() != null || dto.getStartDate() != null || dto.isEndDateSpecified()) {
            overrideRepository.deleteByRecurringItemId(recurringItemId);
            log.info("Cleared all overrides for recurring item {} due to schedule change", recurringItemId);
        }

        // Cascade locked fields to all linked transactions
        List<Transaction> linked = transactionRepository.findAllByRecurringItemIdAndWorkspaceId(recurringItemId, workspaceId);
        for (Transaction txn : linked) {
            txn.setAccountId(saved.getAccountId());
            txn.setMerchantId(saved.getMerchantId());
            txn.setCategoryId(saved.getCategoryId());
            txn.setCurrencyCode(saved.getCurrencyCode());
            txn.setNotes(saved.getNotes());
            txn.setTagIds(saved.getTagIds() != null ? new HashSet<>(saved.getTagIds()) : new HashSet<>());
            transactionRepository.save(txn);
        }
        if (!linked.isEmpty()) {
            log.info("Cascaded recurring item changes to {} linked transactions", linked.size());
        }

        return saved;
    }

    @Transactional
    public void deleteRecurringItem(UUID recurringItemId, UUID workspaceId) {
        RecurringItem recurringItem = getRecurringItem(recurringItemId, workspaceId);
        log.info("Deleting recurring item {}", recurringItemId);
        recurringItemRepository.delete(recurringItem);
    }

    @Transactional
    public RecurringItemOverride createOverride(UUID recurringItemId, UUID workspaceId,
                                                SetOccurrenceOverrideDto dto) {
        RecurringItem ri = getRecurringItem(recurringItemId, workspaceId);
        LocalDate occurrenceDate = dto.getOccurrenceDate();

        // Validate the date is an actual occurrence
        List<LocalDate> occurrences = RecurringOccurrenceCalculator.computeOccurrences(
                ri, occurrenceDate, occurrenceDate);
        if (!occurrences.contains(occurrenceDate)) {
            throw new BadRequestException("Date %s is not a valid occurrence of this recurring item".formatted(occurrenceDate));
        }

        // Check for existing override on this occurrence
        overrideRepository.findByRecurringItemIdAndOccurrenceDate(recurringItemId, occurrenceDate)
                .ifPresent(existing -> {
                    throw new BadRequestException("Override already exists for occurrence %s. Use PUT to update.".formatted(occurrenceDate));
                });

        RecurringItemOverride override = RecurringItemOverride.builder()
                .workspaceId(workspaceId)
                .recurringItemId(recurringItemId)
                .occurrenceDate(occurrenceDate)
                .amount(dto.getAmount())
                .notes(dto.getNotes())
                .build();

        log.info("Created override for recurring item {} on {}: amount={}",
                recurringItemId, occurrenceDate, dto.getAmount());
        return overrideRepository.save(override);
    }

    @Transactional
    public RecurringItemOverride updateOverride(UUID overrideId, UUID workspaceId,
                                                UpdateOccurrenceOverrideDto dto) {
        RecurringItemOverride override = overrideRepository.findById(overrideId)
                .filter(o -> o.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new ResourceNotFoundException("Override not found"));

        override.setAmount(dto.getAmount());
        override.setNotes(dto.getNotes());

        log.info("Updated override {} for recurring item {} on {}: amount={}",
                overrideId, override.getRecurringItemId(), override.getOccurrenceDate(), dto.getAmount());
        return overrideRepository.save(override);
    }

    @Transactional
    public void deleteOverride(UUID overrideId, UUID workspaceId) {
        RecurringItemOverride override = overrideRepository.findById(overrideId)
                .filter(o -> o.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new ResourceNotFoundException("Override not found"));

        overrideRepository.delete(override);
        log.info("Deleted override {} for recurring item {} on {}",
                overrideId, override.getRecurringItemId(), override.getOccurrenceDate());
    }
}
