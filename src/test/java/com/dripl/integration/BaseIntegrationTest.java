package com.dripl.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:17-alpine");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    private static final String TEST_API_KEY = "dripl-dev-api-key";

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.set("X-API-Key", TEST_API_KEY);
        return headers;
    }

    protected HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", TEST_API_KEY);
        return headers;
    }

    /**
     * Bootstrap a user and return the full response map (includes token).
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> bootstrapUser(String email, String givenName, String familyName) {
        String body = """
                {"email":"%s","givenName":"%s","familyName":"%s"}
                """.formatted(email, givenName, familyName);
        var response = restTemplate.postForEntity(
                "/api/v1/users/bootstrap",
                new HttpEntity<>(body, jsonHeaders()),
                Map.class);
        return response.getBody();
    }

    /**
     * Create an account via GraphQL and return its ID.
     */
    @SuppressWarnings("unchecked")
    protected String createAccount(String token, String name, String type, String subType) {
        return createAccount(token, name, type, subType, null);
    }

    /**
     * Create an account via GraphQL with optional startingBalance and return its ID.
     */
    @SuppressWarnings("unchecked")
    protected String createAccount(String token, String name, String type, String subType, String startingBalance) {
        String balanceArg = startingBalance != null ? ", startingBalance: %s".formatted(startingBalance) : "";
        String query = """
                mutation {
                    createAccount(input: { name: "%s", type: %s, subType: %s%s }) { id }
                }
                """.formatted(name, type, subType, balanceArg);
        var response = restTemplate.exchange(
                "/graphql", HttpMethod.POST,
                new HttpEntity<>(Map.of("query", query), authHeaders(token)),
                Map.class);
        var data = (Map<String, Object>) response.getBody().get("data");
        var account = (Map<String, Object>) data.get("createAccount");
        return (String) account.get("id");
    }

    /**
     * Create a tag via GraphQL and return its ID.
     */
    @SuppressWarnings("unchecked")
    protected String createTag(String token, String name) {
        String query = """
                mutation {
                    createTag(input: { name: "%s" }) { id }
                }
                """.formatted(name);
        var response = restTemplate.exchange(
                "/graphql", HttpMethod.POST,
                new HttpEntity<>(Map.of("query", query), authHeaders(token)),
                Map.class);
        var data = (Map<String, Object>) response.getBody().get("data");
        var tag = (Map<String, Object>) data.get("createTag");
        return (String) tag.get("id");
    }
}
