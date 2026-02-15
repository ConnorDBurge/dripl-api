package com.dripl.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CorrelationIdIT extends BaseIntegrationTest {

    @Autowired
    private com.dripl.workspace.repository.WorkspaceRepository workspaceRepository;

    @Test
    void responseHeader_echoesCorrelationId() {
        String correlationId = UUID.randomUUID().toString();

        HttpHeaders headers = jsonHeaders();
        headers.set("X-Correlation-Id", correlationId);

        var response = restTemplate.exchange(
                "/api/v1/users/bootstrap", HttpMethod.POST,
                new HttpEntity<>("""
                        {"email":"corr-echo-%s@test.com","givenName":"Echo","familyName":"Test"}
                        """.formatted(System.nanoTime()), headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo(correlationId);
    }

    @Test
    void responseHeader_generatesCorrelationId_whenNotSent() {
        var response = restTemplate.exchange(
                "/api/v1/users/bootstrap", HttpMethod.POST,
                new HttpEntity<>("""
                        {"email":"corr-gen-%s@test.com","givenName":"Gen","familyName":"Test"}
                        """.formatted(System.nanoTime()), jsonHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String returned = response.getHeaders().getFirst("X-Correlation-Id");
        assertThat(returned).isNotNull().isNotBlank();
        // Should be a valid UUID
        assertThat(UUID.fromString(returned)).isNotNull();
    }

    @Test
    void errorResponse_includesCorrelationId() {
        String correlationId = UUID.randomUUID().toString();

        var user = bootstrapUser("corr-err-%s@test.com".formatted(System.nanoTime()), "Err", "Test");
        String token = (String) user.get("token");

        HttpHeaders headers = authHeaders(token);
        headers.set("X-Correlation-Id", correlationId);

        var response = restTemplate.exchange(
                "/api/v1/workspaces/current/members/00000000-0000-0000-0000-000000000000",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("correlationId")).isEqualTo(correlationId);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo(correlationId);
    }

    @Test
    void correlationId_propagatesToAsyncCleanup() {
        // Attach a test appender to capture cleanup listener logs
        var testAppender = new TestAppender();
        Logger cleanupLogger = (Logger) LoggerFactory.getLogger(
                "com.dripl.workspace.listener.WorkspaceCleanupListener");
        cleanupLogger.addAppender(testAppender);
        testAppender.start();

        try {
            String correlationId = UUID.randomUUID().toString();

            var user = bootstrapUser("corr-async-%s@test.com".formatted(System.nanoTime()), "Async", "Test");
            String token = (String) user.get("token");
            String workspaceId = (String) user.get("lastWorkspaceId");
            String userId = (String) user.get("id");

            // Remove the only member with our correlation ID
            HttpHeaders headers = authHeaders(token);
            headers.set("X-Correlation-Id", correlationId);

            restTemplate.exchange(
                    "/api/v1/workspaces/current/members/" + userId, HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class);

            // Wait for async cleanup and verify the correlation ID made it to the log
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(workspaceRepository.findById(UUID.fromString(workspaceId))).isEmpty();
                assertThat(testAppender.events).anyMatch(event ->
                        correlationId.equals(event.getMDCPropertyMap().get("correlationId"))
                                && event.getFormattedMessage().contains("Deleting orphaned workspace"));
            });
        } finally {
            testAppender.stop();
            cleanupLogger.detachAppender(testAppender);
        }
    }

    /**
     * In-memory Logback appender for capturing log events in tests.
     */
    private static class TestAppender extends AppenderBase<ILoggingEvent> {
        final CopyOnWriteArrayList<ILoggingEvent> events = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            events.add(event);
        }
    }
}
