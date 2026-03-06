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

class CategoryGraphQLIT extends BaseIntegrationTest {

    @Autowired private TokenService tokenService;

    private String token;
    private UUID workspaceId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        String email = "gql-cat-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Category", "User");
        token = (String) bootstrap.get("token");
        workspaceId = UUID.fromString((String) bootstrap.get("lastWorkspaceId"));
        userId = UUID.fromString((String) bootstrap.get("id"));
    }

    private Map<String, Object> graphql(String query) {
        return graphql(query, (Map<String, Object>) null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> graphql(String query, Map<String, Object> variables) {
        Map<String, Object> body = variables != null
                ? Map.of("query", query, "variables", variables)
                : Map.of("query", query);
        var response = restTemplate.exchange(
                "/graphql", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> response) {
        return (Map<String, Object>) response.get("data");
    }

    @Test
    void createCategory_rootCategory_returnsCategory() {
        var result = graphql("""
                mutation {
                    createCategory(input: { name: "Food" }) {
                        id name status parentId income excludeFromBudget excludeFromTotals workspaceId
                    }
                }
                """);

        assertThat(result.get("errors")).isNull();
        @SuppressWarnings("unchecked")
        var category = (Map<String, Object>) data(result).get("createCategory");
        assertThat(category.get("name")).isEqualTo("Food");
        assertThat(category.get("status")).isEqualTo("ACTIVE");
        assertThat(category.get("parentId")).isNull();
        assertThat(category.get("income")).isEqualTo(false);
        assertThat(category.get("excludeFromBudget")).isEqualTo(false);
        assertThat(category.get("excludeFromTotals")).isEqualTo(false);
        assertThat(category.get("workspaceId")).isEqualTo(workspaceId.toString());
    }

    @Test
    void createCategory_withAllFields_returnsCategory() {
        var result = graphql("""
                mutation {
                    createCategory(input: {
                        name: "Salary"
                        description: "Monthly pay"
                        income: true
                        excludeFromBudget: true
                        excludeFromTotals: false
                    }) {
                        name description income excludeFromBudget
                    }
                }
                """);

        assertThat(result.get("errors")).isNull();
        @SuppressWarnings("unchecked")
        var category = (Map<String, Object>) data(result).get("createCategory");
        assertThat(category.get("name")).isEqualTo("Salary");
        assertThat(category.get("description")).isEqualTo("Monthly pay");
        assertThat(category.get("income")).isEqualTo(true);
        assertThat(category.get("excludeFromBudget")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createCategory_withParent_returnsCategory() {
        var parentResult = graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        String parentId = (String) ((Map<String, Object>) data(parentResult).get("createCategory")).get("id");

        var result = graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { id name parentId }
                }
                """, Map.of("input", Map.of("name", "Groceries", "parentId", parentId)));

        assertThat(result.get("errors")).isNull();
        var category = (Map<String, Object>) data(result).get("createCategory");
        assertThat(category.get("name")).isEqualTo("Groceries");
        assertThat(category.get("parentId")).isEqualTo(parentId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createCategory_parentTooDeep_returnsError() {
        // Create root
        var rootResult = graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        String rootId = (String) ((Map<String, Object>) data(rootResult).get("createCategory")).get("id");

        // Create child
        var childResult = graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { id }
                }
                """, Map.of("input", Map.of("name", "Groceries", "parentId", rootId)));
        String childId = (String) ((Map<String, Object>) data(childResult).get("createCategory")).get("id");

        // Try to create grandchild — should fail
        var result = graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { id }
                }
                """, Map.of("input", Map.of("name", "Organic", "parentId", childId)));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void createCategory_parentNotFound_returnsError() {
        UUID fakeParentId = UUID.randomUUID();

        var result = graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { id }
                }
                """, Map.of("input", Map.of("name", "Groceries", "parentId", fakeParentId.toString())));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listCategories_returnsFlat() {
        graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        graphql("""
                mutation { createCategory(input: { name: "Bills" }) { id } }
                """);

        var result = graphql("{ categories { id name } }");

        assertThat(result.get("errors")).isNull();
        var categories = (List<Map<String, Object>>) data(result).get("categories");
        assertThat(categories).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getCategoryTree_returnsHierarchy() {
        var parentResult = graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        String parentId = (String) ((Map<String, Object>) data(parentResult).get("createCategory")).get("id");

        graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { id }
                }
                """, Map.of("input", Map.of("name", "Groceries", "parentId", parentId)));
        graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { id }
                }
                """, Map.of("input", Map.of("name", "Dining Out", "parentId", parentId)));

        var result = graphql("{ categoryTree { name group children { name } } }");

        assertThat(result.get("errors")).isNull();
        var tree = (List<Map<String, Object>>) data(result).get("categoryTree");
        assertThat(tree).hasSize(1);

        Map<String, Object> food = tree.get(0);
        assertThat(food.get("name")).isEqualTo("Food");
        assertThat(food.get("group")).isEqualTo(true);

        var children = (List<Map<String, Object>>) food.get("children");
        assertThat(children).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getCategory_byId_returnsCategory() {
        var createResult = graphql("""
                mutation {
                    createCategory(input: { name: "Bills", description: "Monthly bills" }) { id }
                }
                """);
        String categoryId = (String) ((Map<String, Object>) data(createResult).get("createCategory")).get("id");

        var result = graphql("""
                query($categoryId: ID!) {
                    category(categoryId: $categoryId) { name description }
                }
                """, Map.of("categoryId", categoryId));

        assertThat(result.get("errors")).isNull();
        var category = (Map<String, Object>) data(result).get("category");
        assertThat(category.get("name")).isEqualTo("Bills");
        assertThat(category.get("description")).isEqualTo("Monthly bills");
    }

    @Test
    @SuppressWarnings("unchecked")
    void categoryTree_parentIncludesChildren() {
        var parentResult = graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        String parentId = (String) ((Map<String, Object>) data(parentResult).get("createCategory")).get("id");

        graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { id }
                }
                """, Map.of("input", Map.of("name", "Groceries", "parentId", parentId)));
        graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { id }
                }
                """, Map.of("input", Map.of("name", "Dining Out", "parentId", parentId)));

        var result = graphql("{ categoryTree { name children { name parentId } } }");

        assertThat(result.get("errors")).isNull();
        var tree = (List<Map<String, Object>>) data(result).get("categoryTree");
        var food = tree.stream().filter(c -> "Food".equals(c.get("name"))).findFirst().orElseThrow();

        var children = (List<Map<String, Object>>) food.get("children");
        assertThat(children).hasSize(2);
        assertThat(children).extracting(c -> c.get("name"))
                .containsExactlyInAnyOrder("Groceries", "Dining Out");
        assertThat(children).allMatch(c -> parentId.equals(c.get("parentId")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateCategory_name_returnsCategory() {
        var createResult = graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        String categoryId = (String) ((Map<String, Object>) data(createResult).get("createCategory")).get("id");

        var result = graphql("""
                mutation($categoryId: ID!, $input: UpdateCategoryInput!) {
                    updateCategory(categoryId: $categoryId, input: $input) { id name }
                }
                """, Map.of("categoryId", categoryId, "input", Map.of("name", "Food & Drink")));

        assertThat(result.get("errors")).isNull();
        var category = (Map<String, Object>) data(result).get("updateCategory");
        assertThat(category.get("name")).isEqualTo("Food & Drink");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateCategory_setParent_returnsCategory() {
        var parentResult = graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        String parentId = (String) ((Map<String, Object>) data(parentResult).get("createCategory")).get("id");

        var childResult = graphql("""
                mutation { createCategory(input: { name: "Groceries" }) { id } }
                """);
        String childId = (String) ((Map<String, Object>) data(childResult).get("createCategory")).get("id");

        var result = graphql("""
                mutation($categoryId: ID!, $input: UpdateCategoryInput!) {
                    updateCategory(categoryId: $categoryId, input: $input) { id parentId }
                }
                """, Map.of("categoryId", childId, "input", Map.of("parentId", parentId)));

        assertThat(result.get("errors")).isNull();
        var category = (Map<String, Object>) data(result).get("updateCategory");
        assertThat(category.get("parentId")).isEqualTo(parentId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateCategory_removeParent_returnsCategory() {
        var parentResult = graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        String parentId = (String) ((Map<String, Object>) data(parentResult).get("createCategory")).get("id");

        var childResult = graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { id }
                }
                """, Map.of("input", Map.of("name", "Groceries", "parentId", parentId)));
        String childId = (String) ((Map<String, Object>) data(childResult).get("createCategory")).get("id");

        // Use HashMap to pass explicit null for parentId
        var input = new java.util.HashMap<String, Object>();
        input.put("parentId", null);
        var result = graphql("""
                mutation($categoryId: ID!, $input: UpdateCategoryInput!) {
                    updateCategory(categoryId: $categoryId, input: $input) { id parentId }
                }
                """, Map.of("categoryId", childId, "input", input));

        assertThat(result.get("errors")).isNull();
        var category = (Map<String, Object>) data(result).get("updateCategory");
        assertThat(category.get("parentId")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateCategory_parentIdOmitted_doesNotChangeParent() {
        var parentResult = graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        String parentId = (String) ((Map<String, Object>) data(parentResult).get("createCategory")).get("id");

        var childResult = graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { id }
                }
                """, Map.of("input", Map.of("name", "Groceries", "parentId", parentId)));
        String childId = (String) ((Map<String, Object>) data(childResult).get("createCategory")).get("id");

        // Update name only — parentId not included in input
        var result = graphql("""
                mutation($categoryId: ID!, $input: UpdateCategoryInput!) {
                    updateCategory(categoryId: $categoryId, input: $input) { id name parentId }
                }
                """, Map.of("categoryId", childId, "input", Map.of("name", "Grocery Store")));

        assertThat(result.get("errors")).isNull();
        var category = (Map<String, Object>) data(result).get("updateCategory");
        assertThat(category.get("name")).isEqualTo("Grocery Store");
        assertThat(category.get("parentId")).isEqualTo(parentId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateCategory_selfParent_returnsError() {
        var createResult = graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        String categoryId = (String) ((Map<String, Object>) data(createResult).get("createCategory")).get("id");

        var result = graphql("""
                mutation($categoryId: ID!, $input: UpdateCategoryInput!) {
                    updateCategory(categoryId: $categoryId, input: $input) { id }
                }
                """, Map.of("categoryId", categoryId, "input", Map.of("parentId", categoryId)));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateCategory_parentTooDeep_returnsError() {
        var rootResult = graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        String rootId = (String) ((Map<String, Object>) data(rootResult).get("createCategory")).get("id");

        var childResult = graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { id }
                }
                """, Map.of("input", Map.of("name", "Groceries", "parentId", rootId)));
        String childId = (String) ((Map<String, Object>) data(childResult).get("createCategory")).get("id");

        // Create an orphan, try to move it under the child (would be depth 3)
        var orphanResult = graphql("""
                mutation { createCategory(input: { name: "Organic" }) { id } }
                """);
        String orphanId = (String) ((Map<String, Object>) data(orphanResult).get("createCategory")).get("id");

        var result = graphql("""
                mutation($categoryId: ID!, $input: UpdateCategoryInput!) {
                    updateCategory(categoryId: $categoryId, input: $input) { id }
                }
                """, Map.of("categoryId", orphanId, "input", Map.of("parentId", childId)));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateCategory_categoryWithChildren_cannotBeNested() {
        // Food has child Groceries. Try to move Food under Transport → would create depth 3.
        var foodResult = graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        String foodId = (String) ((Map<String, Object>) data(foodResult).get("createCategory")).get("id");

        graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { id }
                }
                """, Map.of("input", Map.of("name", "Groceries", "parentId", foodId)));

        var transportResult = graphql("""
                mutation { createCategory(input: { name: "Transport" }) { id } }
                """);
        String transportId = (String) ((Map<String, Object>) data(transportResult).get("createCategory")).get("id");

        // Try to make Food (which has children) a child of Transport
        var result = graphql("""
                mutation($categoryId: ID!, $input: UpdateCategoryInput!) {
                    updateCategory(categoryId: $categoryId, input: $input) { id }
                }
                """, Map.of("categoryId", foodId, "input", Map.of("parentId", transportId)));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateCategory_parentNotFound_returnsError() {
        var createResult = graphql("""
                mutation { createCategory(input: { name: "Groceries" }) { id } }
                """);
        String categoryId = (String) ((Map<String, Object>) data(createResult).get("createCategory")).get("id");

        UUID fakeParentId = UUID.randomUUID();
        var result = graphql("""
                mutation($categoryId: ID!, $input: UpdateCategoryInput!) {
                    updateCategory(categoryId: $categoryId, input: $input) { id }
                }
                """, Map.of("categoryId", categoryId, "input", Map.of("parentId", fakeParentId.toString())));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteCategory_returnsTrue() {
        var createResult = graphql("""
                mutation { createCategory(input: { name: "Delete Me" }) { id } }
                """);
        String categoryId = (String) ((Map<String, Object>) data(createResult).get("createCategory")).get("id");

        var result = graphql("""
                mutation($categoryId: ID!) { deleteCategory(categoryId: $categoryId) }
                """, Map.of("categoryId", categoryId));

        assertThat(result.get("errors")).isNull();
        assertThat(data(result).get("deleteCategory")).isEqualTo(true);

        // Verify deleted
        var getResult = graphql("""
                query($categoryId: ID!) { category(categoryId: $categoryId) { id } }
                """, Map.of("categoryId", categoryId));
        assertThat(getResult.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteCategory_parentSetsChildrenToNull() {
        var parentResult = graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        String parentId = (String) ((Map<String, Object>) data(parentResult).get("createCategory")).get("id");

        var childResult = graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { id }
                }
                """, Map.of("input", Map.of("name", "Groceries", "parentId", parentId)));
        String childId = (String) ((Map<String, Object>) data(childResult).get("createCategory")).get("id");

        // Delete parent
        graphql("""
                mutation($categoryId: ID!) { deleteCategory(categoryId: $categoryId) }
                """, Map.of("categoryId", parentId));

        // Child should still exist with null parentId (ON DELETE SET NULL)
        var result = graphql("""
                query($categoryId: ID!) { category(categoryId: $categoryId) { name parentId } }
                """, Map.of("categoryId", childId));

        assertThat(result.get("errors")).isNull();
        var category = (Map<String, Object>) data(result).get("category");
        assertThat(category.get("name")).isEqualTo("Groceries");
        assertThat(category.get("parentId")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void workspaceIsolation_cannotAccessOtherWorkspaceCategories() {
        var createResult = graphql("""
                mutation { createCategory(input: { name: "Isolated" }) { id } }
                """);
        String categoryId = (String) ((Map<String, Object>) data(createResult).get("createCategory")).get("id");

        // Mint token for a different workspace
        UUID workspace2Id = UUID.randomUUID();
        String token2 = tokenService.mintToken(userId, workspace2Id);

        // Try to access with different workspace token
        Map<String, Object> body = Map.of("query", """
                query($categoryId: ID!) { category(categoryId: $categoryId) { id } }
                """, "variables", Map.of("categoryId", categoryId));
        @SuppressWarnings("unchecked")
        var response = restTemplate.exchange(
                "/graphql", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token2)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("errors")).isNotNull();
    }

    @Test
    void getCategory_notFound_returnsError() {
        UUID randomId = UUID.randomUUID();

        var result = graphql("""
                query($categoryId: ID!) { category(categoryId: $categoryId) { id } }
                """, Map.of("categoryId", randomId.toString()));

        assertThat(result.get("errors")).isNotNull();
    }

    // ── Display Order Tests ─────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void createCategories_autoAssignsDisplayOrder() {
        var r1 = graphql("""
                mutation { createCategory(input: { name: "First" }) { displayOrder } }
                """);
        var r2 = graphql("""
                mutation { createCategory(input: { name: "Second" }) { displayOrder } }
                """);
        var r3 = graphql("""
                mutation { createCategory(input: { name: "Third" }) { displayOrder } }
                """);

        assertThat(((Number) ((Map<String, Object>) data(r1).get("createCategory")).get("displayOrder")).intValue()).isEqualTo(0);
        assertThat(((Number) ((Map<String, Object>) data(r2).get("createCategory")).get("displayOrder")).intValue()).isEqualTo(1);
        assertThat(((Number) ((Map<String, Object>) data(r3).get("createCategory")).get("displayOrder")).intValue()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createChildCategories_autoAssignsOrderWithinParent() {
        var parentResult = graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        String parentId = (String) ((Map<String, Object>) data(parentResult).get("createCategory")).get("id");

        var c1 = graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { displayOrder }
                }
                """, Map.of("input", Map.of("name", "Groceries", "parentId", parentId)));
        var c2 = graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { displayOrder }
                }
                """, Map.of("input", Map.of("name", "Dining", "parentId", parentId)));

        assertThat(((Number) ((Map<String, Object>) data(c1).get("createCategory")).get("displayOrder")).intValue()).isEqualTo(0);
        assertThat(((Number) ((Map<String, Object>) data(c2).get("createCategory")).get("displayOrder")).intValue()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void moveCategory_updatesOrderAndShiftsSiblings() {
        graphql("""
                mutation { createCategory(input: { name: "Alpha" }) { id } }
                """);
        graphql("""
                mutation { createCategory(input: { name: "Beta" }) { id } }
                """);
        var r3 = graphql("""
                mutation { createCategory(input: { name: "Gamma" }) { id } }
                """);
        String id3 = (String) ((Map<String, Object>) data(r3).get("createCategory")).get("id");

        // Move Gamma (position 2) to position 0
        var moveResult = graphql("""
                mutation($categoryId: ID!) { moveCategory(categoryId: $categoryId, displayOrder: 0) }
                """, Map.of("categoryId", id3));
        assertThat(moveResult.get("errors")).isNull();
        assertThat(data(moveResult).get("moveCategory")).isEqualTo(true);

        // Verify via tree: Gamma=0, Alpha=1, Beta=2
        var treeResult = graphql("{ categoryTree { name } }");
        var roots = (List<Map<String, Object>>) data(treeResult).get("categoryTree");

        assertThat(roots.get(0).get("name")).isEqualTo("Gamma");
        assertThat(roots.get(1).get("name")).isEqualTo("Alpha");
        assertThat(roots.get(2).get("name")).isEqualTo("Beta");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reparentCategory_appendsToEndOfNewGroup() {
        var parentResult = graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        String parentId = (String) ((Map<String, Object>) data(parentResult).get("createCategory")).get("id");

        graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { id }
                }
                """, Map.of("input", Map.of("name", "Groceries", "parentId", parentId)));

        // Create standalone then move under parent
        var standaloneResult = graphql("""
                mutation { createCategory(input: { name: "Snacks" }) { id } }
                """);
        String snacksId = (String) ((Map<String, Object>) data(standaloneResult).get("createCategory")).get("id");

        var result = graphql("""
                mutation($categoryId: ID!, $input: UpdateCategoryInput!) {
                    updateCategory(categoryId: $categoryId, input: $input) { id displayOrder }
                }
                """, Map.of("categoryId", snacksId, "input", Map.of("parentId", parentId)));

        assertThat(result.get("errors")).isNull();
        var category = (Map<String, Object>) data(result).get("updateCategory");
        // Snacks should be displayOrder 1 (after Groceries at 0)
        assertThat(((Number) category.get("displayOrder")).intValue()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ungroupCategory_appendsToEndOfRoots() {
        var parentResult = graphql("""
                mutation { createCategory(input: { name: "Food" }) { id } }
                """);
        String parentId = (String) ((Map<String, Object>) data(parentResult).get("createCategory")).get("id");

        var childResult = graphql("""
                mutation($input: CreateCategoryInput!) {
                    createCategory(input: $input) { id }
                }
                """, Map.of("input", Map.of("name", "Groceries", "parentId", parentId)));
        String childId = (String) ((Map<String, Object>) data(childResult).get("createCategory")).get("id");

        // Create another root
        graphql("""
                mutation { createCategory(input: { name: "Bills" }) { id } }
                """);

        // Remove parent (ungroup) via explicit null
        var input = new java.util.HashMap<String, Object>();
        input.put("parentId", null);
        var result = graphql("""
                mutation($categoryId: ID!, $input: UpdateCategoryInput!) {
                    updateCategory(categoryId: $categoryId, input: $input) { id displayOrder parentId }
                }
                """, Map.of("categoryId", childId, "input", input));

        assertThat(result.get("errors")).isNull();
        var category = (Map<String, Object>) data(result).get("updateCategory");
        // Groceries should be at end of roots (Food=0, Bills=1, Groceries=2)
        assertThat(((Number) category.get("displayOrder")).intValue()).isEqualTo(2);
        assertThat(category.get("parentId")).isNull();
    }
}
