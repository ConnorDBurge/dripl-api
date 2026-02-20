package com.dripl.transaction.split.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SplitChildDto {

    @NotNull(message = "Amount must be provided for each split child")
    private BigDecimal amount;

    @Size(min = 1, max = 100, message = "Merchant name must be between 1 and 100 characters")
    private String merchantName;

    private UUID categoryId;

    private Set<UUID> tagIds;

    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;
}
