package com.dripl.budget.entity;

import com.dripl.budget.dto.BudgetCategoryConfigDto;
import com.dripl.budget.enums.RolloverType;
import com.dripl.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "budget_category_configs")
@EqualsAndHashCode(callSuper = true)
public class BudgetCategoryConfig extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "budget_id")
    private UUID budgetId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "rollover_type", nullable = false)
    private RolloverType rolloverType = RolloverType.NONE;

    public BudgetCategoryConfigDto toDto() {
        return BudgetCategoryConfigDto.builder()
                .id(getId())
                .categoryId(categoryId)
                .rolloverType(rolloverType)
                .build();
    }
}
