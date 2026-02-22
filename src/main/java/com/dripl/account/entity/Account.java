package com.dripl.account.entity;

import com.dripl.account.enums.AccountSource;
import com.dripl.common.enums.Status;
import com.dripl.account.enums.AccountSubType;
import com.dripl.account.enums.AccountType;
import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.audit.BaseEntity;
import com.dripl.common.exception.BadRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "sub_type", nullable = false)
    private AccountSubType subType;

    @Builder.Default
    @Column(name = "starting_balance", nullable = false)
    private BigDecimal startingBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "balance", nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private CurrencyCode currency = CurrencyCode.USD;

    @Column(name = "institution_name")
    private String institutionName;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private AccountSource source = AccountSource.MANUAL;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "balance_last_updated")
    private LocalDateTime balanceLastUpdated;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "external_id")
    private String externalId;

    @PrePersist
    private void validateOnCreate() {
        validateTypeAndSubType();
    }

    private void validateTypeAndSubType() {
        if (type != null && subType != null && !type.supportsSubType(subType)) {
            String allowed = type.allowedSubTypes().stream()
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.joining(", "));
            throw new BadRequestException(String.format(
                    "Sub-type '%s' is not valid for account type '%s'. Allowed sub-types: [%s]",
                    subType, type, allowed));
        }
    }

    @Override
    public void setUpdatedByFromContext() {
        super.setUpdatedByFromContext();
        validateTypeAndSubType();
    }
}
