package com.balanced.aggregation.entity;

import com.balanced.aggregation.enums.AggregationProvider;
import com.balanced.common.audit.BaseEntity;
import com.balanced.common.crypto.StringEncryptionConverter;
import com.balanced.common.enums.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "bank_connections")
public class BankConnection extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private AggregationProvider provider;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Column(name = "enrollment_id")
    private String enrollmentId;

    @Column(name = "institution_id")
    private String institutionId;

    @Column(name = "institution_name")
    private String institutionName;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
}
