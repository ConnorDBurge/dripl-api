package com.dripl.recurring.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetOccurrenceOverrideDto {

    @NotNull
    private LocalDate occurrenceDate;
    private BigDecimal amount;
    private String notes;
}
