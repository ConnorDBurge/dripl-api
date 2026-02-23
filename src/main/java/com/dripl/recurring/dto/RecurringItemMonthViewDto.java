package com.dripl.recurring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringItemMonthViewDto {

    private LocalDate monthStart;
    private LocalDate monthEnd;
    private BigDecimal expectedExpenses;
    private BigDecimal expectedIncome;
    private int occurrenceCount;
    private int itemCount;
    private List<RecurringItemViewDto> items;
}
