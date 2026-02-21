package com.dripl.transaction.controller;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.annotation.WorkspaceId;
import com.dripl.common.dto.PagedResponse;
import com.dripl.transaction.dto.CreateTransactionDto;
import com.dripl.transaction.dto.TransactionDto;
import com.dripl.transaction.dto.UpdateTransactionDto;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.enums.TransactionSource;
import com.dripl.transaction.enums.TransactionStatus;
import com.dripl.transaction.event.dto.TransactionEventDto;
import com.dripl.transaction.event.service.TransactionEventService;
import com.dripl.transaction.mapper.TransactionMapper;
import com.dripl.transaction.repository.TransactionSpecifications;
import com.dripl.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.dripl.transaction.repository.TransactionSpecifications.optionally;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 250;

    private final TransactionService transactionService;
    private final TransactionEventService transactionEventService;
    private final TransactionMapper transactionMapper;

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedResponse<TransactionDto>> listTransactions(
            @WorkspaceId UUID workspaceId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID splitId,
            @RequestParam(required = false) UUID recurringItemId,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionSource source,
            @RequestParam(required = false) CurrencyCode currencyCode,
            @RequestParam(required = false) Set<UUID> tagIds,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection) {

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : null;

        Specification<Transaction> spec = Specification
                .where(TransactionSpecifications.inWorkspace(workspaceId))
                .and(optionally(accountId,          TransactionSpecifications::hasAccount))
                .and(optionally(merchantId,         TransactionSpecifications::hasMerchant))
                .and(optionally(groupId,            TransactionSpecifications::hasGroup))
                .and(optionally(splitId,            TransactionSpecifications::hasSplit))
                .and(optionally(categoryId,         TransactionSpecifications::hasCategory))
                .and(optionally(recurringItemId,    TransactionSpecifications::hasRecurringItem))
                .and(optionally(status,             TransactionSpecifications::hasStatus))
                .and(optionally(source,             TransactionSpecifications::hasSource))
                .and(optionally(currencyCode,       TransactionSpecifications::hasCurrency))
                .and(optionally(tagIds,             TransactionSpecifications::hasAnyTag))
                .and(optionally(search,             TransactionSpecifications::searchText))
                .and(optionally(minAmount,          TransactionSpecifications::amountGreaterThanOrEqual))
                .and(optionally(maxAmount,          TransactionSpecifications::amountLessThanOrEqual))
                .and(optionally(start,              TransactionSpecifications::dateOnOrAfter))
                .and(optionally(end,                TransactionSpecifications::dateOnOrBefore));

        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable;
        if (TransactionSpecifications.isRelatedSort(sortBy)) {
            spec = spec.and(TransactionSpecifications.sortByRelatedName(sortBy, sortDirection));
            pageable = PageRequest.of(page, clampedSize);
        } else {
            pageable = PageRequest.of(page, clampedSize, TransactionSpecifications.buildSort(sortBy, sortDirection));
        }

        Page<TransactionDto> result = transactionService.listAll(spec, pageable)
                .map(transactionMapper::toDto);

        return ResponseEntity.ok(PagedResponse.from(result));
    }

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/{transactionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionDto> getTransaction(
            @WorkspaceId UUID workspaceId, @PathVariable UUID transactionId) {
        Transaction transaction = transactionService.getTransaction(transactionId, workspaceId);
        return ResponseEntity.ok(transactionMapper.toDto(transaction));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionDto> createTransaction(
            @WorkspaceId UUID workspaceId, @Valid @RequestBody CreateTransactionDto dto) {
        Transaction transaction = transactionService.createTransaction(workspaceId, dto);
        return ResponseEntity.status(201).body(transactionMapper.toDto(transaction));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(value = "/{transactionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionDto> updateTransaction(
            @WorkspaceId UUID workspaceId, @PathVariable UUID transactionId,
            @Valid @RequestBody UpdateTransactionDto dto) {
        Transaction transaction = transactionService.updateTransaction(transactionId, workspaceId, dto);
        return ResponseEntity.ok(transactionMapper.toDto(transaction));
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @DeleteMapping("/{transactionId}")
    public ResponseEntity<Void> deleteTransaction(
            @WorkspaceId UUID workspaceId, @PathVariable UUID transactionId) {
        transactionService.deleteTransaction(transactionId, workspaceId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/{transactionId}/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TransactionEventDto>> getTransactionEvents(
            @WorkspaceId UUID workspaceId, @PathVariable UUID transactionId) {
        transactionService.getTransaction(transactionId, workspaceId);
        return ResponseEntity.ok(transactionEventService.getEventsForTransaction(transactionId, workspaceId));
    }
}
