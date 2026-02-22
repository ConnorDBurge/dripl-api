package com.dripl.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetSectionDto {

    private BigDecimal expected;
    private BigDecimal activity;
    private BigDecimal available;

    @Builder.Default
    private List<BudgetCategoryViewDto> categories = new ArrayList<>();
}
