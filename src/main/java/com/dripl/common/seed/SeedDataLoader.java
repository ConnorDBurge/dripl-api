package com.dripl.common.seed;

import com.dripl.account.dto.CreateAccountDto;
import com.dripl.account.service.AccountService;
import com.dripl.merchant.dto.CreateMerchantDto;
import com.dripl.merchant.service.MerchantService;
import com.dripl.user.entity.User;
import com.dripl.user.service.UserService;
import com.dripl.workspace.dto.CreateWorkspaceDto;
import com.dripl.workspace.entity.Workspace;
import com.dripl.workspace.membership.enums.Role;
import com.dripl.workspace.membership.service.MembershipService;
import com.dripl.workspace.service.WorkspaceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class SeedDataLoader implements CommandLineRunner {

    private final UserService userService;
    private final WorkspaceService workspaceService;
    private final MembershipService membershipService;
    private final AccountService accountService;
    private final MerchantService merchantService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        log.info("=== Seed Data Loader ===");

        setSeedAuthentication();

        try {
            wipeDatabase();

            Map<String, User> usersByEmail = seedUsers();
            seedWorkspaces(usersByEmail);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void setSeedAuthentication() {
        Claims claims = Jwts.claims().subject("seed-data@dripl.dev").build();
        var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Map<String, User> seedUsers() throws Exception {
        List<Map<String, Object>> seedUsers = readJson("seed-data/users.json");
        Map<String, User> usersByEmail = new LinkedHashMap<>();

        for (Map<String, Object> seedUser : seedUsers) {
            String email = (String) seedUser.get("email");
            String givenName = (String) seedUser.get("givenName");
            String familyName = (String) seedUser.get("familyName");

            User user = userService.bootstrapUser(email, givenName, familyName);
            usersByEmail.put(email, user);
            log.info("Seeded user: {} {} <{}>", givenName, familyName, email);
        }

        return usersByEmail;
    }

    private void seedWorkspaces(Map<String, User> usersByEmail) throws Exception {
        List<Map<String, Object>> seedWorkspaces = readJson("seed-data/workspaces.json");

        Set<String> defaultRenamed = new HashSet<>();
        int totalAccounts = 0;
        int totalMerchants = 0;

        for (Map<String, Object> seedWorkspace : seedWorkspaces) {
            String ownerEmail = (String) seedWorkspace.get("ownerEmail");
            String workspaceName = (String) seedWorkspace.get("name");
            User owner = usersByEmail.get(ownerEmail);

            UUID workspaceId;
            if (!defaultRenamed.contains(ownerEmail)) {
                // Rename the default workspace created by bootstrap
                workspaceId = owner.getLastWorkspaceId();
                jdbcTemplate.update("UPDATE workspaces SET name = ? WHERE id = ?",
                        workspaceName, workspaceId);
                defaultRenamed.add(ownerEmail);
            } else {
                Workspace workspace = workspaceService.provisionWorkspace(
                        owner.getId(), CreateWorkspaceDto.builder().name(workspaceName).build());
                workspaceId = workspace.getId();
            }

            // Add members
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> members = (List<Map<String, Object>>) seedWorkspace.get("members");
            if (members != null) {
                for (Map<String, Object> member : members) {
                    String memberEmail = (String) member.get("email");
                    User memberUser = usersByEmail.get(memberEmail);
                    if (memberUser == null) {
                        log.warn("Member {} not found, skipping", memberEmail);
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    List<String> roleNames = (List<String>) member.get("roles");
                    Set<Role> roles = roleNames.stream()
                            .map(Role::valueOf)
                            .collect(Collectors.toSet());
                    membershipService.createMembership(memberUser.getId(), workspaceId, roles);
                }
            }

            // Create accounts
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> accounts = (List<Map<String, Object>>) seedWorkspace.get("accounts");
            if (accounts != null) {
                for (Map<String, Object> seedAccount : accounts) {
                    CreateAccountDto dto = objectMapper.convertValue(seedAccount, CreateAccountDto.class);
                    accountService.createAccount(workspaceId, dto);
                    totalAccounts++;
                }
            }

            // Create merchants
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> merchants = (List<Map<String, Object>>) seedWorkspace.get("merchants");
            if (merchants != null) {
                for (Map<String, Object> seedMerchant : merchants) {
                    CreateMerchantDto dto = objectMapper.convertValue(seedMerchant, CreateMerchantDto.class);
                    merchantService.createMerchant(workspaceId, dto);
                    totalMerchants++;
                }
            }

            log.info("Seeded workspace: '{}'", workspaceName);
        }

        logSummary(usersByEmail.size(), seedWorkspaces.size(), totalAccounts, totalMerchants);
    }

    private List<Map<String, Object>> readJson(String path) throws Exception {
        InputStream inputStream = new ClassPathResource(path).getInputStream();
        return objectMapper.readValue(inputStream, new TypeReference<>() {});
    }

    private void wipeDatabase() {
        jdbcTemplate.execute("DELETE FROM workspaces");
        jdbcTemplate.execute("DELETE FROM users");
    }

    private void logSummary(int users, int workspaces, int accounts, int merchants) {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║                    SEED DATA SUMMARY                        ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║  Users: {}   Workspaces: {}   Accounts: {}   Merchants: {}",
                users, workspaces, accounts, merchants);
        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("");
    }
}
