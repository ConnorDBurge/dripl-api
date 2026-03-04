package com.dripl.account.dto;

import com.dripl.account.enums.AccountSource;
import com.dripl.account.enums.AccountSubType;
import com.dripl.account.enums.AccountType;
import com.dripl.account.enums.CurrencyCode;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAccountInput {

    @Size(min = 1, max = 120, message = "Account name must be between 1 and 120 characters")
    private String name;

    private AccountType type;

    private AccountSubType subType;

    private BigDecimal startingBalance;

    private CurrencyCode currency;

    @Size(min = 1, max = 120, message = "Institution name must be between 1 and 120 characters")
    private String institutionName;

    private AccountSource source;
}
