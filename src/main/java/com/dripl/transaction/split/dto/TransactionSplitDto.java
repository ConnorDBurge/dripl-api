package com.dripl.transaction.split.dto;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class TransactionSplitDto extends BaseDto {

    private UUID workspaceId;
    private UUID accountId;
    private BigDecimal totalAmount;
    private CurrencyCode currencyCode;
    private LocalDateTime date;
    private Set<UUID> transactionIds;
}
