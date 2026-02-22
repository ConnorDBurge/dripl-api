package com.dripl.workspace.settings.entity;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "workspace_settings")
@EqualsAndHashCode(callSuper = true)
public class WorkspaceSettings extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, unique = true)
    private UUID workspaceId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "default_currency_code", nullable = false)
    private CurrencyCode defaultCurrencyCode = CurrencyCode.USD;

    @Builder.Default
    @Column(name = "timezone", nullable = false)
    private String timezone = "UTC";
}
