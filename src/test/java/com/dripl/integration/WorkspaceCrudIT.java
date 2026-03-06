package com.dripl.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class WorkspaceCrudIT extends BaseIntegrationTest {

    private String token;
    private Map<String, Object> user;

    @BeforeEach
    void setUp() {
        user = bootstrapUser("ws-user-%s@test.com".formatted(System.nanoTime()), "WS", "User");
        token = (String) user.get("token");
    }

    @Test
    void listWorkspaces_afterBootstrap_returnsDefaultWorkspace() {
        var data = graphqlData(token, "{ workspaces { id name status } }");
        var workspaces = (List<Map<String, Object>>) data.get("workspaces");

        assertThat(workspaces).hasSize(1);
    }

    @Test
    void provisionWorkspace_createsAndSwitches() {
        var data = graphqlData(token, """
                mutation { provisionWorkspace(input: { name: "Second Workspace" }) { id name token } }
                """);
        var ws = (Map<String, Object>) data.get("provisionWorkspace");

        assertThat(ws.get("name")).isEqualTo("Second Workspace");
        assertThat(ws.get("token")).isNotNull();

        // Should now have 2 workspaces
        String newToken = (String) ws.get("token");
        var listData = graphqlData(newToken, "{ workspaces { id } }");
        var workspaces = (List<Map<String, Object>>) listData.get("workspaces");
        assertThat(workspaces).hasSize(2);
    }

    @Test
    void provisionWorkspace_duplicateName_returnsError() {
        var response = graphql(token, """
                mutation { provisionWorkspace(input: { name: "WS's Workspace" }) { id name token } }
                """);

        assertThat(response.get("errors")).isNotNull();
    }

    @Test
    void switchWorkspace_validWorkspace_returnsNewToken() {
        // Create a second workspace
        var provisionData = graphqlData(token, """
                mutation { provisionWorkspace(input: { name: "Switch Target" }) { id name token } }
                """);
        var provisioned = (Map<String, Object>) provisionData.get("provisionWorkspace");
        String newToken = (String) provisioned.get("token");

        // Switch back to original
        String originalWorkspaceId = (String) user.get("workspaceId");
        var switchData = graphqlData(newToken, """
                mutation { switchWorkspace(input: { workspaceId: "%s" }) { id name token } }
                """.formatted(originalWorkspaceId));
        var switched = (Map<String, Object>) switchData.get("switchWorkspace");

        assertThat(switched.get("id")).isEqualTo(originalWorkspaceId);
        assertThat(switched.get("token")).isNotNull();
    }

    @Test
    void getCurrentWorkspace_returnsWorkspace() {
        var data = graphqlData(token, "{ currentWorkspace { id name status } }");
        var ws = (Map<String, Object>) data.get("currentWorkspace");

        assertThat(ws.get("name")).isEqualTo("WS's Workspace");
        assertThat(ws.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void updateCurrentWorkspace_validName_returnsUpdated() {
        var data = graphqlData(token, """
                mutation { updateCurrentWorkspace(input: { name: "Renamed Workspace" }) { id name } }
                """);
        var ws = (Map<String, Object>) data.get("updateCurrentWorkspace");

        assertThat(ws.get("name")).isEqualTo("Renamed Workspace");
    }
}
