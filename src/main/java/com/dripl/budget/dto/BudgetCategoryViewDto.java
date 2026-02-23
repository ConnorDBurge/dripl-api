package com.dripl.budget.dto;

import com.dripl.budget.enums.RolloverType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetCategoryViewDto {

    private UUID categoryId;
    private String name;
    private UUID parentId;
    private int displayOrder;
    private BigDecimal expected;
    private BigDecimal recurringExpected;
    private BigDecimal activity;
    private BigDecimal available;
    private BigDecimal rolledOver;
    private RolloverType rolloverType;

    @Builder.Default
    private List<BudgetCategoryViewDto> children = new ArrayList<>();
}
