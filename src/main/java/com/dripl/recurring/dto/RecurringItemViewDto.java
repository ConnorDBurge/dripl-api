package com.dripl.recurring.dto;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.recurring.enums.FrequencyGranularity;
import com.dripl.recurring.enums.RecurringItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringItemViewDto {

    private UUID recurringItemId;
    private String description;
    private UUID merchantId;
    private UUID accountId;
    private UUID categoryId;
    private BigDecimal amount;
    private CurrencyCode currencyCode;
    private RecurringItemStatus status;
    private FrequencyGranularity frequencyGranularity;
    private Integer frequencyQuantity;
    private List<RecurringOccurrenceDto> occurrences;
    private BigDecimal totalExpected;
}
