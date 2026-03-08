package com.balanced.aggregation.client;

import com.balanced.aggregation.dto.AggregatedAccount;
import com.balanced.aggregation.dto.AggregatedTransaction;

import java.time.LocalDate;
import java.util.List;

/**
 * Provider-agnostic interface for bank aggregation.
 * Implementations: TellerAggregatorClient, MockAggregatorClient.
 */
public interface BankAggregatorClient {

    /** Fetch all accounts for an access token. */
    List<AggregatedAccount> getAccounts(String accessToken);

    /** Fetch transactions for an account within a date range, handling pagination internally. */
    List<AggregatedTransaction> getTransactions(String accessToken,
                                                 String externalAccountId,
                                                 LocalDate startDate,
                                                 LocalDate endDate);

    /** Remove enrollment / revoke access. */
    void removeConnection(String accessToken);
}
