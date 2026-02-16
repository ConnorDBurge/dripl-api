package com.dripl.account.enums;

import java.util.EnumSet;

public enum AccountType {
    CASH,
    CREDIT,
    INVESTMENT,
    LOAN,
    REAL_ESTATE,
    VEHICLE,
    EMPLOYEE_COMPENSATION,
    OTHER_LIABILITY,
    OTHER_ASSET;

    public EnumSet<AccountSubType> allowedSubTypes() {
        EnumSet<AccountSubType> result = EnumSet.noneOf(AccountSubType.class);
        for (AccountSubType subType : AccountSubType.values()) {
            if (subType.supports(this)) {
                result.add(subType);
            }
        }
        return result;
    }

    public boolean supportsSubType(AccountSubType subType) {
        return subType != null && subType.supports(this);
    }
}
