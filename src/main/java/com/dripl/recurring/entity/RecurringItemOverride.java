package com.dripl.recurring.entity;

import com.dripl.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "recurring_item_overrides",
        uniqueConstraints = @UniqueConstraint(columnNames = {"recurring_item_id", "occurrence_date"}))
public class RecurringItemOverride extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "recurring_item_id", nullable = false)
    private UUID recurringItemId;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "notes", length = 500)
    private String notes;
}
