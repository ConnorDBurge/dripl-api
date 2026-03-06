package com.dripl.budget.dto;

import com.dripl.budget.enums.RolloverType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateBudgetCategoryConfigInput {

    private RolloverType rolloverType;
}
