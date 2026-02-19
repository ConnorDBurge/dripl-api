package com.dripl.transaction.dto;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.dto.BaseDto;
import com.dripl.transaction.enums.TransactionSource;
import com.dripl.transaction.enums.TransactionStatus;
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
public class TransactionDto extends BaseDto {

    private UUID workspaceId;
    private UUID accountId;
    private UUID merchantId;
    private UUID categoryId;
    private LocalDateTime date;
    private BigDecimal amount;
    private CurrencyCode currencyCode;
    private String notes;
    private TransactionStatus status;
    private TransactionSource source;
    private LocalDateTime pendingAt;
    private LocalDateTime postedAt;
    private UUID recurringItemId;
    private UUID groupId;
    private Set<UUID> tagIds;
}
