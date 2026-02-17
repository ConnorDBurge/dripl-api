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

class CategoryCrudIT extends BaseIntegrationTest {

    @Autowired private TokenService tokenService;

    private String token;
    private UUID workspaceId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        String email = "category-user-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Category", "User");
        token = (String) bootstrap.get("token");
        workspaceId = UUID.fromString((String) bootstrap.get("lastWorkspaceId"));
        userId = UUID.fromString((String) bootstrap.get("id"));
    }

    @Test
    void createCategory_rootCategory_returns201() {
        var response = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("name")).isEqualTo("Food");
        assertThat(response.getBody().get("status")).isEqualTo("ACTIVE");
        assertThat(response.getBody().get("parentId")).isNull();
        assertThat(response.getBody().get("income")).isEqualTo(false);
        assertThat(response.getBody().get("excludeFromBudget")).isEqualTo(false);
        assertThat(response.getBody().get("excludeFromTotals")).isEqualTo(false);
        assertThat(response.getBody().get("workspaceId")).isEqualTo(workspaceId.toString());
    }

    @Test
    void createCategory_withAllFields_returns201() {
        var response = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {
                            "name":"Salary",
                            "description":"Monthly pay",
                            "income":true,
                            "excludeFromBudget":true,
                            "excludeFromTotals":false
                        }
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("name")).isEqualTo("Salary");
        assertThat(response.getBody().get("description")).isEqualTo("Monthly pay");
        assertThat(response.getBody().get("income")).isEqualTo(true);
        assertThat(response.getBody().get("excludeFromBudget")).isEqualTo(true);
    }

    @Test
    void createCategory_withParent_returns201() {
        var parentResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);
        String parentId = (String) parentResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries","parentId":"%s"}
                        """.formatted(parentId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("name")).isEqualTo("Groceries");
        assertThat(response.getBody().get("parentId")).isEqualTo(parentId);
    }

    @Test
    void createCategory_parentTooDeep_returns400() {
        // Create root
        var rootResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);
        String rootId = (String) rootResponse.getBody().get("id");

        // Create child
        var childResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries","parentId":"%s"}
                        """.formatted(rootId), authHeaders(token)),
                Map.class);
        String childId = (String) childResponse.getBody().get("id");

        // Try to create grandchild — should fail
        var response = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Organic","parentId":"%s"}
                        """.formatted(childId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail")).isEqualTo("Parent category cannot have its own parent (maximum depth is 2)");
    }

    @Test
    void createCategory_parentNotFound_returns404() {
        UUID fakeParentId = UUID.randomUUID();

        var response = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries","parentId":"%s"}
                        """.formatted(fakeParentId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("detail")).isEqualTo("Parent category not found");
    }

    @Test
    void listCategories_returnsFlat() {
        restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Bills"}
                        """, authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void getCategoryTree_returnsHierarchy() {
        var parentResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);
        String parentId = (String) parentResponse.getBody().get("id");

        restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries","parentId":"%s"}
                        """.formatted(parentId), authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Dining Out","parentId":"%s"}
                        """.formatted(parentId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/categories/tree", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> food = (Map<String, Object>) response.getBody().get(0);
        assertThat(food.get("name")).isEqualTo("Food");
        assertThat(food.get("group")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) food.get("children");
        assertThat(children).hasSize(2);
    }

    @Test
    void getCategory_byId_returns200() {
        var createResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Bills","description":"Monthly bills"}
                        """, authHeaders(token)),
                Map.class);
        String categoryId = (String) createResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/categories/" + categoryId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Bills");
        assertThat(response.getBody().get("description")).isEqualTo("Monthly bills");
        assertThat(response.getBody().get("children")).isNotNull();
        assertThat((List<?>) response.getBody().get("children")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getCategory_parentIncludesChildren() {
        var parentResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);
        String parentId = (String) parentResponse.getBody().get("id");

        restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries","parentId":"%s"}
                        """.formatted(parentId), authHeaders(token)),
                Map.class);
        restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Dining Out","parentId":"%s"}
                        """.formatted(parentId), authHeaders(token)),
                Map.class);

        var response = restTemplate.exchange(
                "/api/v1/categories/" + parentId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Food");

        List<Map<String, Object>> children = (List<Map<String, Object>>) response.getBody().get("children");
        assertThat(children).hasSize(2);
        assertThat(children).extracting(c -> c.get("name"))
                .containsExactlyInAnyOrder("Groceries", "Dining Out");
        assertThat(children).allMatch(c -> parentId.equals(c.get("parentId")));
    }

    @Test
    void updateCategory_name_returns200() {
        var createResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);
        String categoryId = (String) createResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/categories/" + categoryId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"name":"Food & Drink"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Food & Drink");
    }

    @Test
    void updateCategory_setParent_returns200() {
        var parentResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);
        String parentId = (String) parentResponse.getBody().get("id");

        var childResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries"}
                        """, authHeaders(token)),
                Map.class);
        String childId = (String) childResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/categories/" + childId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"parentId":"%s"}
                        """.formatted(parentId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("parentId")).isEqualTo(parentId);
    }

    @Test
    void updateCategory_removeParent_returns200() {
        var parentResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);
        String parentId = (String) parentResponse.getBody().get("id");

        var childResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries","parentId":"%s"}
                        """.formatted(parentId), authHeaders(token)),
                Map.class);
        String childId = (String) childResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/categories/" + childId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"parentId":null}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("parentId")).isNull();
    }

    @Test
    void updateCategory_parentIdOmitted_doesNotChangeParent() {
        var parentResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);
        String parentId = (String) parentResponse.getBody().get("id");

        var childResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries","parentId":"%s"}
                        """.formatted(parentId), authHeaders(token)),
                Map.class);
        String childId = (String) childResponse.getBody().get("id");

        // PATCH with name only — parentId not included in body
        var response = restTemplate.exchange(
                "/api/v1/categories/" + childId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"name":"Grocery Store"}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Grocery Store");
        assertThat(response.getBody().get("parentId")).isEqualTo(parentId);
    }

    @Test
    void updateCategory_selfParent_returns400() {
        var createResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);
        String categoryId = (String) createResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/categories/" + categoryId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"parentId":"%s"}
                        """.formatted(categoryId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail")).isEqualTo("Category cannot be its own parent");
    }

    @Test
    void updateCategory_parentTooDeep_returns400() {
        // Create root → child chain
        var rootResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);
        String rootId = (String) rootResponse.getBody().get("id");

        var childResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries","parentId":"%s"}
                        """.formatted(rootId), authHeaders(token)),
                Map.class);
        String childId = (String) childResponse.getBody().get("id");

        // Create an orphan, try to move it under the child (would be depth 3)
        var orphanResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Organic"}
                        """, authHeaders(token)),
                Map.class);
        String orphanId = (String) orphanResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/categories/" + orphanId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"parentId":"%s"}
                        """.formatted(childId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail")).isEqualTo("Parent category cannot have its own parent (maximum depth is 2)");
    }

    @Test
    void updateCategory_categoryWithChildren_cannotBeNested() {
        // Food has child Groceries. Try to move Food under Transport → would create depth 3.
        var foodResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);
        String foodId = (String) foodResponse.getBody().get("id");

        restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries","parentId":"%s"}
                        """.formatted(foodId), authHeaders(token)),
                Map.class);

        var transportResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Transport"}
                        """, authHeaders(token)),
                Map.class);
        String transportId = (String) transportResponse.getBody().get("id");

        // Try to make Food (which has children) a child of Transport
        var response = restTemplate.exchange(
                "/api/v1/categories/" + foodId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"parentId":"%s"}
                        """.formatted(transportId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail")).isEqualTo("Cannot nest a category that already has children (maximum depth is 2)");
    }

    @Test
    void updateCategory_parentNotFound_returns404() {
        var createResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries"}
                        """, authHeaders(token)),
                Map.class);
        String categoryId = (String) createResponse.getBody().get("id");

        UUID fakeParentId = UUID.randomUUID();
        var response = restTemplate.exchange(
                "/api/v1/categories/" + categoryId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"parentId":"%s"}
                        """.formatted(fakeParentId), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("detail")).isEqualTo("Parent category not found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateCategory_clearChildren_detachesAll() {
        var foodResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);
        String foodId = (String) foodResponse.getBody().get("id");

        var groceriesResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries","parentId":"%s"}
                        """.formatted(foodId), authHeaders(token)),
                Map.class);
        String groceriesId = (String) groceriesResponse.getBody().get("id");

        var diningResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Dining Out","parentId":"%s"}
                        """.formatted(foodId), authHeaders(token)),
                Map.class);
        String diningId = (String) diningResponse.getBody().get("id");

        // Clear children
        var response = restTemplate.exchange(
                "/api/v1/categories/" + foodId, HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"children":[]}
                        """, authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify Food has no children
        var foodGet = restTemplate.exchange(
                "/api/v1/categories/" + foodId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat((List<?>) foodGet.getBody().get("children")).isEmpty();

        // Verify both former children are now root categories
        var groceriesGet = restTemplate.exchange(
                "/api/v1/categories/" + groceriesId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(groceriesGet.getBody().get("parentId")).isNull();

        var diningGet = restTemplate.exchange(
                "/api/v1/categories/" + diningId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(diningGet.getBody().get("parentId")).isNull();
    }

    @Test
    void deleteCategory_returns204() {
        var createResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Delete Me"}
                        """, authHeaders(token)),
                Map.class);
        String categoryId = (String) createResponse.getBody().get("id");

        var response = restTemplate.exchange(
                "/api/v1/categories/" + categoryId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var getResponse = restTemplate.exchange(
                "/api/v1/categories/" + categoryId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteCategory_parentSetsChildrenToNull() {
        var parentResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Food"}
                        """, authHeaders(token)),
                Map.class);
        String parentId = (String) parentResponse.getBody().get("id");

        var childResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Groceries","parentId":"%s"}
                        """.formatted(parentId), authHeaders(token)),
                Map.class);
        String childId = (String) childResponse.getBody().get("id");

        // Delete parent
        restTemplate.exchange(
                "/api/v1/categories/" + parentId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Void.class);

        // Child should still exist with null parentId (ON DELETE SET NULL)
        var response = restTemplate.exchange(
                "/api/v1/categories/" + childId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Groceries");
        assertThat(response.getBody().get("parentId")).isNull();
    }

    @Test
    void workspaceIsolation_cannotAccessOtherWorkspaceCategories() {
        var createResponse = restTemplate.exchange(
                "/api/v1/categories", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Isolated"}
                        """, authHeaders(token)),
                Map.class);
        String categoryId = (String) createResponse.getBody().get("id");

        UUID workspace2Id = UUID.randomUUID();
        String token2 = tokenService.mintToken(userId, workspace2Id);

        var response = restTemplate.exchange(
                "/api/v1/categories/" + categoryId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token2)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getCategory_notFound_returns404() {
        UUID randomId = UUID.randomUUID();

        var response = restTemplate.exchange(
                "/api/v1/categories/" + randomId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("detail")).isEqualTo("Category not found");
    }
}
