package com.dripl.transaction.group.dto;

import com.dripl.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class TransactionGroupDto extends BaseDto {

    private UUID workspaceId;
    private String name;
    private UUID categoryId;
    private String notes;
    private Set<UUID> tagIds;
    private BigDecimal totalAmount;
    private Set<UUID> transactionIds;
}
