package com.dripl.recurring.dto;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.config.FlexibleLocalDateTimeDeserializer;
import com.dripl.recurring.enums.FrequencyGranularity;
import com.dripl.recurring.enums.RecurringItemStatus;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateRecurringItemInput {

    private String description;

    private String merchantName;

    private UUID accountId;

    private UUID categoryId;
    @Getter
@Setter
    private boolean categoryIdSpecified;

    private BigDecimal amount;

    private CurrencyCode currencyCode;

    private String notes;
    @Getter
@Setter
    private boolean notesSpecified;

    private FrequencyGranularity frequencyGranularity;

    private Integer frequencyQuantity;

    @JsonDeserialize(contentUsing = FlexibleLocalDateTimeDeserializer.class)
    private List<LocalDateTime> anchorDates;

    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime startDate;

    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime endDate;
    @Getter
@Setter
    private boolean endDateSpecified;

    private RecurringItemStatus status;

    private Set<UUID> tagIds;
    @Getter
@Setter
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
