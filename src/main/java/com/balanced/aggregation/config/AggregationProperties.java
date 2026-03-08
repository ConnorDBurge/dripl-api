package com.balanced.aggregation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "balanced.aggregation")
public record AggregationProperties(
        String provider,
        TellerProperties teller
) {
    public record TellerProperties(
            String applicationId,
            String environment,
            String certificatePath,
            String privateKeyPath
    ) {}
}
