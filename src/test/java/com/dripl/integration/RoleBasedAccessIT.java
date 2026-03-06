package com.dripl.integration;

import com.dripl.auth.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class RoleBasedAccessIT extends BaseIntegrationTest {

    @Autowired
    private JwtUtil jwtUtil;

    private String ownerToken;
    private String readOnlyToken;
    private Map<String, Object> owner;

    @BeforeEach
    void setUp() {
        owner = bootstrapUser("rbac-owner-%s@test.com".formatted(System.nanoTime()), "RBAC", "Owner");
        ownerToken = (String) owner.get("token");

        // Create a second user and add as READ-only member
        Map<String, Object> reader = bootstrapUser(
                "rbac-reader-%s@test.com".formatted(System.nanoTime()), "RBAC", "Reader");
        String readerId = (String) reader.get("userId");
        String ownerWorkspaceId = (String) owner.get("workspaceId");

        // Add a reader to the owner's workspace
        graphqlData(ownerToken, """
                mutation { addWorkspaceMember(input: { userId: "%s", roles: [READ] }) { userId } }
                """.formatted(readerId));

        // Mint a token for the reader in the owner's workspace
        readOnlyToken = jwtUtil.generateToken(
                UUID.fromString(readerId),
                UUID.fromString(ownerWorkspaceId),
                "rbac-reader@test.com",
                List.of("READ"));
    }

    @Test
    void readOnly_canGetCurrentWorkspace() {
        var data = graphqlData(readOnlyToken, "{ currentWorkspace { id name } }");
        var ws = (Map<String, Object>) data.get("currentWorkspace");

        assertThat(ws).isNotNull();
    }

    @Test
    void readOnly_canGetMembers() {
        var data = graphqlData(readOnlyToken, "{ workspaceMembers { userId } }");
        var members = (List<Map<String, Object>>) data.get("workspaceMembers");

        assertThat(members).isNotNull();
    }

    @Test
    void readOnly_cannotUpdateWorkspace() {
        var response = graphql(readOnlyToken, """
                mutation { updateCurrentWorkspace(input: { name: "Hijacked" }) { id name } }
                """);

        assertThat(response.get("errors")).isNotNull();
    }

    @Test
    void readOnly_cannotAddMembers() {
        var response = graphql(readOnlyToken, """
                mutation { addWorkspaceMember(input: { userId: "%s", roles: [READ] }) { userId } }
                """.formatted(UUID.randomUUID()));

        assertThat(response.get("errors")).isNotNull();
    }

    @Test
    void readOnly_cannotUpdateMembers() {
        String ownerId = (String) owner.get("userId");

        var response = graphql(readOnlyToken, """
                mutation { updateWorkspaceMember(userId: "%s", input: { roles: [READ] }) { userId } }
                """.formatted(ownerId));

        assertThat(response.get("errors")).isNotNull();
    }

    @Test
    void readOnly_cannotRemoveMembers() {
        String ownerId = (String) owner.get("userId");

        var response = graphql(readOnlyToken, """
                mutation { removeWorkspaceMember(userId: "%s") }
                """.formatted(ownerId));

        assertThat(response.get("errors")).isNotNull();
    }

    @Test
    void readOnly_cannotDeleteResource() {
        // Owner creates a merchant
        String merchantId = createMerchant(ownerToken, "Test Merchant");

        // READ-only user cannot delete it
        var response = graphql(readOnlyToken, """
                mutation { deleteMerchant(merchantId: "%s") }
                """.formatted(merchantId));

        assertThat(response.get("errors")).isNotNull();
    }

    @Test
    void writeOnly_cannotDeleteResource() {
        String ownerWorkspaceId = (String) owner.get("workspaceId");

        // Create a WRITE-only user (no DELETE)
        Map<String, Object> writer = bootstrapUser(
                "rbac-writer-%s@test.com".formatted(System.nanoTime()), "RBAC", "Writer");
        String writerId = (String) writer.get("userId");

        graphqlData(ownerToken, """
                mutation { addWorkspaceMember(input: { userId: "%s", roles: [READ, WRITE] }) { userId } }
                """.formatted(writerId));

        String writeOnlyToken = jwtUtil.generateToken(
                UUID.fromString(writerId),
                UUID.fromString(ownerWorkspaceId),
                "rbac-writer@test.com",
                List.of("READ", "WRITE"));

        // Writer creates a merchant (WRITE works)
        String merchantId = createMerchant(writeOnlyToken, "Writer Merchant");

        // Writer cannot delete it (no DELETE role)
        var response = graphql(writeOnlyToken, """
                mutation { deleteMerchant(merchantId: "%s") }
                """.formatted(merchantId));

        assertThat(response.get("errors")).isNotNull();
    }

    @Test
    void owner_canDoEverything() {
        // GET current workspace
        var currentData = graphqlData(ownerToken, "{ currentWorkspace { id name } }");
        assertThat(currentData.get("currentWorkspace")).isNotNull();

        // Update current workspace
        var updateData = graphqlData(ownerToken, """
                mutation { updateCurrentWorkspace(input: { name: "Owner Renamed" }) { id name } }
                """);
        assertThat(updateData.get("updateCurrentWorkspace")).isNotNull();

        // GET members
        var membersData = graphqlData(ownerToken, "{ workspaceMembers { userId } }");
        assertThat(membersData.get("workspaceMembers")).isNotNull();
    }
}
