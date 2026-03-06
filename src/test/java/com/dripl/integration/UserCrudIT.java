package com.dripl.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserCrudIT extends BaseIntegrationTest {

    private String token;
    private Map<String, Object> user;

    @BeforeEach
    void setUp() {
        user = bootstrapUser("crud-user-%s@test.com".formatted(System.nanoTime()), "Crud", "User");
        token = (String) user.get("token");
    }

    @Test
    @SuppressWarnings("unchecked")
    void self_returnsCurrentUser() {
        var data = graphqlData(token, """
                query { self { id email givenName familyName isActive lastWorkspaceId } }
                """);
        var self = (Map<String, Object>) data.get("self");

        assertThat(self.get("id")).isEqualTo(user.get("userId"));
        assertThat(self.get("email")).asString().startsWith("crud-user-");
        assertThat(self.get("givenName")).isEqualTo("Crud");
        assertThat(self.get("familyName")).isEqualTo("User");
        assertThat(self.get("isActive")).isEqualTo(true);
        assertThat(self.get("lastWorkspaceId")).isEqualTo(user.get("workspaceId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateSelf_updatesName() {
        var data = graphqlData(token, """
                mutation($input: UpdateUserInput!) {
                    updateSelf(input: $input) { id email givenName familyName }
                }
                """, Map.of("input", Map.of("givenName", "Updated")));
        var updated = (Map<String, Object>) data.get("updateSelf");

        assertThat(updated.get("givenName")).isEqualTo("Updated");
        assertThat(updated.get("familyName")).isEqualTo("User");

        // Verify change persisted
        var verifyData = graphqlData(token, """
                query { self { givenName } }
                """);
        var verify = (Map<String, Object>) verifyData.get("self");
        assertThat(verify.get("givenName")).isEqualTo("Updated");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateSelf_duplicateEmail_returnsError() {
        bootstrapUser("existing@test.com", "Existing", "User");

        var result = graphql(token, """
                mutation($input: UpdateUserInput!) {
                    updateSelf(input: $input) { id email }
                }
                """, Map.of("input", Map.of("email", "existing@test.com")));

        assertThat(result.get("errors")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteSelf_removesUser() {
        var data = graphqlData(token, """
                mutation { deleteSelf }
                """);

        assertThat(data.get("deleteSelf")).isEqualTo(true);

        // User should no longer exist
        var result = graphql(token, """
                query { self { id } }
                """);
        assertThat(result.get("errors")).isNotNull();
    }
}

