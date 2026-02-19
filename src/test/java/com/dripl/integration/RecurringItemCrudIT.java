package com.dripl.integration;

import com.dripl.auth.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringItemCrudIT extends BaseIntegrationTest {

    @Autowired private TokenService tokenService;

    private String token;
    private UUID workspaceId;
    private UUID userId;
    private String accountId;
    private String categoryId;
    private String tagId;

    @BeforeEach
    void setUp() {
        String email = "recurring-user-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Recurring", "User");
        token = (String) bootstrap.get("token");
        workspaceId = UUID.fromString((String) bootstrap.get("lastWorkspaceId"));
        userId = UUID.fromString((String) bootstrap.get("id"));

        // Create an account
        var accountResp = restTemplate.exchange(
                "/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Checking","type":"CASH","subType":"CHECKING","balance":1000}
                        """, authHeaders(token)),
                Map.class);
        accountId = (String) accountResp.getBody().get("id");

        // Create a category
        var categoryResp = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Subscriptions"}
                        """, authHeaders(token)),
                Map.class);
        categoryId = (String) categoryResp.getBody().get("id");

        // Create a tag
        var tagResp = restTemplate.exchange(
                "/api/v1/tags", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"monthly"}
                        """, authHeaders(token)),
                Map.class);
        tagId = (String) tagResp.getBody().get("id");
    }

    @Test
    void createRecurringItem_withExistingMerchant_returns201() {
        // Pre-create merchant
        restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Netflix"}
                        """, authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"Netflix","merchantName":"Netflix","accountId":"%s","categoryId":"%s","amount":-15.99,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01","tagIds":["%s"]}
                        """.formatted(accountId, categoryId, tagId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = response.getBody();
        assertThat(body.get("description")).isEqualTo("Netflix");
        assertThat(body.get("amount")).isEqualTo(-15.99);
        assertThat(body.get("status")).isEqualTo("ACTIVE");
        assertThat(body.get("frequencyGranularity")).isEqualTo("MONTH");
        assertThat(body.get("frequencyQuantity")).isEqualTo(1);
        assertThat(body.get("merchantId")).isNotNull();
        assertThat(body.get("categoryId")).isEqualTo(categoryId);
        assertThat((List<?>) body.get("anchorDates")).hasSize(1);
        assertThat((List<?>) body.get("tagIds")).hasSize(1);
    }

    @Test
    void createRecurringItem_autoCreatesMerchant_returns201() {
        var response = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"New Service","merchantName":"New Service","accountId":"%s","amount":-9.99,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01"}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("merchantId")).isNotNull();

        // Verify the merchant was actually created
        var merchantsResp = restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                List.class);
        List<Map<String, Object>> merchants = merchantsResp.getBody();
        assertThat(merchants).extracting(m -> m.get("name")).contains("New Service");
    }

    @Test
    void createRecurringItem_merchantLookup_caseInsensitive() {
        // Create "Netflix"
        restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Netflix"}
                        """, authHeaders(token)),
                Map.class);

        // Create recurring item with "NETFLIX" — should find existing, not create new
        var response = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"Netflix","merchantName":"NETFLIX","accountId":"%s","amount":-15.99,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01"}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify no duplicate merchant
        var merchantsResp = restTemplate.exchange(
                "/api/v1/merchants", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                List.class);
        long netflixCount = ((List<Map<String, Object>>) merchantsResp.getBody()).stream()
                .filter(m -> ((String) m.get("name")).equalsIgnoreCase("Netflix"))
                .count();
        assertThat(netflixCount).isEqualTo(1);
    }

    @Test
    void listRecurringItems_returnsAll() {
        // Create two recurring items
        restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"Service A","merchantName":"Service A","accountId":"%s","amount":-10.00,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01"}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"Service B","merchantName":"Service B","accountId":"%s","amount":-20.00,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01"}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void getRecurringItem_byId_returns200() {
        var createResp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"Spotify","merchantName":"Spotify","accountId":"%s","amount":-9.99,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01"}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String itemId = (String) createResp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/recurring-items/" + itemId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("amount")).isEqualTo(-9.99);
    }

    @Test
    void updateRecurringItem_partialFields_returns200() {
        var createResp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"Spotify","merchantName":"Spotify","accountId":"%s","amount":-9.99,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01"}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String itemId = (String) createResp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/recurring-items/" + itemId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"amount":-25.99,"description":"Updated"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("amount")).isEqualTo(-25.99);
        assertThat(response.getBody().get("description")).isEqualTo("Updated");
    }

    @Test
    void updateRecurringItem_changeMerchant_autoCreatesNew() {
        var createResp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"OldService","merchantName":"OldService","accountId":"%s","amount":-10.00,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01"}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String itemId = (String) createResp.getBody().get("id");
        String oldMerchantId = (String) createResp.getBody().get("merchantId");

        var response = restTemplate.exchange(
                "/api/v1/recurring-items/" + itemId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"merchantName":"NewService"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("merchantId")).isNotEqualTo(oldMerchantId);
    }

    @Test
    void updateRecurringItem_setCategoryToNull() {
        var createResp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"WithCategory","merchantName":"WithCategory","accountId":"%s","categoryId":"%s","amount":-10.00,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01"}
                        """.formatted(accountId, categoryId), authHeaders(token)),
                Map.class);
        String itemId = (String) createResp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/recurring-items/" + itemId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"categoryId":null}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("categoryId")).isNull();
    }

    @Test
    void updateRecurringItem_setAndClearTags() {
        var createResp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"TagTest","merchantName":"TagTest","accountId":"%s","amount":-10.00,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01"}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String itemId = (String) createResp.getBody().get("id");

        // Set tags
        var setResp = restTemplate.exchange(
                "/api/v1/recurring-items/" + itemId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"tagIds":["%s"]}
                        """.formatted(tagId), authHeaders(token)),
                Map.class);
        assertThat((List<?>) setResp.getBody().get("tagIds")).hasSize(1);

        // Clear tags
        var clearResp = restTemplate.exchange(
                "/api/v1/recurring-items/" + itemId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"tagIds":[]}
                        """, authHeaders(token)),
                Map.class);
        assertThat((List<?>) clearResp.getBody().get("tagIds")).isEmpty();
    }

    @Test
    void updateRecurringItem_endDate_setAndClear() {
        var createResp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"EndDateTest","merchantName":"EndDateTest","accountId":"%s","amount":-10.00,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01"}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String itemId = (String) createResp.getBody().get("id");
        assertThat(createResp.getBody().get("endDate")).isNull();

        // Set endDate
        var setResp = restTemplate.exchange(
                "/api/v1/recurring-items/" + itemId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"endDate":"2025-12-31"}
                        """, authHeaders(token)),
                Map.class);
        assertThat(setResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(setResp.getBody().get("endDate")).isNotNull();

        // Clear endDate
        var clearResp = restTemplate.exchange(
                "/api/v1/recurring-items/" + itemId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"endDate":null}
                        """, authHeaders(token)),
                Map.class);
        assertThat(clearResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clearResp.getBody().get("endDate")).isNull();
    }

    @Test
    void deleteRecurringItem_returns204() {
        var createResp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"ToDelete","merchantName":"ToDelete","accountId":"%s","amount":-10.00,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01"}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String itemId = (String) createResp.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/recurring-items/" + itemId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify gone
        var getResp = restTemplate.exchange(
                "/api/v1/recurring-items/" + itemId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getRecurringItem_notFound_returns404() {
        var response = restTemplate.exchange(
                "/api/v1/recurring-items/" + UUID.randomUUID(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void workspaceIsolation_cannotAccessOtherWorkspaceRecurringItem() {
        var createResp = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"Isolated","merchantName":"Isolated","accountId":"%s","amount":-10.00,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01"}
                        """.formatted(accountId), authHeaders(token)),
                Map.class);
        String itemId = (String) createResp.getBody().get("id");

        // Switch to different workspace
        String token2 = tokenService.mintToken(userId, UUID.randomUUID());

        var response = restTemplate.exchange(
                "/api/v1/recurring-items/" + itemId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token2)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createRecurringItem_accountNotInWorkspace_returns404() {
        var response = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"BadAccount","merchantName":"BadAccount","accountId":"%s","amount":-10.00,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01"}
                        """.formatted(UUID.randomUUID()), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("detail")).toString().contains("Account not found");
    }

    @Test
    void createRecurringItem_categoryNotInWorkspace_returns404() {
        var response = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"BadCategory","merchantName":"BadCategory","accountId":"%s","categoryId":"%s","amount":-10.00,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01"}
                        """.formatted(accountId, UUID.randomUUID()), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("detail")).toString().contains("Category not found");
    }

    @Test
    void createRecurringItem_tagNotInWorkspace_returns404() {
        var response = restTemplate.exchange(
                "/api/v1/recurring-items", HttpMethod.POST,
                new HttpEntity<>("""
                        {"description":"BadTag","merchantName":"BadTag","accountId":"%s","amount":-10.00,"frequencyGranularity":"MONTH","frequencyQuantity":1,"anchorDates":["2025-07-15"],"startDate":"2025-07-01","tagIds":["%s"]}
                        """.formatted(accountId, UUID.randomUUID()), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("detail")).toString().contains("Tag");
    }
}
