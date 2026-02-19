package com.dripl.recurring.dto;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.config.FlexibleLocalDateTimeDeserializer;
import com.dripl.recurring.enums.FrequencyGranularity;
import com.dripl.recurring.enums.RecurringItemStatus;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateRecurringItemDto {

    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;

    @Size(min = 1, max = 100, message = "Merchant name must be between 1 and 100 characters")
    private String merchantName;

    private UUID accountId;

    private UUID categoryId;
    @Getter
    private boolean categoryIdSpecified;

    private BigDecimal amount;

    private CurrencyCode currencyCode;

    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;
    @Getter
    private boolean notesSpecified;

    private FrequencyGranularity frequencyGranularity;

    @Min(value = 1, message = "Frequency quantity must be at least 1")
    private Integer frequencyQuantity;

    @JsonDeserialize(contentUsing = FlexibleLocalDateTimeDeserializer.class)
    private List<LocalDateTime> anchorDates;

    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime startDate;

    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime endDate;
    @Getter
    private boolean endDateSpecified;

    private RecurringItemStatus status;

    private Set<UUID> tagIds;
    @Getter
    private boolean tagIdsSpecified;

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

    @JsonSetter("endDate")
    public void assignEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
        this.endDateSpecified = true;
    }

    @JsonSetter("tagIds")
    public void assignTagIds(Set<UUID> tagIds) {
        this.tagIds = tagIds;
        this.tagIdsSpecified = true;
    }
}
