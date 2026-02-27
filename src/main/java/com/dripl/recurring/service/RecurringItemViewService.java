package com.dripl.recurring.service;

import com.dripl.common.exception.BadRequestException;
import com.dripl.recurring.dto.RecurringItemMonthViewDto;
import com.dripl.recurring.dto.RecurringItemViewDto;
import com.dripl.recurring.dto.RecurringOccurrenceDto;
import com.dripl.recurring.dto.OccurrenceTransactionDto;
import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.entity.RecurringItemOverride;
import com.dripl.recurring.enums.RecurringItemStatus;
import com.dripl.recurring.repository.RecurringItemOverrideRepository;
import com.dripl.recurring.repository.RecurringItemRepository;
import com.dripl.recurring.util.RecurringOccurrenceCalculator;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class RecurringItemViewService {

    private final RecurringItemRepository recurringItemRepository;
    private final RecurringItemOverrideRepository overrideRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public RecurringItemMonthViewDto getMonthView(UUID workspaceId, YearMonth month, Integer periodOffset) {
        if (month != null && periodOffset != null) {
            throw new BadRequestException("Cannot specify both 'month' and 'periodOffset'");
        }

        YearMonth target;
        if (month != null) {
            target = month;
        } else if (periodOffset != null) {
            target = YearMonth.now().plusMonths(periodOffset);
        } else {
            target = YearMonth.now();
        }

        return buildMonthView(workspaceId, target);
    }

    private RecurringItemMonthViewDto buildMonthView(UUID workspaceId, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        List<RecurringItem> allItems = recurringItemRepository.findAllByWorkspaceId(workspaceId);

        // Batch-load overrides for this month
        Map<String, RecurringItemOverride> overrideMap = overrideRepository
                .findByWorkspaceIdAndOccurrenceDateBetween(workspaceId, monthStart, monthEnd)
                .stream()
                .collect(Collectors.toMap(
                        o -> o.getRecurringItemId() + ":" + o.getOccurrenceDate(),
                        o -> o));

        // Batch-load linked transactions by occurrenceDate, keyed by recurringItemId:occurrenceDate
        Map<String, Transaction> txnByOccurrence = transactionRepository
                .findLinkedToRecurringItemsInDateRange(workspaceId, monthStart, monthEnd)
                .stream()
                .collect(Collectors.toMap(
                        t -> t.getRecurringItemId() + ":" + t.getOccurrenceDate(),
                        t -> t,
                        (a, b) -> a));

        List<RecurringItemViewDto> viewItems = new ArrayList<>();
        BigDecimal expectedExpenses = BigDecimal.ZERO;
        BigDecimal expectedIncome = BigDecimal.ZERO;
        int totalOccurrences = 0;

        for (RecurringItem ri : allItems) {
            if (ri.getStatus() != RecurringItemStatus.ACTIVE) continue;

            List<LocalDate> dates = RecurringOccurrenceCalculator.computeOccurrences(ri, monthStart, monthEnd);

            BigDecimal itemTotal = BigDecimal.ZERO;
            List<RecurringOccurrenceDto> occurrences = new ArrayList<>();

            for (LocalDate date : dates) {
                String key = ri.getId() + ":" + date;
                RecurringItemOverride override = overrideMap.get(key);
                Transaction linkedTxn = txnByOccurrence.get(key);

                // Expected amount: override amount > default amount
                BigDecimal expectedAmount = (override != null && override.getAmount() != null)
                        ? override.getAmount() : ri.getAmount();

                // Build transaction summary if linked
                OccurrenceTransactionDto txnDto = null;
                if (linkedTxn != null) {
                    txnDto = OccurrenceTransactionDto.builder()
                            .id(linkedTxn.getId())
                            .date(linkedTxn.getDate().toLocalDate())
                            .amount(linkedTxn.getAmount())
                            .build();
                }

                occurrences.add(RecurringOccurrenceDto.builder()
                        .date(date)
                        .expectedAmount(expectedAmount)
                        .overrideId(override != null ? override.getId() : null)
                        .notes(override != null ? override.getNotes() : null)
                        .transaction(txnDto)
                        .build());

                itemTotal = itemTotal.add(expectedAmount);
            }

            if (occurrences.isEmpty()) continue;

            occurrences.sort(Comparator.comparing(RecurringOccurrenceDto::getDate));

            viewItems.add(RecurringItemViewDto.builder()
                    .recurringItemId(ri.getId())
                    .description(ri.getDescription())
                    .merchantId(ri.getMerchantId())
                    .accountId(ri.getAccountId())
                    .categoryId(ri.getCategoryId())
                    .amount(ri.getAmount())
                    .currencyCode(ri.getCurrencyCode())
                    .status(ri.getStatus())
                    .frequencyGranularity(ri.getFrequencyGranularity())
                    .frequencyQuantity(ri.getFrequencyQuantity())
                    .occurrences(occurrences)
                    .totalExpected(itemTotal)
                    .build());

            if (ri.getAmount().signum() < 0) {
                expectedExpenses = expectedExpenses.add(itemTotal);
            } else {
                expectedIncome = expectedIncome.add(itemTotal);
            }
            totalOccurrences += occurrences.size();
        }

        viewItems.sort(Comparator.comparing(v -> v.getOccurrences().getFirst().getDate()));

        return RecurringItemMonthViewDto.builder()
                .monthStart(monthStart)
                .monthEnd(monthEnd)
                .items(viewItems)
                .expectedExpenses(expectedExpenses)
                .expectedIncome(expectedIncome)
                .itemCount(viewItems.size())
                .occurrenceCount(totalOccurrences)
                .build();
    }
}
