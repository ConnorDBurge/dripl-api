package com.dripl.account;

import com.dripl.account.enums.AccountSubType;
import com.dripl.account.enums.AccountType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTypeSubTypeTest {

    @Test
    void cashType_supportsOnlyCashSubTypes() {
        EnumSet<AccountSubType> allowed = AccountType.CASH.allowedSubTypes();
        assertThat(allowed).containsExactlyInAnyOrder(
                AccountSubType.CHECKING, AccountSubType.SAVINGS, AccountSubType.DIGITAL_WALLET,
                AccountSubType.GIFT_CARD, AccountSubType.PHYSICAL_CASH, AccountSubType.OTHER_CASH);
    }

    @Test
    void creditType_supportsOnlyCreditSubTypes() {
        EnumSet<AccountSubType> allowed = AccountType.CREDIT.allowedSubTypes();
        assertThat(allowed).containsExactlyInAnyOrder(
                AccountSubType.CREDIT_CARD, AccountSubType.CHARGE_CARD,
                AccountSubType.LINE_OF_CREDIT, AccountSubType.OTHER_CREDIT);
    }

    @Test
    void investmentType_supportsOnlyInvestmentSubTypes() {
        EnumSet<AccountSubType> allowed = AccountType.INVESTMENT.allowedSubTypes();
        assertThat(allowed).containsExactlyInAnyOrder(
                AccountSubType.RETIREMENT, AccountSubType.EDUCATION_SAVINGS,
                AccountSubType.BROKERAGE, AccountSubType.HEALTH_SAVINGS, AccountSubType.OTHER_INVESTMENT);
    }

    @Test
    void loanType_supportsOnlyLoanSubTypes() {
        EnumSet<AccountSubType> allowed = AccountType.LOAN.allowedSubTypes();
        assertThat(allowed).containsExactlyInAnyOrder(
                AccountSubType.MORTGAGE, AccountSubType.AUTO_LOAN, AccountSubType.STUDENT_LOAN,
                AccountSubType.PERSONAL_LOAN, AccountSubType.OTHER_LOAN);
    }

    @Test
    void cashType_doesNotSupportCreditCard() {
        assertThat(AccountType.CASH.supportsSubType(AccountSubType.CREDIT_CARD)).isFalse();
    }

    @Test
    void creditType_doesNotSupportChecking() {
        assertThat(AccountType.CREDIT.supportsSubType(AccountSubType.CHECKING)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(AccountSubType.class)
    void everySubType_hasAtLeastOneSupportedType(AccountSubType subType) {
        assertThat(subType.supportedTypes()).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(AccountType.class)
    void everyType_hasAtLeastOneSubType(AccountType type) {
        assertThat(type.allowedSubTypes()).isNotEmpty();
    }
}
