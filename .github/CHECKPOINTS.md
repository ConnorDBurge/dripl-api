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

## Checkpoint 13: Transaction History / Event Log (Future)
Audit trail of create/update events per transaction.

- [ ] Plan TBD

## Checkpoint 14: Frontend (Future)
Next.js frontend with OAuth login.

- [ ] Scaffold Next.js app (dripl-ui)
- [ ] NextAuth.js with Google/Apple OAuth
- [ ] Bootstrap API call from NextAuth server-side
- [ ] JWT storage and authenticated API calls

## Checkpoint 15: AI & Deployment (Future)
Spring AI MCP server and cloud deployment.

- [ ] Spring AI MCP server integration
- [ ] Cloud deployment preparation
- [ ] CI/CD pipeline
