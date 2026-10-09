---
name: write-limit-test
description: Write or change tests for the spending-limit logic of this service - the limit_exceeded flag, monthly totals, limit changes within a month, the default 1000 USD limit, month and time-zone boundaries, concurrent transactions, or the report of exceeded transactions. Use when asked to test a limit scenario, reproduce a bug in flags or remainders, or cover a new edge case.
---

# Write a test for the limit logic

## Rules the tests must respect

- Remainder = limit in force − everything spent in the business month (account, category), including the
  transaction itself. `limit_exceeded` is true only when the remainder is **below** zero; exactly 0 is not
  exceeded.
- The limit in force is the latest **client** limit set at or before the transaction moment. A newer limit
  never changes earlier transactions. Without a client limit the system default (1000 USD,
  `app.limits.default-amount`) applies.
- Months and rate days are counted in the business zone (`app.business-zone`, UTC by default), not in the
  offset the client sent.
- Money always has two decimals: compare with `Money.usd("100")` or `isEqualByComparingTo`, never with
  `BigDecimal.equals` on raw values.

## Pick the level

1. **Domain unit test** (fast, no Spring) — rules of `MonthlySpending.register` and `Transaction.process`.
   Put it in `src/test/java/com/evlarus/spendinglimit/limit/domain` or `.../transaction/domain`.
   Use `support/DomainFixtures` (`CLIENT`, `COUNTERPARTY`, `KZT`, `UTC_CALENDAR`, `clientLimit(id, usd, setAt)`,
   `defaultLimit(id, setAt)`). For a sequence of limits and transactions copy the `Scenario` helper of
   `transaction/domain/SpecificationScenariosTest`.
2. **Integration test** with the database — locking, persistence, queries. Annotate with
   `support/IntegrationTest` (PostgreSQL in Testcontainers, WireMock for Twelve Data, `MutableClock`).
   - Isolate data with `TestAccounts.unique()`; never clean tables.
   - Set client limits through `SpendingLimitRepository.add(SpendingLimit.setByClient(...))` with an explicit
     `setAt`, or through the API after moving the clock.
   - Count SQL statements with `support/QueryCounter` when a read must stay a single query.
   - Examples: `transaction/TransactionProcessingIT` (concurrency, retries, report),
     `limit/infrastructure/persistence/MonthlySpendingRepositoryIT` (locks).
3. **End-to-end over HTTP** — a full scenario as a client would see it. Copy
   `e2e/SpecificationScenariosE2EIT`: `clock.setTo(...)` before each request, `clock.reset()` in `@AfterEach`,
   rates via `TwelveDataStubs.stubCloses(twelveDataMock, "KZT", ...)` after `twelveDataMock.resetAll()`.
   Remember that a transaction dated after the clock (plus 5 minutes) is rejected with 422.

## Conventions

- Test names state the rule: `spendingExactlyTheWholeLimitIsNotExceeding`, not `test1`.
- One behaviour per test; arrange, act, assert separated by blank lines; AssertJ assertions.
- No comments in test code; amounts in USD in the test and converted in a helper, as
  `spendUsdAt(datetime, usd, expectedFlag)` does in the end-to-end test.
- Use days no other test uses when rates are stored (they are unique per currency and day).

## Run

```bash
./mvnw test -Dtest=MonthlySpendingTest
```

```bash
./mvnw verify -Dit.test=TransactionProcessingIT -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true
```

```bash
./mvnw verify
```

Integration tests need a running Docker. Domain coverage below 90% lines / 85% branches fails the build.
