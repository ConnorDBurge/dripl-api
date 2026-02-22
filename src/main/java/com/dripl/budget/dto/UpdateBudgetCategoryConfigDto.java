package com.dripl.budget.dto;

import com.dripl.budget.enums.RolloverType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateBudgetCategoryConfigDto {

    @NotNull(message = "Rollover type is required")
    private RolloverType rolloverType;
}
