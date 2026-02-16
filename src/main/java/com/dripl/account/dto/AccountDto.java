package com.dripl.account.dto;

import com.dripl.account.enums.AccountSource;
import com.dripl.account.enums.AccountStatus;
import com.dripl.account.enums.AccountSubType;
import com.dripl.account.enums.AccountType;
import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class AccountDto extends BaseDto {

    private UUID workspaceId;
    private String name;
    private AccountType type;
    private AccountSubType subType;
    private BigDecimal balance;
    private CurrencyCode currency;
    private String institutionName;
    private AccountSource source;
    private AccountStatus status;
    private LocalDateTime balanceLastUpdated;
    private LocalDateTime closedAt;
    private String externalId;
    private Boolean excludeFromTransactions;
}
