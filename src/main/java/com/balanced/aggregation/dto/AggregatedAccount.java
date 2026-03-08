package com.balanced.aggregation.dto;

public record AggregatedAccount(
        String externalId,
        String enrollmentId,
        String name,
        String type,
        String subtype,
        String institutionName,
        String institutionId,
        String currency,
        String lastFour,
        String status
) {}
