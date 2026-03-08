package com.balanced.account.dto;

import com.balanced.account.enums.AccountSource;
import com.balanced.common.enums.Status;
import com.balanced.account.enums.AccountSubType;
import com.balanced.account.enums.AccountType;
import com.balanced.account.enums.CurrencyCode;
import com.balanced.common.dto.BaseDto;
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
public class AccountResponse extends BaseDto {

    private UUID workspaceId;
    private String name;
    private AccountType type;
    private AccountSubType subType;
    private BigDecimal startingBalance;
    private BigDecimal balance;
    private CurrencyCode currency;
    private String institutionName;
    private AccountSource source;
    private Status status;
    private LocalDateTime balanceLastUpdated;
    private LocalDateTime closedAt;
    private String externalId;
    private UUID bankConnectionId;
}
