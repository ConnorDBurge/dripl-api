package com.dripl.transaction.dto;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.config.FlexibleLocalDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
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
public class CreateTransactionDto {

    private UUID accountId;

    @Size(min = 1, max = 100, message = "Merchant name must be between 1 and 100 characters")
    private String merchantName;

    private UUID categoryId;

    @NotNull(message = "Date must be provided")
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime date;

    private BigDecimal amount;

    private CurrencyCode currencyCode;

    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;

    private UUID recurringItemId;

    private LocalDate occurrenceDate;

    private Set<UUID> tagIds;
}
