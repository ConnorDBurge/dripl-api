package com.balanced.aggregation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AggregatedTransaction(
        String externalId,
        String externalAccountId,
        BigDecimal amount,
        LocalDate date,
        String description,
        String counterpartyName,
        String category,
        String status,
        String type
) {}
