package com.dripl.category.entity;

import com.dripl.common.audit.BaseEntity;
import com.dripl.common.enums.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "categories")
public class Category extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "is_income", nullable = false)
    private boolean income;

    @Column(name = "exclude_from_budget", nullable = false)
    private boolean excludeFromBudget;

    @Column(name = "exclude_from_totals", nullable = false)
    private boolean excludeFromTotals;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
