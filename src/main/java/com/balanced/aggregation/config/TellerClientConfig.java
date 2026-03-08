package com.balanced.aggregation.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;

@Configuration
@ConditionalOnProperty(name = "balanced.aggregation.provider", havingValue = "teller")
@Profile("!test & !integration")
public class TellerClientConfig {

    private static final String TELLER_BASE_URL = "https://api.teller.io";
    private static final String TELLER_VERSION = "2020-10-12";

    @Bean
    public RestClient tellerRestClient(AggregationProperties props) {
        var tellerProps = props.teller();

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(TELLER_BASE_URL)
                .defaultHeader("Teller-Version", TELLER_VERSION);

        if (hasCertificate(tellerProps)) {
            builder.requestFactory(createMtlsRequestFactory(tellerProps));
        }

        return builder.build();
    }

    private boolean hasCertificate(AggregationProperties.TellerProperties tellerProps) {
        return tellerProps.certificatePath() != null
                && !tellerProps.certificatePath().isBlank()
                && tellerProps.privateKeyPath() != null
                && !tellerProps.privateKeyPath().isBlank();
    }

    private JdkClientHttpRequestFactory createMtlsRequestFactory(
            AggregationProperties.TellerProperties tellerProps) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (var certStream = new FileInputStream(tellerProps.certificatePath())) {
                keyStore.load(certStream, "".toCharArray());
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, "".toCharArray());

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            HttpClient httpClient = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();

            return new JdkClientHttpRequestFactory(httpClient);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure mTLS for Teller API", e);
        }
    }
}
