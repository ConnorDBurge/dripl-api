package com.dripl.transaction.dto;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.config.FlexibleLocalDateTimeDeserializer;
import com.dripl.transaction.enums.TransactionStatus;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTransactionDto {

    private UUID accountId;

    @Size(min = 1, max = 100, message = "Merchant name must be between 1 and 100 characters")
    private String merchantName;

    private UUID merchantId;
    @Getter
    private boolean merchantIdSpecified;

    private UUID categoryId;
    @Getter
    private boolean categoryIdSpecified;

    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
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

    @JsonSetter("merchantId")
    public void assignMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
        this.merchantIdSpecified = true;
    }

    @JsonSetter("categoryId")
    public void assignCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
        this.categoryIdSpecified = true;
    }

    @JsonSetter("notes")
    public void assignNotes(String notes) {
        this.notes = notes;
        this.notesSpecified = true;
    }

    @JsonSetter("tagIds")
    public void assignTagIds(Set<UUID> tagIds) {
        this.tagIds = tagIds;
        this.tagIdsSpecified = true;
    }

    @JsonSetter("recurringItemId")
    public void assignRecurringItemId(UUID recurringItemId) {
        this.recurringItemId = recurringItemId;
        this.recurringItemIdSpecified = true;
    }

    @JsonSetter("occurrenceDate")
    public void assignOccurrenceDate(LocalDate occurrenceDate) {
        this.occurrenceDate = occurrenceDate;
        this.occurrenceDateSpecified = true;
    }

    @JsonSetter("groupId")
    public void assignGroupId(UUID groupId) {
        this.groupId = groupId;
        this.groupIdSpecified = true;
    }

    @JsonSetter("splitId")
    public void assignSplitId(UUID splitId) {
        this.splitId = splitId;
        this.splitIdSpecified = true;
    }
}
