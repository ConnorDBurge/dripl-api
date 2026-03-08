package com.balanced.aggregation.dto;

public record SyncResult(
        int transactionsAdded,
        int transactionsModified,
        int transactionsRemoved,
        int accountsSynced
) {}
