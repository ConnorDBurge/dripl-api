package com.dripl.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class MemberManagementIT extends BaseIntegrationTest {

    private String ownerToken;
    private Map<String, Object> owner;
    private Map<String, Object> member;

    @BeforeEach
    void setUp() {
        owner = bootstrapUser("owner-%s@test.com".formatted(System.nanoTime()), "Owner", "User");
        ownerToken = (String) owner.get("token");

        // Create a second user to add as member
        member = bootstrapUser("member-%s@test.com".formatted(System.nanoTime()), "Member", "User");
    }

    @Test
    void listMembers_afterBootstrap_returnsOwner() {
        var data = graphqlData(ownerToken, "{ workspaceMembers { userId email roles } }");
        var members = (List<Map<String, Object>>) data.get("workspaceMembers");

        assertThat(members).hasSize(1);
    }

    @Test
    void addMember_returnsNewMembership() {
        String memberId = (String) member.get("userId");

        var data = graphqlData(ownerToken, """
                mutation { addWorkspaceMember(input: { userId: "%s", roles: [READ] }) { userId email roles } }
                """.formatted(memberId));
        var m = (Map<String, Object>) data.get("addWorkspaceMember");

        assertThat(m.get("userId")).isEqualTo(memberId);

        // Verify member appears in list
        var listData = graphqlData(ownerToken, "{ workspaceMembers { userId } }");
        var members = (List<Map<String, Object>>) listData.get("workspaceMembers");
        assertThat(members).hasSize(2);
    }

    @Test
    void getMemberById_returnsMembership() {
        String ownerId = (String) owner.get("userId");

        var data = graphqlData(ownerToken, """
                { workspaceMember(userId: "%s") { userId email } }
                """.formatted(ownerId));
        var m = (Map<String, Object>) data.get("workspaceMember");

        assertThat(m.get("userId")).isEqualTo(ownerId);
    }

    @Test
    void getMemberById_nonExistent_returnsError() {
        var response = graphql(ownerToken, """
                { workspaceMember(userId: "00000000-0000-0000-0000-000000000000") { userId } }
                """);

        assertThat(response.get("errors")).isNotNull();
    }

    @Test
    void updateMember_changesRoles() {
        // Add member first
        String memberId = (String) member.get("userId");
        graphqlData(ownerToken, """
                mutation { addWorkspaceMember(input: { userId: "%s", roles: [READ] }) { userId } }
                """.formatted(memberId));

        // Update their roles
        var data = graphqlData(ownerToken, """
                mutation { updateWorkspaceMember(userId: "%s", input: { roles: [READ, WRITE] }) { userId roles } }
                """.formatted(memberId));
        var m = (Map<String, Object>) data.get("updateWorkspaceMember");

        List<String> roles = (List<String>) m.get("roles");
        assertThat(roles).contains("READ", "WRITE");
    }

    @Test
    void removeMember_succeeds() {
        // Add member first
        String memberId = (String) member.get("userId");
        graphqlData(ownerToken, """
                mutation { addWorkspaceMember(input: { userId: "%s", roles: [READ] }) { userId } }
                """.formatted(memberId));

        // Remove them
        var data = graphqlData(ownerToken, """
                mutation { removeWorkspaceMember(userId: "%s") }
                """.formatted(memberId));

        assertThat(data.get("removeWorkspaceMember")).isEqualTo(true);

        // Verify removed from list
        var listData = graphqlData(ownerToken, "{ workspaceMembers { userId } }");
        var members = (List<Map<String, Object>>) listData.get("workspaceMembers");
        assertThat(members).hasSize(1);
    }
}
