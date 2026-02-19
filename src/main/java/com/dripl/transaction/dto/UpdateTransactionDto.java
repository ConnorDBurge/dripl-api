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

    private UUID recurringItemId;
    @Getter
    private boolean recurringItemIdSpecified;

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

    @JsonSetter("groupId")
    public void assignGroupId(UUID groupId) {
        this.groupId = groupId;
        this.groupIdSpecified = true;
    }
}
