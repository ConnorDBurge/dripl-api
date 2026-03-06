package com.dripl.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SuppressWarnings("unchecked")
class WorkspaceCleanupIT extends BaseIntegrationTest {

    @Autowired
    private com.dripl.workspace.repository.WorkspaceRepository workspaceRepository;

    @Test
    void removingLastMember_deletesOrphanedWorkspace() {
        // Owner bootstraps → gets a default workspace
        var owner = bootstrapUser("cleanup-owner-%s@test.com".formatted(System.nanoTime()), "Owner", "Test");
        String ownerToken = (String) owner.get("token");
        String workspaceId = (String) owner.get("workspaceId");

        // Add a second member
        var member = bootstrapUser("cleanup-member-%s@test.com".formatted(System.nanoTime()), "Member", "Test");
        String memberId = (String) member.get("userId");

        graphqlData(ownerToken, """
                mutation { addWorkspaceMember(input: { userId: "%s", roles: [READ] }) { userId } }
                """.formatted(memberId));

        // Remove the second member — workspace should still exist (owner remains)
        graphqlData(ownerToken, """
                mutation { removeWorkspaceMember(userId: "%s") }
                """.formatted(memberId));

        // Give async listener time to run
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(workspaceRepository.findById(java.util.UUID.fromString(workspaceId))).isPresent();
        });

        // Now remove the owner (last member) — workspace should be cleaned up
        String ownerId = (String) owner.get("userId");
        graphqlData(ownerToken, """
                mutation { removeWorkspaceMember(userId: "%s") }
                """.formatted(ownerId));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(workspaceRepository.findById(java.util.UUID.fromString(workspaceId))).isEmpty();
        });
    }

    @Test
    void deleteUser_cleansUpOrphanedWorkspaces() {
        // Bootstrap user with default workspace
        var user = bootstrapUser("cleanup-delete-%s@test.com".formatted(System.nanoTime()), "Delete", "Me");
        String token = (String) user.get("token");
        String workspaceId = (String) user.get("workspaceId");

        // Provision a second workspace
        var provisionData = graphqlData(token, """
                mutation { provisionWorkspace(input: { name: "Second WS" }) { id token } }
                """);
        var provisioned = (Map<String, Object>) provisionData.get("provisionWorkspace");
        String secondToken = (String) provisioned.get("token");
        String secondWorkspaceId = (String) provisioned.get("id");

        // Delete the user via GraphQL
        graphqlData(secondToken, """
                mutation { deleteSelf }
                """);

        // Both workspaces should be cleaned up async
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(workspaceRepository.findById(java.util.UUID.fromString(workspaceId))).isEmpty();
            assertThat(workspaceRepository.findById(java.util.UUID.fromString(secondWorkspaceId))).isEmpty();
        });
    }

    @Test
    void removingMember_fromSharedWorkspace_doesNotDeleteWorkspace() {
        // Owner bootstraps
        var owner = bootstrapUser("shared-owner-%s@test.com".formatted(System.nanoTime()), "Owner", "Shared");
        String ownerToken = (String) owner.get("token");
        String workspaceId = (String) owner.get("workspaceId");

        // Add member
        var member = bootstrapUser("shared-member-%s@test.com".formatted(System.nanoTime()), "Member", "Shared");
        String memberId = (String) member.get("userId");

        graphqlData(ownerToken, """
                mutation { addWorkspaceMember(input: { userId: "%s", roles: [READ, WRITE] }) { userId } }
                """.formatted(memberId));

        // Remove the member
        graphqlData(ownerToken, """
                mutation { removeWorkspaceMember(userId: "%s") }
                """.formatted(memberId));

        // Wait a moment then verify workspace still exists (owner is still a member)
        await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(workspaceRepository.findById(java.util.UUID.fromString(workspaceId))).isPresent();
        });
    }
}
