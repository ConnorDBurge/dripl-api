package com.balanced.aggregation.dto;

import com.balanced.aggregation.enums.AggregationProvider;
import com.balanced.common.dto.BaseDto;
import com.balanced.common.enums.Status;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class BankConnectionResponse extends BaseDto {

    private UUID workspaceId;
    private AggregationProvider provider;
    private String enrollmentId;
    private String institutionId;
    private String institutionName;
    private Status status;
    private LocalDateTime lastSyncedAt;
}
