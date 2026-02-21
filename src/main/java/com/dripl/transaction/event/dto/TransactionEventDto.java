package com.dripl.transaction.event.dto;

import com.dripl.common.event.FieldChange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionEventDto {

    private UUID id;
    private UUID transactionId;
    private String eventType;
    private List<FieldChange> changes;
    private String performedBy;
    private LocalDateTime performedAt;
}
