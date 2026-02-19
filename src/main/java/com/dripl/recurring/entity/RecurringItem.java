package com.dripl.recurring.entity;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.audit.BaseEntity;
import com.dripl.recurring.enums.FrequencyGranularity;
import com.dripl.recurring.enums.RecurringItemStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "recurring_items")
public class RecurringItem extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", nullable = false)
    private CurrencyCode currencyCode = CurrencyCode.USD;

    @Column(name = "notes", length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_granularity", nullable = false)
    private FrequencyGranularity frequencyGranularity;

    @Builder.Default
    @Column(name = "frequency_quantity", nullable = false)
    private Integer frequencyQuantity = 1;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "recurring_item_anchor_dates", joinColumns = @JoinColumn(name = "recurring_item_id"))
    @Column(name = "anchor_date")
    @OrderColumn
    private List<LocalDateTime> anchorDates = new ArrayList<>();

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecurringItemStatus status = RecurringItemStatus.ACTIVE;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "recurring_item_tags", joinColumns = @JoinColumn(name = "recurring_item_id"))
    @Column(name = "tag_id")
    private Set<UUID> tagIds = new HashSet<>();
}
