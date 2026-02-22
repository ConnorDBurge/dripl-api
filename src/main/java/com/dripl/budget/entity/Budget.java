package com.dripl.budget.entity;

import com.dripl.budget.dto.BudgetDto;
import com.dripl.budget.util.BudgetPeriodCalculator;
import com.dripl.budget.util.PeriodRange;
import com.dripl.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "budgets")
@EqualsAndHashCode(callSuper = true)
public class Budget extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "name", nullable = false)
    private String name;

    // Fixed-interval mode
    @Column(name = "interval_days")
    private Integer intervalDays;

    @Column(name = "anchor_date")
    private LocalDate anchorDate;

    // Anchor-in-month mode
    @Column(name = "anchor_day_1")
    private Integer anchorDay1;

    @Column(name = "anchor_day_2")
    private Integer anchorDay2;

    @Transient
    private List<UUID> accountIds = new ArrayList<>();

    public boolean isBudgetConfigured() {
        return anchorDate != null || anchorDay1 != null;
    }

    public boolean isFixedIntervalMode() {
        return anchorDate != null && intervalDays != null;
    }

    public boolean isAnchorInMonthMode() {
        return anchorDay1 != null;
    }

    public BudgetDto toDto() {
        LocalDate currentPeriodStart = null;
        LocalDate currentPeriodEnd = null;
        if (isBudgetConfigured()) {
            PeriodRange period = BudgetPeriodCalculator.computePeriod(this, LocalDate.now());
            currentPeriodStart = period.start();
            currentPeriodEnd = period.end();
        }

        return BudgetDto.builder()
                .id(getId())
                .name(name)
                .anchorDay1(anchorDay1)
                .anchorDay2(anchorDay2)
                .intervalDays(intervalDays)
                .anchorDate(anchorDate)
                .accountIds(accountIds != null ? accountIds : List.of())
                .currentPeriodStart(currentPeriodStart)
                .currentPeriodEnd(currentPeriodEnd)
                .build();
    }

    public static List<BudgetDto> toDtos(List<Budget> budgets) {
        return budgets.stream()
                .map(Budget::toDto)
                .toList();
    }
}
