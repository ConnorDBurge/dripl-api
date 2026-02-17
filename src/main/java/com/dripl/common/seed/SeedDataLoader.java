package com.dripl.common.seed;

import com.dripl.account.dto.CreateAccountDto;
import com.dripl.account.service.AccountService;
import com.dripl.category.dto.CreateCategoryDto;
import com.dripl.category.entity.Category;
import com.dripl.category.service.CategoryService;
import com.dripl.merchant.dto.CreateMerchantDto;
import com.dripl.merchant.service.MerchantService;
import com.dripl.tag.dto.CreateTagDto;
import com.dripl.tag.service.TagService;
import com.dripl.user.entity.User;
import com.dripl.user.service.UserService;
import com.dripl.workspace.dto.CreateWorkspaceDto;
import com.dripl.workspace.entity.Workspace;
import com.dripl.workspace.membership.enums.Role;
import com.dripl.workspace.membership.service.MembershipService;
import com.dripl.workspace.service.WorkspaceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
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
    private final TagService tagService;
    private final CategoryService categoryService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        Logger driplLogger = (Logger) LoggerFactory.getLogger("com.dripl");
        Level previousLevel = driplLogger.getLevel();
        driplLogger.setLevel(Level.WARN);

        setSeedAuthentication();

        try {
            wipeDatabase();

            Map<String, User> usersByEmail = seedUsers();
            seedWorkspaces(usersByEmail);
        } finally {
            SecurityContextHolder.clearContext();
            driplLogger.setLevel(previousLevel);
        }

        log.info("Local seed data loaded");
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
        }

        return usersByEmail;
    }

    private void seedWorkspaces(Map<String, User> usersByEmail) throws Exception {
        List<Map<String, Object>> seedWorkspaces = readJson("seed-data/workspaces.json");

        Set<String> defaultRenamed = new HashSet<>();

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
                }
            }

            // Create merchants
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> merchants = (List<Map<String, Object>>) seedWorkspace.get("merchants");
            if (merchants != null) {
                for (Map<String, Object> seedMerchant : merchants) {
                    CreateMerchantDto dto = objectMapper.convertValue(seedMerchant, CreateMerchantDto.class);
                    merchantService.createMerchant(workspaceId, dto);
                }
            }

            // Create tags
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tags = (List<Map<String, Object>>) seedWorkspace.get("tags");
            if (tags != null) {
                for (Map<String, Object> seedTag : tags) {
                    CreateTagDto dto = objectMapper.convertValue(seedTag, CreateTagDto.class);
                    tagService.createTag(workspaceId, dto);
                }
            }

            // Create categories (with parent-child nesting)
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> categories = (List<Map<String, Object>>) seedWorkspace.get("categories");
            if (categories != null) {
                for (Map<String, Object> seedCategory : categories) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> children = (List<Map<String, Object>>) seedCategory.get("children");
                    seedCategory.remove("children");

                    CreateCategoryDto parentDto = objectMapper.convertValue(seedCategory, CreateCategoryDto.class);
                    Category parent = categoryService.createCategory(workspaceId, parentDto);

                    if (children != null) {
                        for (Map<String, Object> child : children) {
                            child.put("parentId", parent.getId().toString());
                            CreateCategoryDto childDto = objectMapper.convertValue(child, CreateCategoryDto.class);
                            categoryService.createCategory(workspaceId, childDto);
                        }
                    }
                }
            }
        }
    }

    private List<Map<String, Object>> readJson(String path) throws Exception {
        InputStream inputStream = new ClassPathResource(path).getInputStream();
        return objectMapper.readValue(inputStream, new TypeReference<>() {});
    }

    private void wipeDatabase() {
        jdbcTemplate.execute("DELETE FROM workspaces");
        jdbcTemplate.execute("DELETE FROM users");
    }
}
