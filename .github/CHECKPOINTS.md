# Dripl — Checkpoints

## Checkpoint 1: Hello World with Auth ✅
Spring Boot boots, connects to Postgres, and JWT auth works end-to-end in Postman.

- [x] Initialize Spring Boot project (Java 21, Maven)
- [x] Create `docker-compose.yml` (dripl-api, dripl-db, dripl-network)
- [x] Create Dockerfile for dripl-api
- [x] Add `application.yml` with Postgres, JPA, and JWT config
- [x] Set up base package structure (`com.dripl.*`)
- [x] Implement JWT utility (mint/validate with `workspace_id`, `user_id`, `roles` claims)
- [x] Implement `SecurityConfig` with JWT filter
- [x] Create `/dev/token` endpoint (profile-gated, disabled in prod)
- [x] Add Flyway for database migrations (moved from Checkpoint 5)
- [x] Verify: `docker compose up` → mint token → call secured endpoint in Postman

## Checkpoint 2: Users & Workspaces ✅
User and workspace management works. Can create users, provision workspaces, and switch between them.

- [x] Port `@WorkspaceId`, `@UserId`, `@Subject` annotations and argument resolvers
- [x] Port `@PreAuthorize` role-based authorization (READ/WRITE)
- [x] Audit base entity (`createdAt`, `updatedAt`)
- [x] User entity + repository + service + controller (`/users`)
- [x] Workspace entity + repository + service + controller (`/workspaces`)
- [x] WorkspaceMembership entity + repository + service
- [x] Workspace provisioning logic
- [x] Workspace switching logic (re-mint JWT with new workspace claims)
- [x] Duplicate workspace name prevention (per-user, case-insensitive)
- [x] Verify: Bootstrap user → create workspace → switch workspace — all via Postman
- [x] Current workspace endpoint (`GET /workspaces/current`)
- [x] Update current workspace endpoint (`PATCH /workspaces/current`)
- [x] List workspace members (`GET /workspaces/current/members`)
- [x] Add workspace member (`POST /workspaces/current/members`)
- [x] Update workspace member roles (`PATCH /workspaces/current/members/{userId}`)
- [x] Remove workspace member (`DELETE /workspaces/current/members/{userId}`)
- [x] WorkspaceMembershipDto, CreateMembershipDto, UpdateMembershipDto
- [x] Package restructuring into controller/dto/entity/repository/service/enums sub-packages
- [x] TokenService extraction, response flattening (UserResponse/WorkspaceResponse)
- [x] GlobalExceptionHandler with ErrorResponse record, validation errors
- [x] Unit tests for all services and endpoints

## Checkpoint 2.5: Testing & Infrastructure ✅
Comprehensive test suite and observability features.

- [x] Testcontainers for isolated test DB (PostgreSQL 17 Alpine, singleton pattern)
- [x] Unit tests: 106 tests across all services, controllers, utilities
- [x] Integration tests: 38 tests (Testcontainers, full HTTP stack)
  - [x] BootstrapAndAuthIT (6): Auth flow, validation, 403 for unauthenticated
  - [x] UserCrudIT (5): GET/PATCH/DELETE self, duplicate email, empty email
  - [x] WorkspaceCrudIT (7): List, provision, duplicate, switch, GET/PATCH current
  - [x] MemberManagementIT (6): List/add/get/update/remove members
  - [x] RoleBasedAccessIT (7): READ-only restrictions, OWNER full access
  - [x] WorkspaceCleanupIT (3): Orphan deletion, multi-workspace cleanup, shared workspace preservation
  - [x] CorrelationIdIT (4): Header echo, auto-generation, error inclusion, async propagation
- [x] Maven Failsafe plugin for IT test execution (`*IT.java`)
- [x] Correlation ID filter (CorrelationIdFilter → MDC → log pattern → response header)
- [x] Async orphaned workspace cleanup (MembershipDeletedEvent → WorkspaceCleanupListener)
- [x] `@EnableAsync` on DriplApplication
- [x] Improved log messages with human-readable names + UUIDs
- [x] Awaitility for async assertion polling in IT tests

## Checkpoint 3: Accounts ✅
Full CRUD for financial accounts, scoped to the active workspace.

- [x] Flyway migration for `accounts` table
- [x] Account entity + repository + service + controller (`/api/v1/accounts`)
- [x] Account type/subtype validation
- [x] Workspace-scoped queries (all queries filter by `workspaceId`)
- [x] Unit tests
- [x] Integration tests
- [x] Verify: Create, list, get, update, delete accounts — confirm workspace isolation

## Checkpoint 4: Merchants ✅
Full CRUD for merchants/payees, scoped to the active workspace.

- [x] Flyway migration for `merchants` table
- [x] Merchant entity + repository + service + controller (`/api/v1/merchants`)
- [x] MerchantStatus enum (ACTIVE, ARCHIVED)
- [x] Workspace-scoped queries (all queries filter by `workspaceId`)
- [x] Duplicate name prevention (case-insensitive, per workspace)
- [x] MapStruct mapper (MerchantMapper with toDto, toDtos, updateEntity)
- [x] DTOs (MerchantDto, CreateMerchantDto, UpdateMerchantDto)
- [x] Unit tests
- [x] Integration tests
- [x] Verify: Create, list, get, update, delete merchants — confirm workspace isolation

## Checkpoint 4.5: Dev Seed Data ✅
Dev-only seed data system that wipes and recreates data on every startup.

- [x] `SeedDataLoader` (`@Profile("dev")` CommandLineRunner)
- [x] JSON seed files: `users.json` (flat user list), `workspaces.json` (workspaces with nested members, accounts, merchants)
- [x] Wipe database on startup using `ON DELETE CASCADE` (delete workspaces → delete users)
- [x] Seed via service layer (bootstrapUser, provisionWorkspace, createMembership, createAccount, createMerchant)
- [x] Fake SecurityContext (`seed-data@dripl.dev`) for JPA audit fields (`createdBy`/`updatedBy`)
- [x] Rename default bootstrap workspace to match seed data names
- [x] 3 users, 3 workspaces, 10 accounts, 19 merchants, 1 cross-workspace member
- [x] Verified: Docker startup, API access via bootstrap, workspace isolation

## Checkpoint 5: Tags ✅
Full CRUD for transaction tags, scoped to the active workspace.

- [x] Flyway migration V7 for `tags` table
- [x] Tag entity + repository + service + controller (`/api/v1/tags`)
- [x] TagStatus consolidated into shared `com.dripl.common.enums.Status` (ACTIVE, ARCHIVED, CLOSED)
- [x] Shared Status enum also replaces MerchantStatus and AccountStatus
- [x] Workspace-scoped queries (all queries filter by `workspaceId`)
- [x] Duplicate name prevention (case-insensitive, per workspace)
- [x] Optional description field
- [x] MapStruct mapper (TagMapper with toDto, toDtos, updateEntity)
- [x] DTOs (TagDto, CreateTagDto, UpdateTagDto)
- [x] Unit tests (15 service + 6 controller)
- [x] Integration tests (13 TagCrudIT)
- [x] Tags added to seed data (14 tags across 3 workspaces)
- [x] Seed data logging muted during seeding (Logback level set to WARN, summary printed after)
- [x] Verify: Create, list, get, update, delete tags — confirm workspace isolation

## Checkpoint 6: Categories ✅
Full CRUD for transaction categories with parent-child tree structure, scoped to the active workspace.

- [x] Flyway migration V8 for `categories` table (self-referencing `parent_id ON DELETE SET NULL`)
- [x] Category entity + repository + service + controller (`/api/v1/categories`)
- [x] Category tree endpoint (`/api/v1/categories/tree`) with `CategoryTreeDto.buildTree()`
- [x] Parent-child hierarchy (max depth 2, circular reference prevention)
- [x] Category with children cannot be nested (prevents depth > 2 on move)
- [x] Single GET includes children as full `CategoryDto` objects (`@JsonInclude(NON_NULL)`)
- [x] PATCH `"children": []` clears all children (sets parentId to null via JPQL bulk update)
- [x] Combined clear-children + set-parent in one PATCH
- [x] `CategoryTreeDto` extends `BaseDto` (includes audit fields in `/tree` response)
- [x] Fields: name, description, status, income, excludeFromBudget, excludeFromTotals
- [x] Workspace-scoped queries (all queries filter by `workspaceId`)
- [x] MapStruct mapper (CategoryMapper with toDto, toDtos, updateEntity)
- [x] UpdateCategoryDto `parentIdSpecified` and `childrenSpecified` flags (`@JsonSetter`) for PATCH semantics
- [x] Unit tests (26 service + 8 controller + 5 CategoryTreeDto)
- [x] Integration tests (22 CategoryCrudIT)
- [x] Categories added to seed data (nested parent-child groups with descriptions across 3 workspaces)
- [x] Seed data summary updated with category count
- [x] DELETE role added to `Role` enum (READ, WRITE, DELETE, OWNER)
- [x] All delete endpoints use `@PreAuthorize("hasAuthority('DELETE')")` (independent of WRITE)
- [x] Owner default role set updated to include DELETE
- [x] RoleBasedAccessIT: READ-only cannot delete, WRITE-only cannot delete (2 new tests)
- [x] All 364 tests pass (266 unit + 98 integration)
- [x] Verify: Create, list, get tree, update, delete categories — confirm workspace isolation

## Checkpoint 7: Transactions ✅
Full CRUD for financial transactions with cross-entity validation, merchant auto-resolution, and status lifecycle.

- [x] Flyway V9: `transactions` table + `transaction_tags` join table (6 indexes)
- [x] TransactionStatus enum (PENDING, POSTED, ARCHIVED), TransactionSource enum (MANUAL)
- [x] Transaction entity with `@ElementCollection` for tagIds (`Set<UUID>`)
- [x] TransactionRepository with workspace-scoped queries
- [x] TransactionDto, CreateTransactionDto (takes `merchantName`), UpdateTransactionDto (specified flags for categoryId, notes, tagIds)
- [x] TransactionMapper (MapStruct) — `updateEntity` handles date, amount, currencyCode; special cases handled manually
- [x] TransactionService:
  - Delegates to AccountService/CategoryService/TagService for cross-entity validation (returns 404, not 400)
  - Uses verified entity IDs from service responses, not raw DTO values
  - Merchant auto-resolution: case-insensitive lookup, auto-creates if not found, returns full entity
  - Status lifecycle: `pendingAt` set on PENDING, `postedAt` set on POSTED, no timestamp change for ARCHIVED
- [x] TransactionController — full CRUD (READ/WRITE/DELETE)
- [x] `FlexibleLocalDateTimeDeserializer` — accepts both `"2025-07-01"` and `"2025-07-01T00:00:00"`
- [x] Unit tests (34 service + 6 controller = 40)
- [x] Integration tests (16 TransactionCrudIT)
- [x] Seed data: 27 transactions across 3 workspaces (resolved by accountName/merchantName/categoryName/tagNames)
- [x] All 424 tests pass (310 unit + 114 integration)
- [x] Verify: Create with existing/new merchant, case-insensitive lookup, status transitions, tag management, workspace isolation

## Checkpoint 8: Recurring Items ✅
Recurring transaction templates with flexible scheduling, transaction inheritance, spec-based filtering, and shared merchant resolution.

- [x] Flyway V10: `recurring_items` table + `recurring_item_anchor_dates` + `recurring_item_tags` join tables
- [x] Flyway V11: Add `recurring_item_id` nullable FK column to `transactions` table
- [x] RecurringItemStatus enum (ACTIVE, PAUSED, CANCELLED)
- [x] FrequencyGranularity enum (DAY, WEEK, MONTH, YEAR)
- [x] RecurringItem entity with `@ElementCollection` for anchorDates and tagIds, `frequencyQuantity` defaults to 1
- [x] RecurringItemRepository with workspace-scoped queries
- [x] RecurringItemDto, CreateRecurringItemDto (`description` optional, `frequencyQuantity` optional), UpdateRecurringItemDto
- [x] RecurringItemMapper (MapStruct)
- [x] RecurringItemService (validates account/category/tags via services, merchant auto-resolution)
- [x] RecurringItemController — full CRUD (READ/WRITE/DELETE)
- [x] Update Transaction entity/DTOs/service with nullable `recurringItemId`
- [x] Transaction recurring item inheritance: DTO values win, recurring item provides defaults for accountId, merchantId, categoryId, tagIds, amount, currencyCode (create and update)
- [x] `resolveRequired()` / `resolveOptional()` generic helpers for clean inheritance chain
- [x] `BadRequestException` for missing required fields when neither DTO nor recurring item provides them
- [x] Relaxed `CreateTransactionDto` validation (`accountId`, `merchantName`, `amount` optional when `recurringItemId` provided)
- [x] `UpdateTransactionDto` — `recurringItemId` with specified-flag pattern (set inherits, clear nullifies, omit preserves)
- [x] Shared `MerchantService.resolveMerchant()` — extracted from duplicated private methods in TransactionService and RecurringItemService
- [x] Spec-based transaction filtering via `TransactionSpecifications` + `optionally()` pattern
- [x] Transaction query params: accountId, merchantId, categoryId, recurringItemId, status, source, currencyCode, tagIds (any-match)
- [x] MDC log cleanup — removed redundant "in workspace {}" and "user {}" from 16 log statements across 8 services
- [x] Unit tests: RecurringItemService (30) + RecurringItemController (6) + TransactionService inheritance (9) = 45 new
- [x] Integration tests: RecurringItemCrudIT (16) + TransactionCrudIT inheritance (5) = 21 new
- [x] Seed data: 6 recurring items across 2 workspaces with matching transactions
- [x] All 490 tests pass (355 unit + 135 integration)

## Checkpoint 9: Transaction Groups ✅
Named containers for bundling related transactions. First-class entity — no synthetic transactions.

- [x] Flyway V12: `transaction_groups` table + `transaction_group_tags` join table
- [x] Flyway V13: Add `group_id` nullable FK column to `transactions` table
- [x] TransactionGroup entity with `@ElementCollection` for tagIds
- [x] TransactionGroupRepository with workspace-scoped queries
- [x] TransactionGroupDto (includes `totalAmount`, `transactionIds`), CreateTransactionGroupDto, UpdateTransactionGroupDto
- [x] TransactionGroupMapper (MapStruct) — `toDto` with computed fields via `toBuilder()`
- [x] TransactionGroupService: validates transactions in workspace, enforces min 2, manages membership
- [x] TransactionGroupController — 5 endpoints (list, get, create, update metadata+membership, delete)
- [x] Update Transaction entity with nullable `groupId`
- [x] Update TransactionDto to include `groupId`
- [x] Seed data: 2 groups in Burge Family workspace ("Weekend Dining Out", "July Grocery Runs")
- [x] Fix: `@Modifying(flushAutomatically = true, clearAutomatically = true)` on bulk JPQL to prevent FK constraint violations and stale entities
- [x] Fix: Set iteration order in unit tests — use `any(UUID.class)` for unpredictable Set traversal

## Checkpoint 10: Field Locking & API Consolidation ✅
Enforced field immutability for transactions linked to recurring items or groups. Consolidated group API from 7 to 5 endpoints.

- [x] Locked-field validation in `TransactionService.updateTransaction()` — rejects changes to managed fields when RI-linked or grouped
- [x] RI locked fields: `accountId`, `merchantName`, `categoryId`, `tagIds`, `notes`, `currencyCode` — always from RI, DTO values ignored
- [x] Group locked fields: `categoryId`, `tagIds`, `notes` — only changeable via group API
- [x] Mutual exclusivity: transaction cannot be in a group AND linked to a recurring item simultaneously
- [x] Unlink support via PATCH: `{"recurringItemId": null}` / `{"groupId": null}` — bypasses locking via `isUnlinkingRecurringItem()` / `isUnlinkingGroup()` helpers
- [x] Group unlink min-2 enforcement: `countByGroupId` runs BEFORE `setGroupId(null)`, threshold `< 3`
- [x] Setting non-null `groupId` via transaction endpoint throws 400 (must use transaction-groups API)
- [x] Consolidated group API: removed `/{groupId}/transactions` endpoint, `PATCH /{id}` handles both metadata and membership via `transactionIds`
- [x] Full set reconciliation in `updateTransactionGroup`: diffs current vs desired membership, adds/removes accordingly
- [x] Explicit `save()` calls in `applyGroupOverrides` and `updateTransactionGroup` for dirty-tracking reliability
- [x] `applyGroupOverrides` propagates group `categoryId`/`tagIds`/`notes` to added transactions
- [x] RI inheritance on create: locked fields always from RI, validation order fixed (`accountId` before merchant)
- [x] RI inheritance on update: locked fields unconditionally overwritten from RI on link
- [x] `currencyCode` added to RI locked field set
- [x] Renamed stale `dtoOverridesWin` tests → `lockedFieldsFromRI` (unit + IT)
- [x] IT coverage: RI override verification, currencyCode lock, group membership (remove clears, RI-linked rejection, delete clears all)
- [x] Lazy `@ElementCollection` captured eagerly before JPQL with `clearAutomatically = true`

**Test totals:** 395 unit + 163 integration = 558 tests

## Checkpoint 11: Transaction Splits ✅
Split an existing transaction into multiple child transactions with independent categories/merchants while enforcing amounts sum to the original total.

- [x] Flyway V14: `transaction_splits` table (id, workspace_id FK, total_amount, currency_code, account_id, date, audit fields)
- [x] Flyway V15: Add `split_id` nullable FK on `transactions` table
- [x] TransactionSplit entity, repository, DTOs (5), mapper
- [x] TransactionSplitService: create (delete source, create children), update (full child reconciliation), dissolve
- [x] TransactionSplitController: 5 endpoints (list, get, create, update, delete)
- [x] Add `splitId` to Transaction entity, TransactionDto, UpdateTransactionDto (specified-flag), TransactionSpecifications
- [x] Field locking for split children: `accountId`, `currencyCode`, `amount`, `date` locked via transaction API
- [x] Revised mutual exclusivity: group+split blocked, group+RI blocked, **split+RI allowed** (validate accountId/currencyCode match)
- [x] Unit tests: TransactionSplitService (15), TransactionSplitController (5), TransactionService split-locking (+16)
- [x] Integration tests: TransactionSplitCrudIT (21) — full lifecycle, amount validation, mutual exclusivity, field locking, RI linking, dissolve, unlink, filter

**Test totals: 431 unit + 184 integration = 615 tests, all passing**

## Checkpoint 11.5: Polarity Validation & Split Hardening ✅
Cross-cutting category polarity validation, spec-based category filtering, split child sign enforcement, and splitId fully locked.

- [x] `CategoryService.validateCategoryPolarity()` — positive amounts require `income=true`, negative/zero require `income=false`
- [x] Polarity validation wired into TransactionService (create/update), TransactionSplitService (3 spots), TransactionGroupService (create/update), RecurringItemService (create/update)
- [x] Spec-based category filtering via `CategorySpecifications` + `JpaSpecificationExecutor` — `GET /categories?income=true|false` on both list and tree endpoints
- [x] Split child amount sign validation — all children must match the sign of `totalAmount`
- [x] `splitId` fully locked on transaction API — any `splitId` modification throws 400 (use transaction-splits API to dissolve)
- [x] Removed split unlink support from transaction API (was min-2 enforcement; now hard reject)
- [x] Unit tests: 17 polarity tests (CategoryService 7, TransactionService 3, TransactionGroupService 2, TransactionSplitService 2, RecurringItemService 3), 2 sign validation tests, updated split lock tests
- [x] Integration tests: Updated split unlink IT (2 tests → 1 rejection test)

**Test totals: 448 unit + 183 integration = 631 tests, all passing**

## Checkpoint 12: Transaction Filtering, Pagination & Sorting ✅
Offset-based pagination, single-column sorting (including JOIN-based), date/amount range filters, and cross-field text search.

- [x] `PagedResponse<T>` wrapper in `common/dto` — `content` list + `PageInfo` record (number, size, totalElements, totalPages), factory method `from(Page<T>)`
- [x] New filter specs: `dateOnOrAfter`, `dateOnOrBefore`, `amountGreaterThanOrEqual`, `amountLessThanOrEqual`, `searchText` (ILIKE OR across notes, merchant name correlated subquery, category name correlated subquery)
- [x] Sorting: `sortBy` + `sortDirection` params, default `date` DESC. Direct columns: `date`, `amount`, `createdAt` via Spring `Sort`. JOIN columns: `category`, `merchant`, `account` via correlated subqueries in Specification `ORDER BY`. Tiebreaker: `id` ASC
- [x] Controller/service: `page` (default 0), `size` (default 25, max 250, clamped), return `PagedResponse<TransactionDto>` instead of `List<TransactionDto>`
- [x] Unit tests: updated TransactionController (8 tests, +2 for pageMetadata and clampsMaxSize), updated TransactionService list tests for Page/Pageable
- [x] Integration tests: pagination (default metadata, custom size, page 2, out-of-range, size clamping), sorting (date ASC, amount DESC, category name, merchant name), date range (start, end, both), amount range (min, max), search (notes, merchant, category, no match), combined filters+sort+pagination
- [x] Documentation: ARCHITECTURE.md + CHECKPOINTS.md updated
- **Test totals**: 450 unit + 202 IT = 652 tests, all passing

## Checkpoint 13: Transaction Event History ✅
Async fire-and-forget transaction change history using Spring's `ApplicationEventPublisher`.

- [x] Flyway V17: `transaction_events` table (id UUID PK, transaction_id FK CASCADE, workspace_id, event_type, changes JSONB, performed_by, performed_at)
- [x] `DomainEvent` + `FieldChange` + `DomainEvents` records in `common/event` — generic "domain.action" event with `FieldChange` map
- [x] `TransactionEvent` entity + `TransactionEventRepository`
- [x] `TransactionEventDto` response DTO
- [x] `TransactionEventService` — `@Async` + `@TransactionalEventListener` + `REQUIRES_NEW` listener that persists events + read API
- [x] Publish events from `TransactionService` (create, update — no delete due to FK constraint)
- [x] Publish events from `TransactionGroupService` (grouped, ungrouped)
- [x] Publish events from `TransactionSplitService` (split, unsplit)
- [x] `GET /api/v1/transactions/{id}/events` endpoint on `TransactionController`
- [x] BigDecimal normalization fix (`stripTrailingZeros`) for amount diff comparison
- [x] Unit tests: 6 TransactionEventService tests
- [x] Integration tests: 10 TransactionEventIT tests (create/update/grouped/ungrouped/split/unsplit events, change diffs, ordering, GET endpoint)

**Test totals: 459 unit + 216 IT = 675 tests, all passing**

---

---

## Checkpoint 14: Budgeting ✅

Multiple budgets per workspace. Each budget is a standalone entity with its own period configuration (anchor days/dates), account scope (via `budget_accounts` join table), and category settings. Period config moved from `WorkspaceSettings` to the `Budget` entity. `WorkspaceSettings` now only holds `defaultCurrencyCode` and `timezone`.

**Period modes:** Single anchor (monthly from anchorDay1), dual anchor (semi-monthly from anchorDay1/anchorDay2), fixed interval (every N days from anchorDate). Period math is pure server-side computation via `BudgetPeriodCalculator`.

**Navigation:** Offset-based (`?periodOffset=0/-1/+1`) or date-based (`?date=2026-02-14` finds the period containing that date).

**Rollover:** NONE / SAME_CATEGORY / AVAILABLE_POOL. Computed dynamically by chaining back through prior periods (capped at 24).

**Budget view:** Category tree split into inflow/outflow sections. Per-category: expected, recurringExpected, activity, rolledOver, available. Parent rows show rollup totals. `excludeFromBudget` categories omitted.

**Service pattern:** Services return entities, controllers handle DTO mapping. `Budget.toDto()` and `BudgetCategoryConfig.toDto()` live on the entities. `BudgetViewService` returns DTOs directly (computed projection exception).

**Expected amounts:** Single `PUT /categories/{categoryId}/expected?periodStart=...` endpoint — `null` expectedAmount clears the entry (no separate DELETE).

**New tables (V14–V16):** `budgets` + `budget_accounts` (V14), `budget_category_configs` (V15), `budget_period_entries` (V16).

**Migrations collapsed:** 23 migration files → 16 clean per-table files (V1–V16), one per entity in dependency order.

**Completed work:**
- [x] Multi-budget entity, repository, service, controller (full CRUD)
- [x] Budget period config on Budget entity (anchorDay1, anchorDay2, anchorDate, intervalDays)
- [x] BudgetAccount join table for account scoping
- [x] BudgetPeriodCalculator (single anchor, dual anchor, fixed interval)
- [x] BudgetViewService (period view, rollover computation, recurring expected)
- [x] BudgetCrudController (5 CRUD endpoints) + BudgetController (view, config, expected)
- [x] Offset-based and date-based period navigation
- [x] Category rollover config (PATCH /categories/{categoryId})
- [x] Expected amount set/clear (PUT /categories/{categoryId}/expected?periodStart=...)
- [x] Recurring items expected computation in budget view
- [x] Entity toDto() pattern (Budget, BudgetCategoryConfig)
- [x] Migration collapse (23 → 16 files)
- [x] Seed data (Burge Family: biweekly budget with rollover configs + expected amounts)
- [x] Recurring item period override scaffold (PUT /recurring-items/{id}/expected?periodStart=...)
- [x] Unit tests + integration tests
- [x] Documentation update (ARCHITECTURE.md + CHECKPOINTS.md)

**Test totals: 571 unit + 246 integration = 817 tests, all passing**

---

## Checkpoint 15: Budget View Refinements & Category Ordering ✅

Budget view adopts Lunch Money's summary naming. Category display ordering enables drag-and-drop UI.

**Budget view summary fields** — Replaced `toBeBudgeted` with Lunch Money's model:
- `budgetable` = inflow expected + available pool (money available to assign)
- `totalBudgeted` = outflow expected (money assigned to expense categories)
- `leftToBudget` = budgetable − totalBudgeted (unassigned money)
- `netTotalAvailable` = sum of linked account balances (actual money in bank)
- `availablePool` now flows into `budgetable` (AVAILABLE_POOL rollover is assignable money)

**Expected amount validation** — `setExpectedAmount` rejects: (1) parent categories (expected rolls up from children), (2) period start dates that don't align with budget config (returns 400).

**Category display ordering** — New `displayOrder` integer column (V17 migration):
- Auto-assigned sequentially on create (max + 1 among siblings)
- Roots ordered among each other; children ordered within their parent
- Re-parenting or ungrouping appends to end of new sibling group
- `PUT /api/v1/categories/order` accepts `[{id, displayOrder}]` for bulk reorder
- Tree, list, and budget views all sort by `displayOrder`
- `CategoryMapper` ignores `displayOrder` on PATCH (prevents accidental overwrite)

**Completed work:**
- [x] Rename `toBeBudgeted` → `budgetable`, `totalBudgeted`, `leftToBudget` in DTOs and services
- [x] Fix `leftToBudget` formula to include `availablePool`
- [x] Add `netTotalAvailable` (sum of linked account balances)
- [x] Validate expected amount: reject parent categories and misaligned period starts
- [x] V17 migration: `ALTER TABLE categories ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0`
- [x] Auto-assign `displayOrder` on create (root and child scoped separately)
- [x] Handle re-parent/ungroup: append to end of new group
- [x] `PUT /api/v1/categories/{id}/order` single-item move endpoint (shifts siblings automatically)
- [x] Sort by `displayOrder` in tree, list, and budget views
- [x] Add `displayOrder` to all DTOs (CategoryDto, CategoryTreeDto, BudgetCategoryViewDto)
- [x] Unit tests: CategoryServiceTest (44), CategoryTreeDtoTest (5), BudgetViewServiceTest (17)
- [x] Integration tests: CategoryCrudIT (27), BudgetIT (30) including available pool 2-period flow
- [x] Orphaned expected amount cleanup when category becomes parent
- [x] Documentation update (ARCHITECTURE.md + CHECKPOINTS.md)

**Test totals: 590 unit + 256 integration = 846 tests, all passing**

---

### Checkpoint 16: Recurring Items Monthly View

**Phase 16 — Recurring item occurrence view by calendar month**

- [x] Extract `RecurringOccurrenceCalculator` utility from `BudgetViewService` — returns `List<LocalDate>` instead of just count
- [x] `BudgetViewService` refactored to use extracted calculator (`.countOccurrences()` convenience method)
- [x] 11 unit tests for calculator (monthly, biweekly, weekly, yearly, daily, end date, start date, count, multiple anchors, sorting)
- [x] `RecurringItemViewDto` and `RecurringItemMonthViewDto` DTOs
- [x] `RecurringItemViewService.getMonthView(workspaceId, YearMonth)` — queries ACTIVE items, computes occurrences, filters, sorts by first occurrence
- [x] `GET /api/v1/recurring-items/view` endpoint with `month` (YYYY-MM) and `periodOffset` params (mutually exclusive, defaults to current month)
- [x] 8 view service unit tests (happy path, inactive excluded, empty month, total summing, sorted by occurrence, end date exclusion, multiple anchors, no items)
- [x] 4 controller unit tests (default month, specific month, periodOffset, both-params-400)
- [x] 8 integration tests (default month, month param, periodOffset, both-params-400, inactive excluded, empty month, biweekly occurrences, item fields)
- [x] Documentation update (ARCHITECTURE.md + CHECKPOINTS.md)

**Test totals: 613 unit + 264 integration = 877 tests, all passing**

---

### Checkpoint 17: Per-Occurrence Amount Overrides

**Problem**: Recurring items have a fixed `amount`, but actual amounts vary (e.g., utility bills). Users need per-occurrence overrides visible in both the monthly view and budget calculations.

**What was done:**
- [x] V18 migration: `recurring_item_overrides` table with UNIQUE(recurring_item_id, occurrence_date), CASCADE DELETE, indexes
- [x] `RecurringItemOverride` entity + `RecurringItemOverrideRepository` (single lookup, date range, workspace+range queries)
- [x] `SetOccurrenceOverrideDto` — nullable amount (null = clear) + notes
- [x] `RecurringItemService.setOccurrenceOverride()` — validates occurrence date via calculator, upserts override
- [x] `RecurringItemService.clearOccurrenceOverride()` — deletes override if present
- [x] `PUT /api/v1/recurring-items/{id}/overrides/{date}` controller endpoint
- [x] Updated `RecurringOccurrenceDto` with `overridden` flag and `notes`
- [x] Updated `RecurringItemViewService` — batch-loads overrides for month range, applies to each occurrence DTO, affects expense/income totals
- [x] Updated `BudgetViewService.computeRecurringExpected()` — per-occurrence amounts using overrides instead of `amount × count`
- [x] 6 new service unit tests (set create, set update, invalid date, not found, clear existing, clear no-op)
- [x] 4 new view service unit tests (override applied, override affects totals, non-overridden false flag, overridden income/expense split)
- [x] 2 new controller unit tests (set with amount, clear with null amount)
- [x] 7 new integration tests (set valid, invalid date 400, clear, view shows override, cleared reverts to default, override affects totals, cascade delete)
- [x] Documentation update (ARCHITECTURE.md + CHECKPOINTS.md)

**Test totals: 628 unit + 271 integration = 899 tests, all passing**

---

### Checkpoint 18: Per-Occurrence Paid Flag, Date Overrides & Cleanup

**Phase 18 — Enhance recurring item overrides with paid flag, date shifting, and cleanup fixes**

**Paid flag + transaction linking (from prior session):**
- [x] Added `paid` (boolean) and `transactionId` (UUID) to `RecurringOccurrenceDto`
- [x] `TransactionRepository.findLinkedToRecurringItemsInDateRange()` — batch-loads linked transactions
- [x] Amount priority chain: **transaction amount > override amount > default amount** in both view and budget services
- [x] SeedDataLoader FK fix: `DELETE FROM recurring_item_overrides` before `DELETE FROM workspaces`
- [x] V18 migration amended: `ON DELETE CASCADE` on workspace_id FK

**Date override feature:**
- [x] V19 migration: `ALTER TABLE recurring_item_overrides ADD COLUMN date_override DATE`
- [x] `dateOverride` field added to `RecurringItemOverride` entity and `SetOccurrenceOverrideDto`
- [x] `originalDate` field added to `RecurringOccurrenceDto` (non-null only when shifted)
- [x] `RecurringItemOverrideRepository.findByWorkspaceIdAndDateOverrideBetween()` — loads overrides shifted INTO a month
- [x] `RecurringItemViewService` — cross-month shift logic: shifted-out occurrences removed, shifted-in added, sorted by effective date
- [x] `BudgetViewService.computeRecurringExpected()` — same cross-month shift logic for period bucketing
- [x] `RecurringItemService.setOccurrenceOverride()` — passes through dateOverride to entity
- [x] 2 new service unit tests (date override persists, cross-month persists)
- [x] 6 new view service unit tests (same-month shift, cross-month removes from original, cross-month appears in target, no shift keeps originalDate null, date override affects totals)
- [x] 1 new controller unit test (date override dispatches to set)
- [x] 5 new integration tests (same-month date override, cross-month removes from original, cross-month appears in target, no date override originalDate null, clear removes date override)
- [x] Documentation update (ARCHITECTURE.md + CHECKPOINTS.md)

**Override cleanup on schedule changes:**
- [x] `RecurringItemService.updateRecurringItem()` — deletes all overrides when schedule-defining fields change (frequencyGranularity, frequencyQuantity, anchorDates, startDate, endDate)
- [x] `RecurringItemOverrideRepository.deleteByRecurringItemId()` — bulk delete
- [x] 3 new service unit tests (frequency change clears, anchor change clears, non-schedule change doesn't clear)
- [x] 1 new integration test (anchor change clears overrides, new occurrence date reflected)

**Test totals: 642 unit + 280 integration = 922 tests, all passing**

---

### Checkpoint 19: Override Simplification & UUID-keyed API

**Problem**: The date override feature (shifting occurrences to different dates/months) added significant complexity to both the view service and budget service (cross-month shift logic). After reflection, the user decided: transaction linking provides the actual date/amount naturally — no need for `dateOverride`. The override table should only store per-occurrence amount + notes.

**Changes:**
- [x] Removed `dateOverride` from entity, DTOs, repository, service, view service, and budget service
- [x] Merged V19 (dateOverride column) and V20 (nullable amount) into V18 — amount is nullable from the start
- [x] UUID-keyed override API: `POST /{id}/overrides` (201), `PUT /{id}/overrides/{overrideId}` (200), `DELETE /{id}/overrides/{overrideId}` (204)
- [x] New `OccurrenceTransactionDto` — nested DTO with `id`, `date`, `amount` for linked transactions
- [x] Simplified `RecurringOccurrenceDto`: `date`, `expectedAmount`, `overrideId`, `notes`, `transaction` (nested or null)
- [x] Removed ALL cross-month shift logic from `RecurringItemViewService` (~80 lines deleted)
- [x] Removed shift logic from `BudgetViewService.computeRecurringExpected()`
- [x] Service: `createOverride` (validates date + dup check), `updateOverride` (by UUID), `deleteOverride` (by UUID)
- [x] Updated unit tests: 11 new override service tests, updated view/controller/budget tests
- [x] Updated integration tests: 6 dateOverride ITs deleted, 18 remaining updated for new API + DTO shape
- [x] Updated ARCHITECTURE.md: new API endpoints, occurrence DTO shape, test counts

**Test totals: 639 unit + 274 integration = 913 tests, all passing**

---

### Checkpoint 20: Nearest-Occurrence Transaction Matching & recurringExpected Fix

**Problem 1**: Transaction-to-occurrence linking used exact date matching (`riId:txnDate`). A paycheck due on the 15th but received on the 22nd would show `transaction: null` in the occurrence view — not linked even though `recurringItemId` was set.

**Problem 2**: `BudgetViewService.computeRecurringExpected()` was using the linked transaction's actual amount (e.g., 3378.74) for `recurringExpected`, instead of the planned amount (3392.53). `recurringExpected` should always reflect what you *expect* to pay, not what was actually paid — that's already captured in `activity`.

**Changes:**
- [x] `RecurringItemViewService` — replaced exact-date map with grouped-by-recurringItemId + nearest-occurrence matching via new `matchTransactionsToOccurrences()` static helper
- [x] `matchTransactionsToOccurrences()` — greedy 1:1 assignment: computes all (transaction, occurrence, distance) pairs, sorts by date distance, assigns closest unmatched pairs; each occurrence gets at most one transaction
- [x] `BudgetViewService.computeRecurringExpected()` — removed transaction matching entirely; `recurringExpected` is now always `override amount ?? default amount`, never transaction amount. Removed unused `txnsByRecurringItem` query from this method.
- [x] Seed data: added `recurringDescription` to 66 transactions (Netflix, Spotify, Verizon, Xfinity, GitHub, OpenAI, USAA, OneLife Fitness, Disney Plus, Liberty Mutual salary) to link them to recurring items on startup

**Anchor date / frequency clarification documented:**
- Anchor dates define the recurring pattern, not the first occurrence. They can be set to any date — the calculator projects forward and backward from each anchor, clipping to `startDate`/`endDate`.
- "Semi-monthly" (twice per month) = `frequencyGranularity: MONTH, frequencyQuantity: 1` with **2 anchor dates** (e.g., 15th and 28th). Each anchor projects independently on a monthly cycle.
- "Biweekly on Fridays" = `frequencyGranularity: WEEK, frequencyQuantity: 2` with **1 anchor** on a Friday.
- Changing frequency/anchors deletes all overrides (clean slate) but does not affect `activity` or `netTotalAvailable` — those are always transaction-driven.

**Test totals: 639 unit + 274 integration = 913 tests, all passing**

Ideas captured for future consideration:

- **Recurring item frequency changes** — Linked-list node splitting to track frequency history (schedule changes now clear all overrides)
- **Bulk transaction operations** — Delete/update multiple transactions at once (UI multi-select)
- **Duplicate detection** — Flag or prevent transactions with same amount/date/merchant
- **Transaction attachments/receipts** — File uploads on transactions (images, PDFs)
- **CSV import/export** — Import from bank exports, export for spreadsheets
- **Transaction templates** — Quick-create from saved templates (different from recurring)
- **Account-to-account transfers** — Single API call creates linked expense/income pair with shared `transferId`
- **Spring AI MCP server** — AI-powered transaction categorization and insights
- **Cloud deployment** — Production infrastructure, CI/CD pipeline

### Checkpoint 21: Separate Update DTO for Override PUT Endpoint

**Changes:**
- [x] Created `UpdateOccurrenceOverrideDto` — contains only `amount` and `notes` (no `occurrenceDate` required)
- [x] Updated `RecurringItemController.updateOverride()` to accept `UpdateOccurrenceOverrideDto`
- [x] Updated `RecurringItemService.updateOverride()` to accept `UpdateOccurrenceOverrideDto`
- [x] Updated controller and service unit tests to use the new DTO
- [x] Updated ARCHITECTURE.md override API docs to reflect PUT body shape

**Rationale:** The PUT endpoint identifies the override by its UUID path parameter — requiring `occurrenceDate` in the body was redundant and caused validation errors when omitted.

**Test totals: 639 unit + 274 integration = 913 tests, all passing**

## Nice to Have

- **Rules Engine** - Rule-based automation
- **Zapier Integration** - Add zaps for Zapier automation (not Dripl-specific)

### Checkpoint 22: Override Response DTOs and Test Assertion Improvements

**Changes:**
- [x] Override endpoints now return `RecurringItemOverrideDto` response bodies (POST→201+body, PUT→200+body, DELETE→204)
- [x] Strengthened 17 RecurringItemControllerTest assertions to verify response body fields (not just status codes)
- [x] Strengthened RecurringItemViewServiceTest `happyPath` to assert specific field values instead of `isNotNull()`

**Test totals: 647 unit + 274 integration = 921 tests, all passing**

### Checkpoint 23: Add `occurrenceDate` to Transaction, Remove Fuzzy Matching

**Problem:** The recurring item month view used a greedy nearest-date algorithm (`matchTransactionsToOccurrences()`) to pair transactions to occurrences. This was fragile — especially for frequent items (biweekly) where a transaction dated a few days off could match the wrong occurrence.

**Solution:** Added an explicit `occurrenceDate` (LocalDate) field to the Transaction entity. When linking a transaction to a recurring item, the caller must provide the `occurrenceDate` — identifying which specific occurrence is being paid. The month view now does a trivial exact lookup instead of fuzzy matching.

**Changes:**
- [x] Flyway migration `V19__add_occurrence_date_to_transactions.sql` — nullable `occurrence_date DATE` column + index
- [x] Transaction entity — added `occurrenceDate` (LocalDate) field, included in `snapshot()`
- [x] CreateTransactionDto — added `occurrenceDate`
- [x] UpdateTransactionDto — added `occurrenceDate` with specified-flag pattern (`assignOccurrenceDate()` / `isOccurrenceDateSpecified()`)
- [x] TransactionDto — added `occurrenceDate` to response
- [x] TransactionMapper — added `occurrenceDate` to ignore list in `updateEntity()`
- [x] TransactionService create — requires `occurrenceDate` when `recurringItemId` provided, rejects without RI
- [x] TransactionService update — requires on link, auto-clears on unlink, rejects standalone modification, added to locked fields
- [x] TransactionRepository — `findLinkedToRecurringItemsInDateRange` now filters by `occurrenceDate` (LocalDate) instead of transaction `date` (LocalDateTime)
- [x] RecurringItemViewService — replaced `matchTransactionsToOccurrences()` with simple `Map<String, Transaction>` keyed by `recurringItemId:occurrenceDate`, deleted entire fuzzy matching method (~40 lines)
- [x] RecurringItemService — `deleteRecurringItem()` now clears `occurrenceDate` on linked transactions before deletion (DB FK only handles `recurringItemId` via `ON DELETE SET NULL`)
- [x] SeedDataLoader — set `occurrenceDate` when seeding RI-linked transactions (derived from transaction date)
- [x] TransactionServiceTest — added 4 new tests (occurrenceDate validation + locked field), updated 7 existing
- [x] RecurringItemServiceTest — updated delete test to verify `occurrenceDate` clearing
- [x] RecurringItemViewServiceTest — added `occurrenceDate` to transaction builders
- [x] Integration tests — updated TransactionCrudIT (7 tests), TransactionSplitCrudIT (1), RecurringItemViewIT (2) with `occurrenceDate`
- [x] ARCHITECTURE.md — updated entity table, specified-flag list, matching docs, inheritance section, field locking, test counts

**Key design decisions:**
- `occurrenceDate` is nullable — only set when linked to a recurring item
- Required when setting `recurringItemId`, auto-cleared when unlinking
- Locked while linked — cannot change independently; must unlink and re-link
- No FK constraint — just a LocalDate; orphaned values are harmless
- Repository query filters by `occurrenceDate` (not `date`), so a transaction dated Feb 28 paying a March 1 occurrence correctly appears in the March view

**Test totals: 647 unit + 274 integration = 921 tests, all passing**
