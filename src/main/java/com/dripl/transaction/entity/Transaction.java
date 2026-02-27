package com.dripl.transaction.entity;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.audit.BaseEntity;
import com.dripl.common.event.Audited;
import com.dripl.common.event.WorkspaceScoped;
import com.dripl.transaction.enums.TransactionSource;
import com.dripl.transaction.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "transactions")
public class Transaction extends BaseEntity implements WorkspaceScoped {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Audited(displayName = "account")
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Audited(displayName = "merchant")
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Audited(displayName = "group")
    @Column(name = "group_id")
    private UUID groupId;

    @Audited(displayName = "split")
    @Column(name = "split_id")
    private UUID splitId;

    @Audited(displayName = "category")
    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "date", nullable = false)
    private LocalDateTime date;

    @Audited
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Audited(displayName = "currency")
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", nullable = false)
    private CurrencyCode currencyCode = CurrencyCode.USD;

    @Audited
    @Column(name = "notes", length = 500)
    private String notes;

    @Audited
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private TransactionSource source = TransactionSource.MANUAL;

    @Column(name = "pending_at")
    private LocalDateTime pendingAt;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Audited(displayName = "recurringItem")
    @Column(name = "recurring_item_id")
    private UUID recurringItemId;

    @Column(name = "occurrence_date")
    private LocalDate occurrenceDate;

    @Audited(displayName = "tags")
    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "transaction_tags", joinColumns = @JoinColumn(name = "transaction_id"))
    @Column(name = "tag_id")
    private Set<UUID> tagIds = new HashSet<>();

    /**
     * Creates a detached shallow copy for event diffing.
     */
    public Transaction snapshot() {
        return Transaction.builder()
                .accountId(accountId)
                .merchantId(merchantId)
                .groupId(groupId)
                .splitId(splitId)
                .categoryId(categoryId)
                .amount(amount)
                .currencyCode(currencyCode)
                .notes(notes)
                .status(status)
                .recurringItemId(recurringItemId)
                .occurrenceDate(occurrenceDate)
                .tagIds(tagIds != null ? new HashSet<>(tagIds) : new HashSet<>())
                .build();
    }
}
