package com.dripl.budget.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateBudgetDto {

    @Size(max = 255)
    private String name;

    private Integer anchorDay1;
    private Integer anchorDay2;
    private Integer intervalDays;
    private LocalDate anchorDate;

    private List<UUID> accountIds;
}
