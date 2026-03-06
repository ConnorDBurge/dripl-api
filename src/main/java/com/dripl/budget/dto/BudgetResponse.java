package com.dripl.budget.dto;

import com.dripl.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class BudgetResponse extends BaseDto {
    private String name;
    private Integer anchorDay1;
    private Integer anchorDay2;
    private Integer intervalDays;
    private LocalDate anchorDate;
    private List<UUID> accountIds;
    private LocalDate currentPeriodStart;
    private LocalDate currentPeriodEnd;
}
