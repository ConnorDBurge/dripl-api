package com.dripl.recurring.service;

import com.dripl.common.exception.BadRequestException;
import com.dripl.recurring.dto.RecurringItemMonthViewDto;
import com.dripl.recurring.dto.RecurringItemViewDto;
import com.dripl.recurring.dto.RecurringOccurrenceDto;
import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.enums.RecurringItemStatus;
import com.dripl.recurring.repository.RecurringItemRepository;
import com.dripl.recurring.util.RecurringOccurrenceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class RecurringItemViewService {

    private final RecurringItemRepository recurringItemRepository;

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

        List<RecurringItemViewDto> viewItems = new ArrayList<>();
        BigDecimal expectedExpenses = BigDecimal.ZERO;
        BigDecimal expectedIncome = BigDecimal.ZERO;
        int totalOccurrences = 0;

        for (RecurringItem ri : allItems) {
            if (ri.getStatus() != RecurringItemStatus.ACTIVE) continue;

            List<LocalDate> dates = RecurringOccurrenceCalculator.computeOccurrences(ri, monthStart, monthEnd);
            if (dates.isEmpty()) continue;

            List<RecurringOccurrenceDto> occurrences = dates.stream()
                    .map(d -> RecurringOccurrenceDto.builder().date(d).amount(ri.getAmount()).build())
                    .toList();

            BigDecimal itemTotal = ri.getAmount().multiply(BigDecimal.valueOf(dates.size()));

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
            totalOccurrences += dates.size();
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
