package com.dripl.budget.dto;

import com.dripl.budget.enums.RolloverType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetCategoryConfigDto {

    private UUID id;
    private UUID categoryId;
    private RolloverType rolloverType;
}
