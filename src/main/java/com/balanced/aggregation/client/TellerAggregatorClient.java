package com.balanced.aggregation.client;

import com.balanced.aggregation.dto.AggregatedAccount;
import com.balanced.aggregation.dto.AggregatedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "balanced.aggregation.provider", havingValue = "teller")
@Profile("!test & !integration")
public class TellerAggregatorClient implements BankAggregatorClient {

    private static final Logger log = LoggerFactory.getLogger(TellerAggregatorClient.class);
    private static final int PAGE_SIZE = 100;

    private final RestClient tellerRestClient;

    public TellerAggregatorClient(RestClient tellerRestClient) {
        this.tellerRestClient = tellerRestClient;
    }

    @Override
    public List<AggregatedAccount> getAccounts(String accessToken) {
        log.info("Fetching accounts from Teller");
        List<Map<String, Object>> response = tellerRestClient.get()
                .uri("/accounts")
                .headers(h -> h.setBasicAuth(accessToken, ""))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null) return List.of();

        return response.stream()
                .map(this::mapAccount)
                .toList();
    }

    @Override
    public List<AggregatedTransaction> getTransactions(String accessToken,
                                                        String externalAccountId,
                                                        LocalDate startDate,
                                                        LocalDate endDate) {
        log.info("Fetching transactions for account {} (range: {} to {})",
                externalAccountId, startDate, endDate);
        List<AggregatedTransaction> allTransactions = new java.util.ArrayList<>();
        String fromId = null;

        while (true) {
            final String cursor = fromId;
            List<Map<String, Object>> page = tellerRestClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/accounts/{id}/transactions")
                                .queryParam("count", PAGE_SIZE);
                        if (cursor != null) {
                            uriBuilder.queryParam("from_id", cursor);
                        }
                        if (startDate != null) {
                            uriBuilder.queryParam("start_date", startDate.toString());
                        }
                        if (endDate != null) {
                            uriBuilder.queryParam("end_date", endDate.toString());
                        }
                        return uriBuilder.build(externalAccountId);
                    })
                    .headers(h -> h.setBasicAuth(accessToken, ""))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (page == null || page.isEmpty()) break;

            allTransactions.addAll(page.stream().map(this::mapTransaction).toList());

            if (page.size() < PAGE_SIZE) break;

            fromId = str(page.getLast(), "id");
        }

        log.info("Fetched {} total transactions for account {}", allTransactions.size(), externalAccountId);
        return allTransactions;
    }

    @Override
    public void removeConnection(String accessToken) {
        log.info("Removing Teller enrollment");
        // Teller removes enrollment by deleting all accounts
        List<AggregatedAccount> accounts = getAccounts(accessToken);
        for (AggregatedAccount account : accounts) {
            tellerRestClient.delete()
                    .uri("/accounts/{id}", account.externalId())
                    .headers(h -> h.setBasicAuth(accessToken, ""))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    @SuppressWarnings("unchecked")
    private AggregatedAccount mapAccount(Map<String, Object> raw) {
        Map<String, Object> institution = (Map<String, Object>) raw.getOrDefault("institution", Map.of());
        return new AggregatedAccount(
                str(raw, "id"),
                str(raw, "enrollment_id"),
                str(raw, "name"),
                str(raw, "type"),
                str(raw, "subtype"),
                str(institution, "name"),
                str(institution, "id"),
                str(raw, "currency"),
                str(raw, "last_four"),
                str(raw, "status")
        );
    }

    @SuppressWarnings("unchecked")
    private AggregatedTransaction mapTransaction(Map<String, Object> raw) {
        Map<String, Object> counterparty = (Map<String, Object>) raw.getOrDefault("counterparty", Map.of());
        return new AggregatedTransaction(
                str(raw, "id"),
                str(raw, "account_id"),
                toBigDecimal(raw.get("amount")),
                toLocalDate(raw.get("date")),
                str(raw, "description"),
                str(counterparty, "name"),
                str(raw, "category"),
                str(raw, "status"),
                str(raw, "type")
        );
    }

    private static String str(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private static BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        return new BigDecimal(val.toString());
    }

    private static LocalDate toLocalDate(Object val) {
        if (val == null) return null;
        return LocalDate.parse(val.toString());
    }
}
