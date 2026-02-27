package com.dripl.recurring.dto;

import com.dripl.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class RecurringItemOverrideDto extends BaseDto {

    private UUID recurringItemId;
    private LocalDate occurrenceDate;
    private BigDecimal amount;
    private String notes;
}
