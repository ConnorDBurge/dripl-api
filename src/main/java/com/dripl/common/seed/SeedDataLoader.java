package com.dripl.common.seed;

import com.dripl.account.dto.CreateAccountDto;
import com.dripl.account.entity.Account;
import com.dripl.account.service.AccountService;
import com.dripl.budget.dto.CreateBudgetDto;
import com.dripl.budget.dto.SetExpectedAmountDto;
import com.dripl.budget.dto.UpdateBudgetCategoryConfigDto;
import com.dripl.budget.entity.Budget;
import com.dripl.budget.enums.RolloverType;
import com.dripl.budget.service.BudgetService;
import com.dripl.budget.service.BudgetConfigService;
import com.dripl.budget.util.BudgetPeriodCalculator;
import com.dripl.budget.util.PeriodRange;
import com.dripl.category.dto.CreateCategoryDto;
import com.dripl.category.entity.Category;
import com.dripl.category.service.CategoryService;
import com.dripl.merchant.dto.CreateMerchantDto;
import com.dripl.merchant.entity.Merchant;
import com.dripl.merchant.service.MerchantService;
import com.dripl.recurring.dto.CreateRecurringItemDto;
import com.dripl.recurring.enums.FrequencyGranularity;
import com.dripl.recurring.service.RecurringItemService;
import com.dripl.tag.dto.CreateTagDto;
import com.dripl.tag.entity.Tag;
import com.dripl.tag.service.TagService;
import com.dripl.transaction.dto.CreateTransactionDto;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.enums.TransactionStatus;
import com.dripl.transaction.group.dto.CreateTransactionGroupDto;
import com.dripl.transaction.group.service.TransactionGroupService;
import com.dripl.transaction.service.TransactionService;
import com.dripl.transaction.split.dto.CreateTransactionSplitDto;
import com.dripl.transaction.split.dto.SplitChildDto;
import com.dripl.transaction.split.service.TransactionSplitService;
import com.dripl.user.entity.User;
import com.dripl.user.service.UserService;
import com.dripl.workspace.dto.CreateWorkspaceDto;
import com.dripl.workspace.entity.Workspace;
import com.dripl.workspace.membership.enums.Role;
import com.dripl.workspace.membership.service.MembershipService;
import com.dripl.workspace.service.WorkspaceService;
import com.dripl.workspace.settings.dto.UpdateWorkspaceSettingsDto;
import com.dripl.workspace.settings.service.WorkspaceSettingsService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    private final TransactionService transactionService;
    private final TransactionGroupService transactionGroupService;
    private final TransactionSplitService transactionSplitService;
    private final RecurringItemService recurringItemService;
    private final BudgetConfigService budgetConfigService;
    private final BudgetService budgetService;
    private final WorkspaceSettingsService workspaceSettingsService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        log.info("⚠️ Building local seed data");
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

        log.info("✅ Seed data built successfully");
    }

    private void setSeedAuthentication() {
        setAuthenticationForEmail("seed-data@dripl.dev");
    }

    private void setAuthenticationForEmail(String email) {
        Claims claims = Jwts.claims().subject(email).build();
        var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Map<String, User> seedUsers() throws Exception {
        List<Map<String, Object>> seedUsers = readJson("seed-data/local/users.json");
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
        List<Map<String, Object>> seedWorkspaces = readJson("seed-data/local/workspaces.json");
        Set<String> defaultRenamed = new HashSet<>();

        for (Map<String, Object> seedWorkspace : seedWorkspaces) {
            String ownerEmail = (String) seedWorkspace.get("ownerEmail");
            String workspaceName = (String) seedWorkspace.get("name");
            User owner = usersByEmail.get(ownerEmail);

            setAuthenticationForEmail(ownerEmail);

            UUID workspaceId;
            if (!defaultRenamed.contains(ownerEmail)) {
                workspaceId = owner.getLastWorkspaceId();
                jdbcTemplate.update("UPDATE workspaces SET name = ? WHERE id = ?",
                        workspaceName, workspaceId);
                defaultRenamed.add(ownerEmail);
            } else {
                Workspace workspace = workspaceService.provisionWorkspace(
                        owner.getId(), CreateWorkspaceDto.builder().name(workspaceName).build());
                workspaceId = workspace.getId();
            }

            seedMembers(seedWorkspace, usersByEmail, workspaceId);

            Map<String, UUID> accountsByName = seedAccounts(seedWorkspace, workspaceId);
            Map<String, UUID> merchantsByName = seedMerchants(seedWorkspace, workspaceId);
            Map<String, UUID> tagsByName = seedTags(seedWorkspace, workspaceId);
            Map<String, UUID> categoriesByName = seedCategories(seedWorkspace, workspaceId);
            Map<String, UUID> recurringByDesc = seedRecurringItems(seedWorkspace, workspaceId,
                    accountsByName, categoriesByName, tagsByName);

            Map<String, List<UUID>> txnsByGroup = seedTransactions(seedWorkspace, workspaceId,
                    accountsByName, categoriesByName, tagsByName, recurringByDesc);

            seedTransactionGroups(seedWorkspace, workspaceId, categoriesByName, tagsByName, txnsByGroup);
            seedTransactionSplits(seedWorkspace, workspaceId, accountsByName, categoriesByName, tagsByName);
            seedBudgetData(seedWorkspace, workspaceId, categoriesByName, new ArrayList<>(accountsByName.values()));
        }
    }

    @SuppressWarnings("unchecked")
    private void seedMembers(Map<String, Object> ws, Map<String, User> usersByEmail, UUID workspaceId) {
        List<Map<String, Object>> members = (List<Map<String, Object>>) ws.get("members");
        if (members == null) return;

        for (Map<String, Object> member : members) {
            String email = (String) member.get("email");
            User user = usersByEmail.get(email);
            if (user == null) { log.warn("Member {} not found, skipping", email); continue; }

            List<String> roleNames = (List<String>) member.get("roles");
            Set<Role> roles = roleNames.stream().map(Role::valueOf).collect(Collectors.toSet());
            membershipService.createMembership(user.getId(), workspaceId, roles);
        }
    }

    private Map<String, UUID> seedAccounts(Map<String, Object> ws, UUID workspaceId) throws Exception {
        Map<String, UUID> map = new LinkedHashMap<>();
        List<Map<String, Object>> items = resolveSeedData(ws, "accounts");
        if (items == null) return map;

        for (Map<String, Object> seed : items) {
            Account entity = accountService.createAccount(workspaceId,
                    objectMapper.convertValue(seed, CreateAccountDto.class));
            map.put(entity.getName(), entity.getId());
        }
        return map;
    }

    private Map<String, UUID> seedMerchants(Map<String, Object> ws, UUID workspaceId) throws Exception {
        Map<String, UUID> map = new LinkedHashMap<>();
        List<Map<String, Object>> items = resolveSeedData(ws, "merchants");
        if (items == null) return map;

        for (Map<String, Object> seed : items) {
            Merchant entity = merchantService.createMerchant(workspaceId,
                    objectMapper.convertValue(seed, CreateMerchantDto.class));
            map.put(entity.getName(), entity.getId());
        }
        return map;
    }

    private Map<String, UUID> seedTags(Map<String, Object> ws, UUID workspaceId) throws Exception {
        Map<String, UUID> map = new LinkedHashMap<>();
        List<Map<String, Object>> items = resolveSeedData(ws, "tags");
        if (items == null) return map;

        for (Map<String, Object> seed : items) {
            Tag entity = tagService.createTag(workspaceId,
                    objectMapper.convertValue(seed, CreateTagDto.class));
            map.put(entity.getName(), entity.getId());
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, UUID> seedCategories(Map<String, Object> ws, UUID workspaceId) throws Exception {
        Map<String, UUID> map = new LinkedHashMap<>();
        List<Map<String, Object>> items = resolveSeedData(ws, "categories");
        if (items == null) return map;

        for (Map<String, Object> seed : items) {
            List<Map<String, Object>> children = (List<Map<String, Object>>) seed.get("children");
            seed.remove("children");

            Category parent = categoryService.createCategory(workspaceId,
                    objectMapper.convertValue(seed, CreateCategoryDto.class));
            map.put(parent.getName(), parent.getId());

            if (children != null) {
                for (Map<String, Object> child : children) {
                    child.put("parentId", parent.getId().toString());
                    Category childEntity = categoryService.createCategory(workspaceId,
                            objectMapper.convertValue(child, CreateCategoryDto.class));
                    map.put(childEntity.getName(), childEntity.getId());
                }
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, UUID> seedRecurringItems(Map<String, Object> ws, UUID workspaceId,
            Map<String, UUID> accounts, Map<String, UUID> categories, Map<String, UUID> tags) throws Exception {
        Map<String, UUID> map = new LinkedHashMap<>();
        List<Map<String, Object>> items = resolveSeedData(ws, "recurringItems");
        if (items == null) return map;

        for (Map<String, Object> seed : items) {
            String categoryName = (String) seed.get("categoryName");
            Set<UUID> tagIds = resolveTagIds((List<String>) seed.get("tagNames"), tags);

            List<Integer> anchorDaysOfMonth = ((List<Number>) seed.get("anchorDaysOfMonth")).stream()
                    .map(Number::intValue).toList();
            List<LocalDateTime> anchorDates = anchorDaysOfMonth.stream()
                    .map(day -> {
                        LocalDate now = LocalDate.now();
                        int clamped = Math.min(day, now.lengthOfMonth());
                        return now.withDayOfMonth(clamped).atStartOfDay();
                    })
                    .collect(Collectors.toList());

            int startDaysAgo = ((Number) seed.get("startDaysAgo")).intValue();

            CreateRecurringItemDto dto = CreateRecurringItemDto.builder()
                    .description((String) seed.get("description"))
                    .merchantName((String) seed.get("merchantName"))
                    .accountId(accounts.get((String) seed.get("accountName")))
                    .categoryId(categoryName != null ? categories.get(categoryName) : null)
                    .amount(new java.math.BigDecimal(seed.get("amount").toString()))
                    .notes((String) seed.get("notes"))
                    .frequencyGranularity(FrequencyGranularity.valueOf((String) seed.get("frequencyGranularity")))
                    .frequencyQuantity(((Number) seed.get("frequencyQuantity")).intValue())
                    .anchorDates(anchorDates)
                    .startDate(LocalDate.now().minusDays(startDaysAgo).atStartOfDay())
                    .tagIds(tagIds)
                    .build();

            var item = recurringItemService.createRecurringItem(workspaceId, dto);
            map.put(item.getDescription(), item.getId());
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<UUID>> seedTransactions(Map<String, Object> ws, UUID workspaceId,
            Map<String, UUID> accounts, Map<String, UUID> categories,
            Map<String, UUID> tags, Map<String, UUID> recurringByDesc) throws Exception {
        Map<String, List<UUID>> txnsByGroup = new LinkedHashMap<>();
        List<Map<String, Object>> items = resolveSeedData(ws, "transactions");
        if (items == null) return txnsByGroup;

        for (Map<String, Object> seed : items) {
            String categoryName = (String) seed.get("categoryName");
            String recurringDesc = (String) seed.get("recurringDescription");
            Set<UUID> tagIds = resolveTagIds((List<String>) seed.get("tagNames"), tags);

            int daysAgo = ((Number) seed.get("daysAgo")).intValue();

            CreateTransactionDto dto = CreateTransactionDto.builder()
                    .accountId(accounts.get((String) seed.get("accountName")))
                    .merchantName((String) seed.get("merchantName"))
                    .categoryId(categoryName != null ? categories.get(categoryName) : null)
                    .date(LocalDate.now().minusDays(daysAgo).atStartOfDay())
                    .amount(new java.math.BigDecimal(seed.get("amount").toString()))
                    .notes((String) seed.get("notes"))
                    .recurringItemId(recurringDesc != null ? recurringByDesc.get(recurringDesc) : null)
                    .tagIds(tagIds)
                    .build();

            Transaction txn = transactionService.createTransaction(workspaceId, dto);

            if ("POSTED".equals(seed.get("status"))) {
                txn.setStatus(TransactionStatus.POSTED);
                txn.setPostedAt(java.time.LocalDateTime.now());
            }

            String groupName = (String) seed.get("groupName");
            if (groupName != null) {
                txnsByGroup.computeIfAbsent(groupName, k -> new ArrayList<>()).add(txn.getId());
            }
        }
        return txnsByGroup;
    }

    @SuppressWarnings("unchecked")
    private void seedTransactionGroups(Map<String, Object> ws, UUID workspaceId,
            Map<String, UUID> categories, Map<String, UUID> tags,
            Map<String, List<UUID>> txnsByGroup) throws Exception {
        List<Map<String, Object>> items = resolveSeedData(ws, "transactionGroups");
        if (items == null) return;

        for (Map<String, Object> seed : items) {
            String groupName = (String) seed.get("name");
            String categoryName = (String) seed.get("categoryName");
            Set<UUID> tagIds = resolveTagIds((List<String>) seed.get("tagNames"), tags);

            List<UUID> txnIds = txnsByGroup.getOrDefault(groupName, List.of());
            if (txnIds.size() < 2) continue;

            CreateTransactionGroupDto dto = CreateTransactionGroupDto.builder()
                    .name(groupName)
                    .categoryId(categoryName != null ? categories.get(categoryName) : null)
                    .notes((String) seed.get("notes"))
                    .tagIds(tagIds)
                    .transactionIds(new LinkedHashSet<>(txnIds))
                    .build();

            transactionGroupService.createTransactionGroup(workspaceId, dto);
        }
    }

    @SuppressWarnings("unchecked")
    private void seedTransactionSplits(Map<String, Object> ws, UUID workspaceId,
            Map<String, UUID> accounts, Map<String, UUID> categories, Map<String, UUID> tags) throws Exception {
        List<Map<String, Object>> items = resolveSeedData(ws, "transactionSplits");
        if (items == null) return;

        for (Map<String, Object> seed : items) {
            List<Map<String, Object>> seedChildren = (List<Map<String, Object>>) seed.get("children");
            if (seedChildren == null || seedChildren.size() < 2) {
                log.warn("Split seed requires at least 2 children, skipping: {}", seed.get("name"));
                continue;
            }

            String categoryName = (String) seed.get("categoryName");
            int daysAgo = ((Number) seed.get("daysAgo")).intValue();
            CreateTransactionDto sourceDto = CreateTransactionDto.builder()
                    .accountId(accounts.get((String) seed.get("accountName")))
                    .merchantName((String) seed.get("merchantName"))
                    .categoryId(categoryName != null ? categories.get(categoryName) : null)
                    .date(LocalDate.now().minusDays(daysAgo).atStartOfDay())
                    .amount(new java.math.BigDecimal(seed.get("amount").toString()))
                    .notes((String) seed.get("notes"))
                    .build();

            Transaction sourceTxn = transactionService.createTransaction(workspaceId, sourceDto);

            List<SplitChildDto> childDtos = new ArrayList<>();
            for (Map<String, Object> seedChild : seedChildren) {
                String childCatName = (String) seedChild.get("categoryName");
                Set<UUID> childTagIds = resolveTagIds((List<String>) seedChild.get("tagNames"), tags);

                childDtos.add(SplitChildDto.builder()
                        .amount(new java.math.BigDecimal(seedChild.get("amount").toString()))
                        .merchantName((String) seedChild.get("merchantName"))
                        .categoryId(childCatName != null ? categories.get(childCatName) : null)
                        .tagIds(childTagIds)
                        .notes((String) seedChild.get("notes"))
                        .build());
            }

            transactionSplitService.createTransactionSplit(workspaceId,
                    CreateTransactionSplitDto.builder()
                            .transactionId(sourceTxn.getId())
                            .children(childDtos)
                            .build());
        }
    }

    @SuppressWarnings("unchecked")
    private void seedBudgetData(Map<String, Object> ws, UUID workspaceId,
            Map<String, UUID> categoriesByName, List<UUID> accountIds) throws Exception {
        // Workspace settings (timezone only now — budget period moves to Budget entity)
        Map<String, Object> settingsSeed = (Map<String, Object>) ws.get("workspaceSettings");
        if (settingsSeed != null) {
            UpdateWorkspaceSettingsDto.UpdateWorkspaceSettingsDtoBuilder builder = UpdateWorkspaceSettingsDto.builder();
            if (settingsSeed.get("timezone") != null) {
                builder.timezone((String) settingsSeed.get("timezone"));
            }
            workspaceSettingsService.updateSettings(workspaceId, builder.build());
        }

        // Create a Budget entity from workspace settings period config
        UUID budgetId = null;
        if (settingsSeed != null) {
            CreateBudgetDto.CreateBudgetDtoBuilder budgetBuilder = CreateBudgetDto.builder()
                    .name("Default Budget")
                    .accountIds(accountIds);

            if (settingsSeed.get("budgetIntervalDays") != null) {
                budgetBuilder.intervalDays(((Number) settingsSeed.get("budgetIntervalDays")).intValue());
            }
            if (settingsSeed.get("budgetAnchorDaysAgo") != null) {
                int daysAgo = ((Number) settingsSeed.get("budgetAnchorDaysAgo")).intValue();
                budgetBuilder.anchorDate(LocalDate.now().minusDays(daysAgo));
            }
            if (settingsSeed.get("budgetAnchorDay1") != null) {
                budgetBuilder.anchorDay1(((Number) settingsSeed.get("budgetAnchorDay1")).intValue());
            }
            if (settingsSeed.get("budgetAnchorDay2") != null) {
                budgetBuilder.anchorDay2(((Number) settingsSeed.get("budgetAnchorDay2")).intValue());
            }

            Budget budget = budgetService.createBudget(workspaceId, budgetBuilder.build());
            budgetId = budget.getId();
        }

        if (budgetId == null) return;

        // Budget category configs (rollover types)
        List<Map<String, Object>> configs = resolveSeedData(ws, "budgetCategoryConfigs");
        if (configs != null) {
            for (Map<String, Object> config : configs) {
                String categoryName = (String) config.get("categoryName");
                UUID categoryId = categoriesByName.get(categoryName);
                if (categoryId == null) {
                    log.warn("Budget config: category '{}' not found, skipping", categoryName);
                    continue;
                }
                budgetConfigService.updateConfig(workspaceId, budgetId, categoryId,
                        UpdateBudgetCategoryConfigDto.builder()
                                .rolloverType(RolloverType.valueOf((String) config.get("rolloverType")))
                                .build());
            }
        }

        // Budget period entries (expected amounts)
        List<Map<String, Object>> periodEntries = resolveSeedData(ws, "budgetPeriodEntries");
        if (periodEntries != null) {
            Budget budgetEntity = budgetService.findBudget(workspaceId, budgetId);
            PeriodRange currentPeriod = BudgetPeriodCalculator.computePeriod(budgetEntity, LocalDate.now());

            for (Map<String, Object> periodSeed : periodEntries) {
                int offset = ((Number) periodSeed.get("periodOffset")).intValue();
                PeriodRange targetPeriod = currentPeriod;
                for (int i = 0; i < Math.abs(offset); i++) {
                    targetPeriod = BudgetPeriodCalculator.computePreviousPeriod(budgetEntity, targetPeriod);
                }

                List<Map<String, Object>> entries = (List<Map<String, Object>>) periodSeed.get("entries");
                for (Map<String, Object> entry : entries) {
                    String categoryName = (String) entry.get("categoryName");
                    UUID categoryId = categoriesByName.get(categoryName);
                    if (categoryId == null) continue;
                    budgetConfigService.setExpectedAmount(workspaceId, budgetId, categoryId, targetPeriod.start(),
                            SetExpectedAmountDto.builder()
                                    .expectedAmount(new java.math.BigDecimal(entry.get("expectedAmount").toString()))
                                    .build());
                }
            }
        }
    }

    private Set<UUID> resolveTagIds(List<String> tagNames, Map<String, UUID> tagsByName) {
        if (tagNames == null) return Set.of();
        return tagNames.stream().map(tagsByName::get).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private List<Map<String, Object>> readJson(String path) throws Exception {
        InputStream inputStream = new ClassPathResource(path).getInputStream();
        return objectMapper.readValue(inputStream, new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveSeedData(Map<String, Object> workspace, String key) throws Exception {
        Object value = workspace.get(key);
        if (value == null) return null;
        if (value instanceof String ref && ref.startsWith("$ref:")) {
            return readJson("seed-data/local/" + ref.substring(5));
        }
        return (List<Map<String, Object>>) value;
    }

    private void wipeDatabase() {
        log.info("Resetting local database");
        jdbcTemplate.execute("DELETE FROM workspaces");
        jdbcTemplate.execute("DELETE FROM users");
    }
}
