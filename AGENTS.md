# AGENTS.md

Instructions for AI coding agents (Codex, Cursor, Copilot, Claude Code) working in this repository.

## What this is

Spring Boot microservice of a bank: it receives expense transactions of client accounts in any supported
currency, converts them to USD with daily closing rates from Twelve Data, checks them against monthly USD
limits per expense category (`product`, `service`) and flags `limit_exceeded`. Clients set limits and list
the transactions that exceeded them. See `README.md` for the API and the business rules.

## Commands

| Task | Command |
|---|---|
| Build, all tests, coverage and architecture checks | `./mvnw verify` (needs a running Docker) |
| Unit tests only | `./mvnw test` |
| One unit test | `./mvnw test -Dtest=MonthlySpendingTest` |
| One integration test | `./mvnw verify -Dit.test=TransactionProcessingIT -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` |
| Local database for the `dev` profile | `docker compose up -d postgres` |
| Run locally (fixed rates, no API key) | `./mvnw spring-boot:run` |
| Whole stack in Docker | `docker compose up --build` |

Java 21, Spring Boot 4.1, Jackson 3, Hibernate 7, PostgreSQL 18, Flyway, Redis (rate cache), Testcontainers 2, WireMock 3.

## Architecture

Packages by feature, layers inside: `com.evlarus.spendinglimit.<feature>.{api, application, domain, infrastructure}`
with features `limit`, `rate`, `transaction` and the shared `common`.

- `domain` — plain Java: business rules live in domain methods (`MonthlySpending.register`,
  `Transaction.process`, `ExchangeRate.select`), not in services. Repository ports are interfaces here.
  No Spring, JPA or Jackson (`ArchitectureTest` fails the build otherwise).
- `application` — use cases and ports to external systems (`ExchangeRateProvider`, `PendingTransactionQueue`,
  read-side queries). Services orchestrate: load, call the domain method, save.
- `infrastructure` — JPA entities and adapters (`Jpa*Repository`), JDBC queries, the Twelve Data client,
  the Redis rate cache (`RedisExchangeRateCache`; a cache failure is logged and treated as a miss).
  Entities are separate from domain classes; aggregates reference each other by id.
- `api` — controllers, request/response records, MapStruct mappers. Controllers never use repositories.
- Feature packages must not form cycles (`ArchitectureTest`).

Concurrency: a transaction is processed under `SELECT ... FOR UPDATE` of its row and of the
`monthly_spending` row of its account, category and month (`TransactionProcessor`). HTTP calls to the rate
provider happen before, outside any database transaction. Pending transactions are claimed with
`FOR UPDATE SKIP LOCKED` and a lease (`JdbcPendingTransactionQueue`).

## Conventions

- No comments in new code: make intent clear through names. Existing comments stay.
- Current time only from the injected `java.time.Clock`; never `Instant.now()` in production code.
- Money is `Money` (two decimals, no silent rounding); timestamps are truncated to microseconds
  (`Timestamps.normalize`); account numbers print masked (`AccountNumber.toString`).
- JSON is snake_case and strict: unknown properties and type coercion are rejected; dates keep the client's
  offset. Errors are RFC 9457 `ProblemDetail` from `GlobalExceptionHandler`
  (`DomainException` → 422, `ConflictException` → 409).
- Records for DTOs and value objects; Lombok only in JPA entities (`@Getter`, `@Setter`, protected
  no-args constructor); MapStruct with `unmappedTargetPolicy=ERROR`.
- Schema changes only through new Flyway migrations; never edit a migration that is on `main`.
- Configuration through `@ConfigurationProperties` records and environment variables; no secrets in the
  repository (`.env` is git-ignored, `.env.example` documents variables).

## Tests

- Domain rules: plain JUnit 5 + AssertJ in `<feature>/domain`, with `support/DomainFixtures`.
- Web layer: `@WebMvcTest` with `MockMvcTester`.
- Integration: `support/IntegrationTest` only (one shared Spring context with PostgreSQL and Redis in Testcontainers,
  WireMock for Twelve Data and `MutableClock`); isolate data with `TestAccounts.unique()`; do not add
  `@MockitoBean` or per-test properties to integration tests.
- `e2e/SpecificationScenariosE2EIT` replays both scenarios of the specification over HTTP; keep it green.
- Line coverage of `*.domain` packages must stay ≥ 90%, branch coverage ≥ 85% (JaCoCo check).

## Git

- One branch and one pull request per piece of work; commit messages in English, imperative,
  `feat:`/`fix:`/`test:`/`docs:`/`build:`/`ci:`/`chore:` prefixes, a body explaining why.
- Do not add AI attribution trailers to commits.
- Only fixed bugs are added to `docs/ai-log.md`, and only after the author approves the entry.

## Skills and MCP

- Project skills live in `.claude/skills/` (`.agents/skills` links there): adding a rate provider, writing a
  limit test, creating a migration.
- `.mcp.json` connects a read-only PostgreSQL server (user `mcp_readonly`, password from `MCP_DB_PASSWORD`, default `mcp_readonly`) and
  Context7 for up-to-date library documentation.
