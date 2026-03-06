package com.dripl.transaction.dto;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.transaction.enums.TransactionSource;
import com.dripl.transaction.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionFilter {

    private UUID accountId;
    private UUID merchantId;
    private UUID categoryId;
    private UUID groupId;
    private UUID splitId;
    private UUID recurringItemId;
    private TransactionStatus status;
    private TransactionSource source;
    private CurrencyCode currencyCode;
    private Set<UUID> tagIds;
    private String startDate;
    private String endDate;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private String search;
}
