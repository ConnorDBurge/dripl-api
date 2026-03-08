package com.balanced.aggregation.client;

import com.balanced.aggregation.dto.AggregatedAccount;
import com.balanced.aggregation.dto.AggregatedTransaction;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
@Profile({"test", "integration"})
public class MockAggregatorClient implements BankAggregatorClient {

    @Override
    public List<AggregatedAccount> getAccounts(String accessToken) {
        return List.of(
                new AggregatedAccount("acc_mock_001", "enr_mock_001",
                        "Mock Checking", "depository", "checking",
                        "Mock Bank", "mock_bank", "USD", "1234", "open"),
                new AggregatedAccount("acc_mock_002", "enr_mock_001",
                        "Mock Savings", "depository", "savings",
                        "Mock Bank", "mock_bank", "USD", "5678", "open"),
                new AggregatedAccount("acc_mock_003", "enr_mock_001",
                        "Mock Credit Card", "credit", "credit_card",
                        "Mock Bank", "mock_bank", "USD", "9012", "open")
        );
    }

    @Override
    public List<AggregatedTransaction> getTransactions(String accessToken,
                                                        String externalAccountId,
                                                        LocalDate startDate,
                                                        LocalDate endDate) {
        return List.of(
                new AggregatedTransaction("txn_%s_001".formatted(externalAccountId), externalAccountId,
                        new BigDecimal("-42.34"), LocalDate.now().minusDays(1),
                        "AMAZON MKTPLACE PMTS", "Amazon", "shopping", "posted", "card_payment"),
                new AggregatedTransaction("txn_%s_002".formatted(externalAccountId), externalAccountId,
                        new BigDecimal("-5.75"), LocalDate.now().minusDays(2),
                        "STARBUCKS STORE 123", "Starbucks", "food_and_drink", "posted", "card_payment"),
                new AggregatedTransaction("txn_%s_003".formatted(externalAccountId), externalAccountId,
                        new BigDecimal("3500.00"), LocalDate.now().minusDays(3),
                        "DIRECT DEPOSIT ACME INC", "Acme Inc", "income", "posted", "ach")
        );
    }

    @Override
    public void removeConnection(String accessToken) {
        // no-op
    }
}
