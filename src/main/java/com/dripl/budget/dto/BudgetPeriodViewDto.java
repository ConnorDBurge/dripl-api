package com.dripl.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetPeriodViewDto {

    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal budgetable;
    private BigDecimal totalBudgeted;
    private BigDecimal leftToBudget;
    private BigDecimal netTotalAvailable;
    private BigDecimal recurringExpected;
    private BigDecimal availablePool;
    private BigDecimal totalRolledOver;
    private BudgetSectionDto inflow;
    private BudgetSectionDto outflow;
}
