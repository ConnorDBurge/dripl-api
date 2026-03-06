package com.dripl.transaction.dto;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.transaction.enums.TransactionStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTransactionInput {

    private UUID accountId;

    @Size(min = 1, max = 100, message = "Merchant name must be between 1 and 100 characters")
    private String merchantName;

    private UUID merchantId;
    @Getter
    private boolean merchantIdSpecified;

    private UUID categoryId;
    @Getter
    private boolean categoryIdSpecified;

    private LocalDateTime date;

    private BigDecimal amount;

    private CurrencyCode currencyCode;

    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;
    @Getter
    private boolean notesSpecified;

    private TransactionStatus status;

    private Set<UUID> tagIds;
    @Getter
    private boolean tagIdsSpecified;

    private UUID groupId;
    @Getter
    private boolean groupIdSpecified;

    private UUID splitId;
    @Getter
    private boolean splitIdSpecified;

    private UUID recurringItemId;
    @Getter
    private boolean recurringItemIdSpecified;

    private LocalDate occurrenceDate;
    @Getter
    private boolean occurrenceDateSpecified;

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
        this.merchantIdSpecified = true;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
        this.categoryIdSpecified = true;
    }

    public void setNotes(String notes) {
        this.notes = notes;
        this.notesSpecified = true;
    }

    public void setTagIds(Set<UUID> tagIds) {
        this.tagIds = tagIds;
        this.tagIdsSpecified = true;
    }

    public void setRecurringItemId(UUID recurringItemId) {
        this.recurringItemId = recurringItemId;
        this.recurringItemIdSpecified = true;
    }

    public void setOccurrenceDate(LocalDate occurrenceDate) {
        this.occurrenceDate = occurrenceDate;
        this.occurrenceDateSpecified = true;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
        this.groupIdSpecified = true;
    }

    public void setSplitId(UUID splitId) {
        this.splitId = splitId;
        this.splitIdSpecified = true;
    }
}
