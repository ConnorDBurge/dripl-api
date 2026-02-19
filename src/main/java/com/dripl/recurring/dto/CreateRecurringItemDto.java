package com.dripl.recurring.dto;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.config.FlexibleLocalDateTimeDeserializer;
import com.dripl.recurring.enums.FrequencyGranularity;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class CreateRecurringItemDto {

    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;

    @NotBlank(message = "Merchant name must be provided")
    @Size(min = 1, max = 100, message = "Merchant name must be between 1 and 100 characters")
    private String merchantName;

    @NotNull(message = "Account ID must be provided")
    private UUID accountId;

    private UUID categoryId;

    @NotNull(message = "Amount must be provided")
    private BigDecimal amount;

    private CurrencyCode currencyCode;

    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;

    @NotNull(message = "Frequency granularity must be provided")
    private FrequencyGranularity frequencyGranularity;

    @Min(value = 1, message = "Frequency quantity must be at least 1")
    private Integer frequencyQuantity;

    @NotEmpty(message = "At least one anchor date must be provided")
    @JsonDeserialize(contentUsing = FlexibleLocalDateTimeDeserializer.class)
    private List<LocalDateTime> anchorDates;

    @NotNull(message = "Start date must be provided")
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime startDate;

    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime endDate;

    private Set<UUID> tagIds;
}
