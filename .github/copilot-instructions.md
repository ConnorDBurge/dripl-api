This file provides guidance to Copilot when working with code in this repository.

## Commands

```bash
# Build
./mvnw clean package -DskipTests

# Run all tests (unit + integration)
./mvnw verify

# Unit tests only (Surefire — *Test.java)
./mvnw test

# Integration tests only (Failsafe — *IT.java)
./mvnw failsafe:integration-test

# Run a single unit test class
./mvnw test -Dtest=TransactionServiceTest

# Run a single integration test class
./mvnw failsafe:integration-test -Dit.test=TransactionCrudIT

# Run the app locally (requires Postgres on localhost:5432)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Default DB credentials (dev): host `localhost:5432`, db/user/pass `dripl`.

## Architecture

This is a Spring Boot 3 / Java 21 monolith. Full architectural detail is in `.github/ARCHITECTURE.md` — read it before making significant changes. Checkpoint history is in `.github/CHECKPOINTS.md`.

**Package structure:** `com.dripl.<domain>` — `auth`, `user`, `workspace`, `account`, `merchant`, `tag`, `category`, `transaction`, `recurring`, `budget`, `common`. Each domain has `controller`, `dto`, `entity`, `mapper`, `repository`, `service` subpackages.

## Spring Profiles

| Profile | Purpose |
|---------|---------|
| (default) | Production-like: Flyway enabled, `ddl-auto: validate` |
| `dev` | Runs `SeedDataLoader` on startup — wipes and re-seeds DB from JSON files in `src/main/resources/seed-data/` |
| `test` | Unit tests — Flyway disabled, `ddl-auto: update`, no seed |
| `integration` | Integration tests — Flyway enabled, `ddl-auto: validate`, Testcontainers PostgreSQL |

## Key Patterns

**Workspace isolation:** Every workspace-scoped query filters by `workspaceId` extracted from the JWT — never from request params. JWT claims: `user_id`, `workspace_id`, `roles` (READ/WRITE/DELETE/OWNER). Method-level auth uses `@PreAuthorize("hasAuthority('WRITE')")` etc.

**Custom argument resolvers:** `@WorkspaceId`, `@UserId`, `@Subject` annotations on controller method params — resolved from the JWT by argument resolvers registered in `WebMvcConfig`.

**MapStruct mappers:** All domains use MapStruct with `componentModel = "spring"`. Partial updates use `NullValuePropertyMappingStrategy.IGNORE`. Side effects (e.g., `balanceLastUpdated`) go in `@AfterMapping` methods.

**Specified-flag pattern for PATCH:** Nullable optional fields use `@JsonSetter` methods to flip `*Specified` flags — distinguishes "field omitted" from "field sent as null". Example: `UpdateTransactionDto.setCategoryId()` sets `categoryIdSpecified = true`. Used for: `categoryId`, `notes`, `tagIds`, `recurringItemId`, `groupId`, `splitId` on transactions.

**Spec-based filtering:** `TransactionSpecifications` and `CategorySpecifications` use an `optionally()` helper that adds predicates only when params are non-null. Composed in the controller, passed to the service.

**Merchant auto-resolution:** `MerchantService.resolveMerchant(name, workspaceId)` does case-insensitive lookup, auto-creates if not found. Shared by `TransactionService` and `RecurringItemService`.

**Async events:** `ApplicationEventPublisher` → `@TransactionalEventListener` + `@Async` listeners (`REQUIRES_NEW`). Used for orphaned workspace cleanup (`WorkspaceCleanupListener`) and transaction change history (`TransactionEventService`). Failures don't roll back the parent transaction.

**Field locking on transactions:** Enforced purely in `TransactionService` — no DB triggers. Presence of `recurringItemId`/`groupId`/`splitId` determines locked fields. `splitId` is fully locked (cannot be set/cleared via the transaction API — use the splits API). RI and group can be unlinked via PATCH, bypassing lock checks via `isUnlinkingRecurringItem()` / `isUnlinkingGroup()`.

**Category polarity:** `CategoryService.validateCategoryPolarity()` enforces income categories only on positive amounts. Wired into all 4 domain services (Transaction, TransactionSplit, TransactionGroup, RecurringItem) on create and update.

**Balance:** `accounts.balance` = `startingBalance + SUM(transactions.amount)`, recomputed synchronously in the same `@Transactional` boundary on every transaction mutation via `COALESCE(SUM())`.

**Domain events:** `DomainEvent` record in `common/event` uses `"domain.action"` naming (e.g., `transaction.created`). `FieldChange` records per-field diffs with human-readable values (names not UUIDs). Designed for future Kafka pluggability.

**Budget service pattern:** Services return entities; controllers handle DTO mapping. Exception: `BudgetViewService` returns DTOs directly (computed projection, not entity CRUD). `Budget.toDto()` and `BudgetCategoryConfig.toDto()` live on the entities.

## Testing Requirements

- Write unit tests for each function's happy path and sad path, including coverage for all errors thrown.
- Add integration tests for all controller endpoints.

**Unit tests** (`*Test.java`): Standard Mockito/JUnit 5. Service tests mock repositories and dependent services. Controller tests use `@WebMvcTest`.

**Integration tests** (`*IT.java`): All extend `BaseIntegrationTest`. Singleton Testcontainers PostgreSQL container shared across all ITs. Use `TestRestTemplate`. Bootstrap users via `bootstrapUser(email, givenName, familyName)` helper — returns the full response map including the JWT token. Use Awaitility for assertions on async operations.

## Database Migrations

Flyway migrations live in `src/main/resources/db/migration/`. Current schema spans V1–V17 (one file per entity). When adding a new entity, create the next `V{N}__create_table_*.sql`.
