package com.dripl.recurring.dto;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.dto.BaseDto;
import com.dripl.recurring.enums.FrequencyGranularity;
import com.dripl.recurring.enums.RecurringItemStatus;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class RecurringItemDto extends BaseDto {

    private UUID workspaceId;
    private String description;
    private UUID merchantId;
    private UUID accountId;
    private UUID categoryId;
    private BigDecimal amount;
    private CurrencyCode currencyCode;
    private String notes;
    private FrequencyGranularity frequencyGranularity;
    private Integer frequencyQuantity;
    private List<LocalDateTime> anchorDates;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private RecurringItemStatus status;
    private Set<UUID> tagIds;
}
