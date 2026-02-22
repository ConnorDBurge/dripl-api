package com.dripl.budget.entity;

import com.dripl.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "budget_period_entries")
@EqualsAndHashCode(callSuper = true)
public class BudgetPeriodEntry extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "budget_id")
    private UUID budgetId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Builder.Default
    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedAmount = BigDecimal.ZERO;
}
