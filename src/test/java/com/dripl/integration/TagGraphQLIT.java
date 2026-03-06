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

class TagGraphQLIT extends BaseIntegrationTest {

    @Autowired private TokenService tokenService;

    private String token;
    private UUID workspaceId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        String email = "gql-tag-%s@test.com".formatted(System.nanoTime());
        var bootstrap = bootstrapUser(email, "Tag", "User");
        token = (String) bootstrap.get("token");
        workspaceId = UUID.fromString((String) bootstrap.get("workspaceId"));
        userId = UUID.fromString((String) bootstrap.get("userId"));
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
    void createTag_withNameOnly_returnsTag() {
        var result = graphql("""
                mutation {
                    createTag(input: { name: "groceries" }) {
                        id name description status workspaceId
                    }
                }
                """);

        assertThat(result.get("errors")).isNull();
        @SuppressWarnings("unchecked")
        var tag = (Map<String, Object>) data(result).get("createTag");
        assertThat(tag.get("name")).isEqualTo("groceries");
        assertThat(tag.get("status")).isEqualTo("ACTIVE");
        assertThat(tag.get("description")).isNull();
        assertThat(tag.get("workspaceId")).isEqualTo(workspaceId.toString());
    }

    @Test
    void createTag_withDescription_returnsTag() {
        var result = graphql("""
                mutation {
                    createTag(input: { name: "travel", description: "Trips and vacations" }) {
                        name description
                    }
                }
                """);

        assertThat(result.get("errors")).isNull();
        @SuppressWarnings("unchecked")
        var tag = (Map<String, Object>) data(result).get("createTag");
        assertThat(tag.get("name")).isEqualTo("travel");
        assertThat(tag.get("description")).isEqualTo("Trips and vacations");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listTags_returnsAllWorkspaceTags() {
        graphql("""
                mutation { createTag(input: { name: "food" }) { id } }
                """);
        graphql("""
                mutation { createTag(input: { name: "entertainment" }) { id } }
                """);

        var result = graphql("{ tags { id name } }");

        assertThat(result.get("errors")).isNull();
        var tags = (List<Map<String, Object>>) data(result).get("tags");
        assertThat(tags).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullCrudLifecycle() {
        // Create
        var createResult = graphql("""
                mutation($input: CreateTagInput!) {
                    createTag(input: $input) { id name status }
                }
                """, Map.of("input", Map.of("name", "coffee")));
        assertThat(createResult.get("errors")).isNull();
        var created = (Map<String, Object>) data(createResult).get("createTag");
        String tagId = (String) created.get("id");
        assertThat(created.get("name")).isEqualTo("coffee");

        // Read single
        var getResult = graphql("""
                query($tagId: ID!) { tag(tagId: $tagId) { id name } }
                """, Map.of("tagId", tagId));
        assertThat(getResult.get("errors")).isNull();
        var fetched = (Map<String, Object>) data(getResult).get("tag");
        assertThat(fetched.get("name")).isEqualTo("coffee");

        // Update name
        var updateResult = graphql("""
                mutation($tagId: ID!, $input: UpdateTagInput!) {
                    updateTag(tagId: $tagId, input: $input) { id name status }
                }
                """, Map.of("tagId", tagId, "input", Map.of("name", "espresso")));
        assertThat(updateResult.get("errors")).isNull();
        var updated = (Map<String, Object>) data(updateResult).get("updateTag");
        assertThat(updated.get("name")).isEqualTo("espresso");
        assertThat(updated.get("status")).isEqualTo("ACTIVE");

        // Update status
        var archiveResult = graphql("""
                mutation($tagId: ID!, $input: UpdateTagInput!) {
                    updateTag(tagId: $tagId, input: $input) { id name status }
                }
                """, Map.of("tagId", tagId, "input", Map.of("status", "ARCHIVED")));
        assertThat(archiveResult.get("errors")).isNull();
        var archived = (Map<String, Object>) data(archiveResult).get("updateTag");
        assertThat(archived.get("status")).isEqualTo("ARCHIVED");
        assertThat(archived.get("name")).isEqualTo("espresso");

        // Delete
        var deleteResult = graphql("""
                mutation($tagId: ID!) { deleteTag(tagId: $tagId) }
                """, Map.of("tagId", tagId));
        assertThat(deleteResult.get("errors")).isNull();
        assertThat(data(deleteResult).get("deleteTag")).isEqualTo(true);

        // Verify deleted
        var afterDelete = graphql("""
                query($tagId: ID!) { tag(tagId: $tagId) { id } }
                """, Map.of("tagId", tagId));
        assertThat(afterDelete.get("errors")).isNotNull();
    }

    @Test
    void createTag_duplicateName_returnsError() {
        graphql("""
                mutation { createTag(input: { name: "duplicate" }) { id } }
                """);

        var result = graphql("""
                mutation { createTag(input: { name: "duplicate" }) { id } }
                """);

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void createTag_duplicateNameCaseInsensitive_returnsError() {
        graphql("""
                mutation { createTag(input: { name: "Groceries" }) { id } }
                """);

        var result = graphql("""
                mutation { createTag(input: { name: "groceries" }) { id } }
                """);

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateTag_toDuplicateName_returnsError() {
        graphql("""
                mutation { createTag(input: { name: "existing" }) { id } }
                """);
        var createResult = graphql("""
                mutation { createTag(input: { name: "other" }) { id } }
                """);
        String tagId = (String) ((Map<String, Object>) data(createResult).get("createTag")).get("id");

        var result = graphql("""
                mutation($tagId: ID!, $input: UpdateTagInput!) {
                    updateTag(tagId: $tagId, input: $input) { id }
                }
                """, Map.of("tagId", tagId, "input", Map.of("name", "existing")));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void getTag_notFound_returnsError() {
        UUID randomId = UUID.randomUUID();

        var result = graphql("""
                query($tagId: ID!) { tag(tagId: $tagId) { id } }
                """, Map.of("tagId", randomId.toString()));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    void workspaceIsolation_cannotAccessOtherWorkspaceTags() {
        var createResult = graphql("""
                mutation { createTag(input: { name: "isolated-tag" }) { id } }
                """);
        @SuppressWarnings("unchecked")
        String tagId = (String) ((Map<String, Object>) data(createResult).get("createTag")).get("id");

        // Mint token for a different workspace
        UUID workspace2Id = UUID.randomUUID();
        String token2 = tokenService.mintToken(userId, workspace2Id);

        // Try to access with different workspace token
        Map<String, Object> body = Map.of("query", """
                query($tagId: ID!) { tag(tagId: $tagId) { id } }
                """, "variables", Map.of("tagId", tagId));
        @SuppressWarnings("unchecked")
        var response = restTemplate.exchange(
                "/graphql", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token2)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("errors")).isNotNull();
    }
}
