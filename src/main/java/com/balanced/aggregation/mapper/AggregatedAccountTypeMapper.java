package com.balanced.aggregation.mapper;

import com.balanced.account.enums.AccountSubType;
import com.balanced.account.enums.AccountType;

import java.util.Map;

/**
 * Maps provider account type/subtype strings to our AccountType/AccountSubType enums.
 * Each provider has different type taxonomies — this centralises the mapping.
 */
public final class AggregatedAccountTypeMapper {

    private AggregatedAccountTypeMapper() {}

    private record TypePair(AccountType type, AccountSubType subType) {}

    // Teller types: depository (checking, savings, money_market, certificate_of_deposit, treasury, sweep)
    //               credit (credit_card)
    private static final Map<String, TypePair> TELLER_MAPPINGS = Map.of(
            "depository.checking", new TypePair(AccountType.CASH, AccountSubType.CHECKING),
            "depository.savings", new TypePair(AccountType.CASH, AccountSubType.SAVINGS),
            "depository.money_market", new TypePair(AccountType.CASH, AccountSubType.SAVINGS),
            "depository.certificate_of_deposit", new TypePair(AccountType.CASH, AccountSubType.OTHER_CASH),
            "depository.treasury", new TypePair(AccountType.CASH, AccountSubType.OTHER_CASH),
            "depository.sweep", new TypePair(AccountType.CASH, AccountSubType.OTHER_CASH),
            "credit.credit_card", new TypePair(AccountType.CREDIT, AccountSubType.CREDIT_CARD)
    );

    private static final TypePair TELLER_DEFAULT = new TypePair(AccountType.CASH, AccountSubType.OTHER_CASH);

    public static AccountType mapTellerType(String tellerType, String tellerSubtype) {
        return TELLER_MAPPINGS.getOrDefault(tellerType + "." + tellerSubtype, TELLER_DEFAULT).type();
    }

    public static AccountSubType mapTellerSubType(String tellerType, String tellerSubtype) {
        return TELLER_MAPPINGS.getOrDefault(tellerType + "." + tellerSubtype, TELLER_DEFAULT).subType();
    }
}
