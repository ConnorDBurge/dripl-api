package com.dripl.budget.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetExpectedAmountDto {

    @DecimalMin(value = "0", message = "Expected amount must be >= 0")
    private BigDecimal expectedAmount;
}
