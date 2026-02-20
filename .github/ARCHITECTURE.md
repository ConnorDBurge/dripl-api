# Dripl — Architecture

## Overview

Dripl is a personal/family finance management application. It is built as a modular Spring Boot monolith with a Next.js frontend, backed by PostgreSQL.

## Stack

| Layer       | Technology              | Container     |
|-------------|-------------------------|---------------|
| Frontend    | Next.js (TypeScript)    | `dripl-ui`    |
| Backend     | Java 21 + Spring Boot   | `dripl-api`   |
| Database    | PostgreSQL              | `dripl-db`    |

## Infrastructure

All three containers run on a single Docker network (`dripl-network`).

```
dripl-network
├── dripl-ui   (:3000)   → Next.js frontend
├── dripl-api  (:8080)   → Spring Boot API
└── dripl-db   (:5432)   → PostgreSQL
```

## Package Structure

```
com.dripl
├── auth/
│   ├── config/             # SecurityConfig (filter chain, CORS, session policy)
│   ├── controller/         # DevTokenController (profile-gated), ProtectedController
│   ├── filter/             # JwtAuthFilter (OncePerRequestFilter)
│   ├── service/            # TokenService (mint JWTs with workspace context)
│   └── utils/              # JwtUtil (sign, validate, parse tokens)
├── user/
│   ├── controller/         # UserController (/users/self, /users/bootstrap)
│   ├── dto/                # BootstrapUserDto, UpdateUserDto, UserDto, UserResponse
│   ├── entity/             # User (JPA entity)
│   ├── repository/         # UserRepository
│   └── service/            # UserService (bootstrap, CRUD, delete with cleanup)
├── workspace/
│   ├── controller/         # WorkspaceController, CurrentWorkspaceController
│   ├── dto/                # CreateWorkspaceDto, UpdateWorkspaceDto, SwitchWorkspaceDto, WorkspaceDto, WorkspaceResponse
│   ├── entity/             # Workspace (JPA entity)
│   ├── enums/              # WorkspaceStatus
│   ├── listener/           # WorkspaceCleanupListener (async orphan cleanup)
│   ├── membership/
│   │   ├── dto/            # CreateMembershipDto, UpdateMembershipDto, WorkspaceMembershipDto
│   │   ├── entity/         # WorkspaceMembership (JPA entity)
│   │   ├── enums/          # Role, MembershipStatus
│   │   ├── event/          # MembershipDeletedEvent (carries correlationId)
│   │   ├── repository/     # WorkspaceMembershipRepository
│   │   └── service/        # MembershipService (publishes events on delete)
│   ├── repository/         # WorkspaceRepository
│   └── service/            # WorkspaceService (provision, switch, CRUD)
├── account/
│   ├── controller/         # AccountController (/api/v1/accounts)
│   ├── dto/                # AccountDto, CreateAccountDto, UpdateAccountDto
│   ├── entity/             # Account (JPA entity)
│   ├── enums/              # AccountType, AccountSubType, AccountSource, CurrencyCode
│   ├── mapper/             # AccountMapper (MapStruct with IGNORE null strategy, @AfterMapping for side effects)
│   ├── repository/         # AccountRepository
│   └── service/            # AccountService (CRUD with type-subtype validation)
├── merchant/
│   ├── controller/         # MerchantController (/api/v1/merchants)
│   ├── dto/                # MerchantDto, CreateMerchantDto, UpdateMerchantDto
│   ├── entity/             # Merchant (JPA entity)
│   ├── mapper/             # MerchantMapper (MapStruct)
│   ├── repository/         # MerchantRepository
│   └── service/            # MerchantService (CRUD with duplicate name prevention)
├── tag/
│   ├── controller/         # TagController (/api/v1/tags)
│   ├── dto/                # TagDto, CreateTagDto, UpdateTagDto
│   ├── entity/             # Tag (JPA entity)
│   ├── mapper/             # TagMapper (MapStruct)
│   ├── repository/         # TagRepository
│   └── service/            # TagService (CRUD with duplicate name prevention)
├── category/
│   ├── controller/         # CategoryController (/api/v1/categories, /categories/tree)
│   ├── dto/                # CategoryDto, CategoryTreeDto, CreateCategoryDto, UpdateCategoryDto
│   ├── entity/             # Category (JPA entity, self-referencing parent-child)
│   ├── mapper/             # CategoryMapper (MapStruct)
│   ├── repository/         # CategoryRepository (+ JpaSpecificationExecutor), CategorySpecifications (spec-based filtering)
│   └── service/            # CategoryService (CRUD, parent validation, circular ref check, polarity validation)
├── transaction/
│   ├── controller/         # TransactionController (/api/v1/transactions)
│   ├── dto/                # TransactionDto, CreateTransactionDto, UpdateTransactionDto (specified flags)
│   ├── entity/             # Transaction (JPA entity, @ElementCollection for tagIds)
│   ├── enums/              # TransactionStatus (PENDING/POSTED/ARCHIVED), TransactionSource (MANUAL)
│   ├── mapper/             # TransactionMapper (MapStruct)
│   ├── repository/         # TransactionRepository, TransactionSpecifications (spec-based filtering with optionally() helper)
│   ├── service/            # TransactionService (merchant auto-resolution, status lifecycle, cross-entity validation, recurring item inheritance)
│   ├── group/
│   │   ├── controller/     # TransactionGroupController (/api/v1/transaction-groups)
│   │   ├── dto/            # TransactionGroupDto, CreateTransactionGroupDto, UpdateTransactionGroupDto
│   │   ├── entity/         # TransactionGroup (JPA entity, @ElementCollection for tagIds)
│   │   ├── mapper/         # TransactionGroupMapper (MapStruct, computed fields via toBuilder)
│   │   ├── repository/     # TransactionGroupRepository
│   │   └── service/        # TransactionGroupService (membership reconciliation, group overrides)
│   └── split/
│       ├── controller/     # TransactionSplitController (/api/v1/transaction-splits)
│       ├── dto/            # TransactionSplitDto, CreateTransactionSplitDto, SplitChildDto, UpdateTransactionSplitDto, UpdateSplitChildDto
│       ├── entity/         # TransactionSplit (JPA entity)
│       ├── mapper/         # TransactionSplitMapper (MapStruct)
│       ├── repository/     # TransactionSplitRepository
│       └── service/        # TransactionSplitService (amount sum enforcement, child sign validation, polarity validation)
├── recurring/
│   ├── controller/         # RecurringItemController (/api/v1/recurring-items)
│   ├── dto/                # RecurringItemDto, CreateRecurringItemDto, UpdateRecurringItemDto (specified flags)
│   ├── entity/             # RecurringItem (JPA entity, @ElementCollection for anchorDates and tagIds)
│   ├── enums/              # RecurringItemStatus (ACTIVE/PAUSED/CANCELLED), FrequencyGranularity (DAY/WEEK/MONTH/YEAR)
│   ├── mapper/             # RecurringItemMapper (MapStruct)
│   ├── repository/         # RecurringItemRepository
│   └── service/            # RecurringItemService (merchant auto-resolution via shared MerchantService, cross-entity validation)
└── common/
    ├── annotation/         # @WorkspaceId, @UserId, @Subject
    ├── audit/              # BaseEntity (createdAt, updatedAt), JpaAuditConfig
    ├── config/             # WebMvcConfig (registers argument resolvers), FlexibleLocalDateTimeDeserializer
    ├── dto/                # BaseDto
    ├── enums/              # Status (ACTIVE, ARCHIVED, CLOSED — shared across domains)
    ├── exception/          # GlobalExceptionHandler, ErrorResponse, custom exceptions
    ├── filter/             # CorrelationIdFilter (MDC + response header), ApiKeyFilter
    ├── resolver/           # UserIdArgumentResolver, WorkspaceIdArgumentResolver, SubjectArgumentResolver
    └── seed/               # SeedDataLoader (dev-only, CommandLineRunner)
```

## Authentication & Authorization

### JWT-Based Auth

The API uses self-minted JWTs. No external identity provider is required for development.

**JWT Claims:**
- `sub` — subject (user email)
- `user_id` — UUID of the authenticated user
- `workspace_id` — UUID of the user's active workspace
- `roles` — authorization roles (`READ`, `WRITE`, `DELETE`, `OWNER`)

**Request Flow:**
```
Client → Authorization: Bearer <jwt> → CorrelationIdFilter → JwtAuthFilter → Controller (@WorkspaceId, @UserId injected)
```

### Development Token

A profile-gated endpoint (`/dev/token`) allows minting JWTs for Postman testing without an OAuth flow. This endpoint is disabled in production.

### OAuth (Future)

OAuth2 login (Google, Apple, etc.) will be handled by NextAuth.js in the frontend. The frontend will exchange the OAuth token for a Dripl JWT from the backend.

### Authorization

Method-level authorization using Spring Security:
```java
@PreAuthorize("hasAuthority('WRITE')")
@PostMapping
public AccountDto create(...) { ... }

@PreAuthorize("hasAuthority('DELETE')")
@DeleteMapping("/{id}")
public void delete(...) { ... }
```

Roles:
- **READ** — view resources
- **WRITE** — create and update resources
- **DELETE** — delete resources (independent of WRITE)
- **OWNER** — workspace management (invite/remove members, update workspace)

## Domain Model

### Entities

| Entity                | Table                   | Key Fields |
|-----------------------|-------------------------|------------|
| User                  | `users`                 | email, givenName, familyName, lastWorkspaceId |
| Workspace             | `workspaces`            | name, status |
| WorkspaceMembership   | `workspace_memberships` | user, workspace, roles, status |
| Account               | `accounts`              | workspaceId, name, balance, type, subType, currency, source, status |
| Merchant              | `merchants`             | workspaceId, name, status |
| Category              | `categories`            | workspaceId, name, parentId, income, excludeFromBudget, excludeFromTotals |
| Tag                   | `tags`                  | workspaceId, name, description, status |
| Transaction           | `transactions`          | workspaceId, accountId, merchantId, categoryId, amount, date, status, source, recurringItemId, groupId, splitId |
| RecurringItem         | `recurring_items`       | workspaceId, merchantId, accountId, categoryId, amount, frequencyGranularity, frequencyQuantity, anchorDates, status |
| TransactionGroup      | `transaction_groups`    | workspaceId, name, categoryId, notes, tagIds |
| TransactionSplit      | `transaction_splits`    | workspaceId, totalAmount, currencyCode, accountId, date |

### Workspace Isolation

All workspace-scoped entities include a `workspaceId` field. Queries are scoped at the repository/service layer:

```java
List<Account> findAllByWorkspaceId(UUID workspaceId);
```

The `workspaceId` is extracted from the JWT — never from request parameters — ensuring tamper-proof workspace isolation.

### Audit Fields

All entities inherit `createdAt` and `updatedAt` timestamps via JPA `@EntityListeners(AuditingEntityListener.class)`.

### Partial Updates

Entities support partial updates via a `Patchable` pattern with `@PatchableField` annotations, allowing PATCH endpoints to update only the fields provided in the request body.

## Observability

### Correlation ID

Every request is assigned a correlation ID via `CorrelationIdFilter`:
- Reads `X-Correlation-Id` from the request header, or generates a UUID
- Stored in SLF4J MDC for automatic inclusion in all log output
- Returned in the `X-Correlation-Id` response header
- Included in all `ErrorResponse` bodies
- Propagated to async threads via `MembershipDeletedEvent` for end-to-end tracing

### Log Format
```
2026-02-15T20:53:09.643Z  INFO [abc-123] [u:e0ccc5b2-...] [ws:a58c3e3b-...] --- [nio-8080-exec-3] c.d.workspace.service.WorkspaceService : Provisioned workspace 'Art's Workspace'
```

Logs include correlation ID, user ID, and workspace ID via MDC — service log messages no longer repeat these IDs inline. Human-readable names are included for quick identification. Bootstrap-time logs (before MDC is set) still include user info inline.

## Async Workspace Cleanup

When a membership is deleted, the system asynchronously checks if the workspace is orphaned (zero remaining members) and deletes it:

```
deleteMembership() → publishes MembershipDeletedEvent (with correlationId)
    → @Async @TransactionalEventListener
    → WorkspaceCleanupListener checks member count
    → Deletes workspace if count == 0
```

This keeps the delete endpoint fast while ensuring no orphaned workspaces accumulate.

## API Endpoints

### Identity
| Method | Path                          | Description              | Auth |
|--------|-------------------------------|--------------------------|------|
| POST   | `/api/v1/users/bootstrap`     | Create/find user + workspace | None |
| GET    | `/api/v1/users/self`          | Get current user         | AUTH |
| PATCH  | `/api/v1/users/self`          | Update current user      | AUTH |
| DELETE | `/api/v1/users/self`          | Delete current user      | AUTH |

### Workspaces
| Method | Path                                        | Description              | Auth  |
|--------|---------------------------------------------|--------------------------|-------|
| GET    | `/api/v1/workspaces`                        | List user's workspaces   | AUTH  |
| POST   | `/api/v1/workspaces`                        | Create workspace         | AUTH  |
| POST   | `/api/v1/workspaces/switch`                 | Switch active workspace  | AUTH  |
| GET    | `/api/v1/workspaces/current`                | Get current workspace    | READ  |
| PATCH  | `/api/v1/workspaces/current`                | Update current workspace | WRITE |
| GET    | `/api/v1/workspaces/current/members`        | List workspace members   | READ  |
| GET    | `/api/v1/workspaces/current/members/{userId}` | Get member             | READ  |
| POST   | `/api/v1/workspaces/current/members`        | Add member               | OWNER |
| PATCH  | `/api/v1/workspaces/current/members/{userId}` | Update member roles    | OWNER |
| DELETE | `/api/v1/workspaces/current/members/{userId}` | Remove member          | OWNER |

### Accounts
| Method | Path                          | Description              | Auth  |
|--------|-------------------------------|--------------------------|-------|
| GET    | `/api/v1/accounts`            | List accounts            | READ  |
| GET    | `/api/v1/accounts/{id}`       | Get account              | READ  |
| POST   | `/api/v1/accounts`            | Create account           | WRITE |
| PATCH  | `/api/v1/accounts/{id}`       | Update account           | WRITE |
| DELETE | `/api/v1/accounts/{id}`       | Delete account           | DELETE |

### Merchants
| Method | Path                          | Description              | Auth  |
|--------|-------------------------------|--------------------------|-------|
| GET    | `/api/v1/merchants`           | List merchants           | READ  |
| GET    | `/api/v1/merchants/{id}`      | Get merchant             | READ  |
| POST   | `/api/v1/merchants`           | Create merchant          | WRITE |
| PATCH  | `/api/v1/merchants/{id}`      | Update merchant          | WRITE |
| DELETE | `/api/v1/merchants/{id}`      | Delete merchant          | DELETE |

### Categories
| Method | Path                          | Description              | Auth  |
|--------|-------------------------------|--------------------------|-------|
| GET    | `/api/v1/categories`          | List categories          | READ  |
| GET    | `/api/v1/categories/tree`     | Get category tree        | READ  |
| GET    | `/api/v1/categories/{id}`     | Get category             | READ  |
| POST   | `/api/v1/categories`          | Create category          | WRITE |
| PATCH  | `/api/v1/categories/{id}`     | Update category          | WRITE |
| DELETE | `/api/v1/categories/{id}`     | Delete category          | DELETE |

**Category Features:**
- Spec-based filtering via `CategorySpecifications` + `optionally()` helper — query param: `income` (true/false)
- Both list and tree endpoints support the `income` filter
- Category polarity validation: `CategoryService.validateCategoryPolarity()` ensures income categories (`income=true`) are only assigned to positive amounts and expense categories (`income=false`) to negative/zero amounts. Called from TransactionService, TransactionSplitService, TransactionGroupService, and RecurringItemService on create and update

### Tags
| Method | Path                          | Description              | Auth  |
|--------|-------------------------------|--------------------------|-------|
| GET    | `/api/v1/tags`                | List tags                | READ  |
| GET    | `/api/v1/tags/{id}`           | Get tag                  | READ  |
| POST   | `/api/v1/tags`                | Create tag               | WRITE |
| PATCH  | `/api/v1/tags/{id}`           | Update tag               | WRITE |
| DELETE | `/api/v1/tags/{id}`           | Delete tag               | DELETE |

### Transactions
| Method | Path                          | Description              | Auth  |
|--------|-------------------------------|--------------------------|-------|
| GET    | `/api/v1/transactions`        | List transactions        | READ  |
| GET    | `/api/v1/transactions/{id}`   | Get transaction          | READ  |
| POST   | `/api/v1/transactions`        | Create transaction       | WRITE |
| PATCH  | `/api/v1/transactions/{id}`   | Update transaction       | WRITE |
| DELETE | `/api/v1/transactions/{id}`   | Delete transaction       | DELETE |

**Transaction Features:**
- Merchant auto-resolution: pass `merchantName` string → service does case-insensitive lookup, auto-creates if not found (shared `MerchantService.resolveMerchant()`)
- Status lifecycle: PENDING → POSTED → ARCHIVED, with `pendingAt`/`postedAt` timestamps set on transition
- Specified-flag pattern for nullable PATCH fields (`categoryId`, `notes`, `tagIds`, `recurringItemId`, `groupId`, `splitId`) — `@JsonSetter` methods flip `*Specified` flags to distinguish "not sent" from "sent as null"
- Tags via `@ElementCollection` (Set<UUID>) with `transaction_tags` join table
- `FlexibleLocalDateTimeDeserializer` accepts both `"2025-07-01"` and `"2025-07-01T00:00:00"`
- Cross-entity validation delegates to AccountService/CategoryService/TagService (returns 404, not 400)
- Uses verified entity IDs from service responses, not raw DTO values
- Spec-based filtering via `TransactionSpecifications` + `optionally()` helper — query params: `accountId`, `merchantId`, `categoryId`, `recurringItemId`, `groupId`, `splitId`, `status`, `source`, `currencyCode`, `tagIds` (any-match via join)
- Recurring item inheritance on create/update (see Recurring Items section)
- Field locking when linked to a recurring item, group, or split (see Field Locking section)

### Recurring Items
| Method | Path                              | Description              | Auth  |
|--------|-----------------------------------|--------------------------|-------|
| GET    | `/api/v1/recurring-items`         | List recurring items     | READ  |
| GET    | `/api/v1/recurring-items/{id}`    | Get recurring item       | READ  |
| POST   | `/api/v1/recurring-items`         | Create recurring item    | WRITE |
| PATCH  | `/api/v1/recurring-items/{id}`    | Update recurring item    | WRITE |
| DELETE | `/api/v1/recurring-items/{id}`    | Delete recurring item    | DELETE |

**Recurring Item Design:**
- A recurring item is a transaction template with scheduling metadata — no auto-generation of transactions
- `frequencyGranularity` (DAY/WEEK/MONTH/YEAR) + `frequencyQuantity` (integer, defaults to 1) defines the period
- `anchorDates` (`@ElementCollection List<LocalDateTime>`) — multiple anchors = multiple occurrences per period (e.g., "twice a month" = 2 anchors)
- `startDate`/`endDate` define the active window; `description` is optional
- Status: ACTIVE, PAUSED, CANCELLED
- Carries defaults: merchantId, accountId, categoryId, amount, currencyCode, notes, tagIds
- Transactions link to recurring items via nullable `recurringItemId` FK
- Smart matching (which periods are covered) is UI-side logic, not backend

**Transaction ↔ Recurring Item Inheritance:**
- When `recurringItemId` is provided on create or update, **locked fields always come from the recurring item** — DTO values for locked fields are ignored
- Locked fields: `accountId`, `merchantId`, `categoryId`, `tagIds`, `currencyCode`, `notes`
- Non-locked fields: `amount` (DTO wins if provided, RI is fallback default via `resolveRequired()`)
- On create: if `ri != null`, locked fields are set from RI; validation order ensures `accountId` is checked before merchant resolution
- On update: setting `recurringItemId` overwrites existing locked field values with RI values; clearing it (`null`) removes the link; omitting it preserves the current value
- Required fields (`accountId`, `merchantName`, `amount`) throw `BadRequestException` if neither DTO nor recurring item provides them

### Transaction Groups
| Method | Path                                   | Description                                        | Auth   |
|--------|----------------------------------------|----------------------------------------------------|--------|
| GET    | `/api/v1/transaction-groups`           | List groups (with totals)                          | READ   |
| GET    | `/api/v1/transaction-groups/{id}`      | Get group                                          | READ   |
| POST   | `/api/v1/transaction-groups`           | Create group with transactionIds (min 2)           | WRITE  |
| PATCH  | `/api/v1/transaction-groups/{id}`      | Update metadata AND/OR membership via transactionIds | WRITE |
| DELETE | `/api/v1/transaction-groups/{id}`      | Dissolve group (unlinks transactions)              | DELETE |

**Transaction Group Design:**
- A transaction group is a named container that bundles related transactions (e.g., "Beach Vacation 2025") — no synthetic transactions
- Groups are a first-class entity in a separate `transaction_groups` table, not a self-referencing FK on transactions
- `name` (required), `categoryId` (nullable), `notes` (nullable), `tagIds` (`@ElementCollection Set<UUID>`)
- Transactions link to groups via nullable `groupId` FK on the `transactions` table
- Group `categoryId`/`tagIds`/`notes` propagate down to member transactions on create, update, and add — `applyGroupOverrides()` pushes values and explicitly saves each transaction
- Computed fields in response: `totalAmount` (sum of children), `transactionIds` (Set<UUID>)
- Create requires at least 2 `transactionIds`; transactions must be in the same workspace; a transaction can only belong to one group
- `PATCH` with `transactionIds` does full set reconciliation: diffs current membership vs desired set, removes transactions no longer in the set (sets `groupId` to null), adds new transactions (validates, sets `groupId`, applies overrides), enforces min-2 on the desired set
- Deleting a group dissolves it — child transactions become standalone again (`groupId` set to null via bulk JPQL)
- `GET /transactions?groupId=...` filters transactions by group — no separate endpoint needed
- `@Modifying(flushAutomatically = true, clearAutomatically = true)` on bulk JPQL queries (`setGroupId`, `clearGroupId`) to ensure parent entity is flushed before FK reference and stale entities are evicted
- Lazy `@ElementCollection` values (e.g., `group.getTagIds()`) must be eagerly captured into local variables before JPQL with `clearAutomatically = true` runs, to avoid `LazyInitializationException`

### Transaction Splits
| Method | Path                                   | Description                                        | Auth   |
|--------|----------------------------------------|----------------------------------------------------|--------|
| GET    | `/api/v1/transaction-splits`           | List splits (with computed fields)                 | READ   |
| GET    | `/api/v1/transaction-splits/{id}`      | Get split                                          | READ   |
| POST   | `/api/v1/transaction-splits`           | Split an existing transaction                      | WRITE  |
| PATCH  | `/api/v1/transaction-splits/{id}`      | Add/remove/update children                         | WRITE  |
| DELETE | `/api/v1/transaction-splits/{id}`      | Dissolve split (delink children)                   | DELETE |

**Transaction Split Design:**
- A transaction split divides an existing transaction into ≥2 child transactions with independent categories/merchants while enforcing that amounts sum to the original total
- Splits are a first-class entity in a separate `transaction_splits` table — no parent transaction concept (the original is deleted)
- `totalAmount` (required, immutable), `currencyCode` (from original), `accountId` (from original), `date` (from original) — all captured at split time
- No name, no categoryId, no tagIds, no notes — the split is a lightweight container; child transactions hold all the detail
- Transactions link to splits via nullable `splitId` FK on the `transactions` table
- Children inherit `accountId`, `currencyCode`, and `date` from the split (locked). Other fields (`merchantName`, `categoryId`, `tagIds`, `notes`) default to the original but can be overridden per child
- **Amount invariant**: sum of child amounts must always equal `totalAmount`, enforced on create and update
- **Child sign invariant**: all child amounts must match the sign of `totalAmount` — prevents splitting income into mixed positive/negative children
- **Category polarity**: child categories are validated against the child's amount via `validateCategoryPolarity()`
- Create: specify a source `transactionId` + child definitions; source is deleted, children are created with `splitId`
- `PATCH` with `children` does full reconciliation: children with `id` → update, without `id` → create new, missing existing → delete; amounts must sum to totalAmount, min 2 children
- Deleting a split dissolves it — child transactions become standalone (`splitId` set to null via bulk JPQL)
- `GET /transactions?splitId=...` filters transactions by split
- **Split children CAN be RI-linked** (e.g., one gym charge split into two separate membership recurring items) — RI's `accountId` and `currencyCode` must match the split's values

**Field Locking:**

When a transaction is linked to a recurring item, in a group, or in a split, certain fields are immutable — changes are rejected with 400.

| Link Type | Locked Fields | Unlock Method |
|-----------|--------------|---------------|
| Recurring Item (`recurringItemId != null`) | `accountId`, `merchantName`, `categoryId`, `tagIds`, `notes`, `currencyCode` | `PATCH /transactions/{id}` with `{"recurringItemId": null}` |
| Group (`groupId != null`) | `categoryId`, `tagIds`, `notes` | `PATCH /transactions/{id}` with `{"groupId": null}` |
| Split (`splitId != null`) | `accountId`, `currencyCode`, `amount`, `date` | N/A — `splitId` is fully locked; use `DELETE /transaction-splits/{id}` to dissolve |

- **Mutual exclusivity**: A transaction cannot be in a **group AND a split** simultaneously. A **grouped transaction cannot be RI-linked**. However, a **split child CAN be RI-linked** (e.g., one gym charge split into two memberships with separate RIs). When both split + RI locks apply, the union of locked fields is enforced.
- **Unlink bypasses locking**: Setting `recurringItemId` or `groupId` to null bypasses the lock check via helper methods, allowing unlink + modify locked fields in a single request. **`splitId` cannot be unlinked** — any modification to `splitId` via the transaction API is rejected; use the transaction-splits API to dissolve
- **Linking asymmetry**: `recurringItemId` can be set directly on the transaction. `groupId` can only be set via the group API. `splitId` is fully managed by the splits API — setting or nulling `splitId` on the transaction endpoint throws `BadRequestException`
- **Min-2 enforcement**: Unlinking a transaction from a group via null checks the remaining member count before removing — if fewer than 2 would remain, the request is rejected (use DELETE to dissolve instead)
- **No DB columns or enums**: Locking is inferred from the presence of `recurringItemId`, `groupId`, or `splitId` on the transaction, enforced purely in the service layer

## Testing

### Unit Tests (448)
- **Services**: UserService (15), WorkspaceService (18), MembershipService (14), TokenService (4), AccountService (18), MerchantService (13), TagService (15), CategoryService (36), TransactionService (74), RecurringItemService (33), TransactionGroupService (19), TransactionSplitService (19)
- **Controllers**: UserController (12), WorkspaceController (9), CurrentWorkspaceController (11), AccountController (6), MerchantController (6), TagController (6), CategoryController (8), TransactionController (6), RecurringItemController (6), TransactionGroupController (5), TransactionSplitController (5)
- **Utilities**: JwtUtil (7), GlobalExceptionHandler (12), WorkspaceCleanupListener (3)
- **Domain**: AccountTypeSubTypeTest (58), CategoryTreeDtoTest (5)
- **Context**: DriplApplicationTests (1)

### Integration Tests (183)
All IT tests use Testcontainers (PostgreSQL 17 Alpine) with a singleton container pattern.

- **BootstrapAndAuthIT** (7): New user bootstrap, idempotent re-bootstrap, validation, auth/unauth access
- **UserCrudIT** (5): GET/PATCH/DELETE self, duplicate email, empty email
- **WorkspaceCrudIT** (7): List, provision, duplicate name, blank name, switch, GET/PATCH current
- **MemberManagementIT** (6): List/add/get/update/remove members
- **RoleBasedAccessIT** (9): READ-only user restrictions, WRITE-only cannot delete, OWNER full access
- **WorkspaceCleanupIT** (3): Orphan deletion on last member removal, multi-workspace user deletion, shared workspace preservation
- **CorrelationIdIT** (4): Header echo, auto-generation, error response inclusion, async propagation to cleanup thread
- **AccountCrudIT** (12): Create with defaults, create with all fields, list accounts, get by ID, update partial, update balance, update status, delete account, workspace isolation, duplicate name prevention, invalid type-subtype combo
- **MerchantCrudIT** (11): Create merchant, list merchants, get by ID, update name, update status, archive merchant, delete merchant, workspace isolation, duplicate name prevention, create without name, get nonexistent
- **TagCrudIT** (13): Create with name only, create with description, list tags, get by ID, update name, update description, update status, delete tag, workspace isolation, duplicate name, case-insensitive duplicate, update to duplicate name, get nonexistent
- **CategoryCrudIT** (22): Create root category, create with all fields, create child, child depth limit, parent not found, list categories, get by ID, get with children, get tree, update name, set parent, remove parent, parentId omitted preserves parent, self-parent, parent too deep via update, category with children cannot be nested, parent not found via update, clear children, delete category, delete parent cascades SET NULL, workspace isolation, get nonexistent
- **TransactionCrudIT** (32): Create with existing/new merchant, case-insensitive merchant lookup, list, get, partial update, status transition, merchant change, clear category, set/clear tags, delete, 404, workspace isolation, validation errors, RI inheritance on create, RI locked fields on create, missing required fields, set/clear recurringItemId, RI overwrites existing locked fields, reject currencyCode while RI-linked, field locking (recurring + group), mutual exclusivity, groupId unlink (success, min-2 enforcement, unlink + modify locked fields, assign groupId rejects)
- **RecurringItemCrudIT** (16): Full CRUD, workspace isolation, merchant auto-resolution, tag management, validation
- **TransactionGroupCrudIT** (17): Create group, list groups, get group, update metadata, add/remove transactions via transactionIds, remove below minimum, dissolve group, already-grouped, transaction shows groupId, min 2, create override, update override, add inherits overrides, remove clears groupId, add RI-linked rejects, delete clears all groupIds
- **TransactionSplitCrudIT** (20): Create split, list splits, get split, update children, add/remove children, dissolve split, amount mismatch on create/update, locked field rejection (accountId, amount, date), allow category change, split child can't be grouped, grouped txn can't be split, split child RI-linked, RI account mismatch, unlinkSplitChild rejects, assign splitId rejects, child shows splitId, filter by splitId

### Test Infrastructure
- **Testcontainers**: Singleton PostgreSQL container shared across all IT tests
- **Awaitility**: Async assertion polling for event-driven cleanup verification
- **Profiles**: `test` (unit — ddl-auto: update, flyway disabled), `integration` (IT — ddl-auto: validate, flyway enabled)
- **Maven**: Surefire for `*Test.java`, Failsafe for `*IT.java`

## Key Design Decisions

1. **Monolith over microservices** — Single app, single database. All domain modules are packages, not services. Eliminates inter-service communication, Kafka, and operational overhead.

2. **Self-minted JWTs over Keycloak** — The app mints its own tokens with workspace/user claims. No external identity provider dependency for development. OAuth added later via NextAuth.js.

3. **MapStruct for entity-DTO mapping** — All domains use MapStruct mappers with `componentModel = "spring"`. Partial updates use `NullValuePropertyMappingStrategy.IGNORE` at the class or field level. Side effects (e.g., `balanceLastUpdated`, `closedAt`) handled via `@AfterMapping`.

4. **No RLS** — Workspace isolation is enforced at the application layer via repository queries scoped by `workspaceId` extracted from JWTs. Simpler than PostgreSQL RLS for a single-app architecture.

5. **Dev token endpoint** — Enables full API testing via Postman without any OAuth infrastructure. Profile-gated to prevent production exposure.

6. **Event-driven sync eliminated** — v1 used Kafka to sync workspace resources across services. In a monolith, all data is in one database — no sync needed.

7. **Async event-driven cleanup** — Orphaned workspace cleanup uses Spring's `@TransactionalEventListener` + `@Async` to decouple cleanup from the request thread while ensuring it happens reliably after transaction commit.

8. **Correlation ID via MDC** — Request tracing uses SLF4J MDC rather than passing correlation IDs through method parameters. This keeps service methods clean while enabling end-to-end tracing across sync and async boundaries.

9. **Dev-only seed data** — A `@Profile("dev")` `CommandLineRunner` wipes and recreates all data on every startup from JSON files (`users.json`, `workspaces.json`). Uses the service layer (not repositories) to seed users, workspaces, memberships, accounts, merchants, tags, and categories (with parent-child nesting). A fake `SecurityContext` (`seed-data@dripl.dev`) is set during seeding so JPA auditing populates `createdBy`/`updatedBy`. Database wipe leverages `ON DELETE CASCADE` — only `workspaces` and `users` need explicit deletion. All `com.dripl` logging is muted to WARN during seeding to keep startup output clean; a summary table is printed after seeding completes.

10. **Shared Status enum** — A single `com.dripl.common.enums.Status` enum (`ACTIVE`, `ARCHIVED`, `CLOSED`) is shared across Account, Merchant, Tag, and Category domains. Domain-specific status enums (`WorkspaceStatus`, `MembershipStatus`) remain separate where they have unique values.

11. **Category hierarchy (max depth 2)** — Categories support one level of nesting via a self-referencing `parent_id` FK (`ON DELETE SET NULL`). The service validates that a parent exists in the same workspace, is itself a root category (no grandchildren), and prevents circular references by walking the parent chain. Deleting a parent promotes its children to root. The `/categories/tree` endpoint builds the hierarchy in memory using `CategoryTreeDto.buildTree()` — groups categories by `parentId`, then recursively assembles branches sorted by name.

12. **Spec-based filtering** — `GET /api/v1/transactions` and `GET /api/v1/categories` support optional query params. Controllers build a `Specification<T>` using `TransactionSpecifications` / `CategorySpecifications` with an `optionally()` helper that only adds a predicate when the param is non-null. Transaction filtering supports `accountId`, `merchantId`, `categoryId`, `recurringItemId`, `groupId`, `splitId`, `status`, `source`, `currencyCode`, `tagIds`. Category filtering supports `income`. Tag filtering uses a join to `transaction_tags` with any-match semantics. The spec is composed in the controller and passed to the service, keeping filtering logic declarative and composable.

13. **Shared merchant resolution** — `MerchantService.resolveMerchant(name, workspaceId)` does case-insensitive lookup and auto-creates if not found. Both `TransactionService` and `RecurringItemService` delegate to this shared method instead of having private copies.

14. **MDC-based observability** — User ID and workspace ID are set in MDC by the JWT filter and included in every log line via the log pattern (`[u:userId] [ws:workspaceId]`). Service-level log messages no longer repeat these IDs inline, keeping log statements concise.

15. **Service-layer field locking** — When a transaction is linked to a recurring item, group, or split, locked fields are enforced purely in `TransactionService` — no DB triggers, no extra columns. The presence of `recurringItemId`, `groupId`, or `splitId` on the transaction implies which fields are locked. Locked fields always come from the source entity on link. Unlink requests for RI and group bypass locking checks via helper methods (`isUnlinkingRecurringItem()`, `isUnlinkingGroup()`), allowing unlink + modify in a single PATCH. `splitId` is fully locked — any attempt to set or null it via the transaction API is rejected; splits must be dissolved via the transaction-splits API to preserve the amount sum invariant.

16. **Category polarity validation** — `CategoryService.validateCategoryPolarity()` ensures income categories (`income=true`) are only assigned to positive amounts and expense categories (`income=false`) to negative/zero amounts. This is a cross-cutting concern wired into all 4 domain services (TransactionService, TransactionSplitService, TransactionGroupService, RecurringItemService) on both create and update paths. For groups, the category is validated against every member transaction's amount. For splits, each child's category is validated against its own amount, and all children must match the sign of the split's `totalAmount`.
