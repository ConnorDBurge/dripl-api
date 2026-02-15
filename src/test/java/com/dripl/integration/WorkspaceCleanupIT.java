package com.dripl.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class WorkspaceCleanupIT extends BaseIntegrationTest {

    @Autowired
    private com.dripl.workspace.repository.WorkspaceRepository workspaceRepository;

    @Test
    void removingLastMember_deletesOrphanedWorkspace() {
        // Owner bootstraps → gets a default workspace
        var owner = bootstrapUser("cleanup-owner-%s@test.com".formatted(System.nanoTime()), "Owner", "Test");
        String ownerToken = (String) owner.get("token");
        String workspaceId = (String) owner.get("lastWorkspaceId");

        // Add a second member
        var member = bootstrapUser("cleanup-member-%s@test.com".formatted(System.nanoTime()), "Member", "Test");
        String memberId = (String) member.get("id");

        restTemplate.exchange(
                "/api/v1/workspaces/current/members", HttpMethod.POST,
                new HttpEntity<>("""
                        {"userId":"%s","roles":["READ"]}
                        """.formatted(memberId), authHeaders(ownerToken)),
                Map.class);

        // Remove the second member — workspace should still exist (owner remains)
        restTemplate.exchange(
                "/api/v1/workspaces/current/members/" + memberId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ownerToken)),
                Void.class);

        // Give async listener time to run
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(workspaceRepository.findById(java.util.UUID.fromString(workspaceId))).isPresent();
        });

        // Now remove the owner (last member) — workspace should be cleaned up
        String ownerId = (String) owner.get("id");
        restTemplate.exchange(
                "/api/v1/workspaces/current/members/" + ownerId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ownerToken)),
                Void.class);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(workspaceRepository.findById(java.util.UUID.fromString(workspaceId))).isEmpty();
        });
    }

    @Test
    void deleteUser_cleansUpOrphanedWorkspaces() {
        // Bootstrap user with default workspace
        var user = bootstrapUser("cleanup-delete-%s@test.com".formatted(System.nanoTime()), "Delete", "Me");
        String token = (String) user.get("token");
        String workspaceId = (String) user.get("lastWorkspaceId");

        // Provision a second workspace
        var provision = restTemplate.exchange(
                "/api/v1/workspaces", HttpMethod.POST,
                new HttpEntity<>("""
                        {"name":"Second WS"}
                        """, authHeaders(token)),
                Map.class);
        String secondToken = (String) provision.getBody().get("token");
        String secondWorkspaceId = (String) provision.getBody().get("id");

        // Delete the user
        restTemplate.exchange(
                "/api/v1/users/self", HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(secondToken)),
                Void.class);

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
        String workspaceId = (String) owner.get("lastWorkspaceId");

        // Add member
        var member = bootstrapUser("shared-member-%s@test.com".formatted(System.nanoTime()), "Member", "Shared");
        String memberId = (String) member.get("id");

        restTemplate.exchange(
                "/api/v1/workspaces/current/members", HttpMethod.POST,
                new HttpEntity<>("""
                        {"userId":"%s","roles":["READ","WRITE"]}
                        """.formatted(memberId), authHeaders(ownerToken)),
                Map.class);

        // Remove the member
        restTemplate.exchange(
                "/api/v1/workspaces/current/members/" + memberId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ownerToken)),
                Void.class);

        // Wait a moment then verify workspace still exists (owner is still a member)
        await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(workspaceRepository.findById(java.util.UUID.fromString(workspaceId))).isPresent();
        });
    }
}
