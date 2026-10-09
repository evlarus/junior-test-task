---
name: create-db-migration
description: Change the PostgreSQL schema of this service with a new Flyway migration - add a table, column, index or constraint, or change data. Use whenever an entity, a native query or a repository needs a schema change, or when the task mentions Flyway, a migration, the database schema or an index.
---

# Create a database migration

The schema is defined only by Flyway migrations (schema first). Hibernate never creates tables:
`spring.jpa.hibernate.ddl-auto=validate` fails the start when an entity does not match the schema.

## 1. Look at the current schema

- Existing migrations: `src/main/resources/db/migration/V1__create_spending_limit.sql` …
  `V5__add_transaction_retry_state.sql`.
- With the `postgres` MCP server (see `.mcp.json`, read-only user `mcp_readonly`) ask for the table
  definitions and indexes of the local database started with `docker compose up -d`.

## 2. Write the migration

- File: `src/main/resources/db/migration/V<next number>__<what_it_does_in_snake_case>.sql`, e.g.
  `V6__add_transaction_idempotency_key.sql`. Two underscores after the version.
- **Never edit a migration that is already on `main`**: Flyway checks checksums and the start fails.
  Fix mistakes with a new migration.
- Repeat business invariants as constraints: `NOT NULL`, `CHECK (amount > 0)`, `CHECK (account ~ '^[0-9]{10}$')`,
  foreign keys. Index foreign keys yourself (PostgreSQL does not), use partial indexes for filtered
  lookups (`WHERE limit_exceeded`, `WHERE status = 'PENDING'`).
- `spending_limit` rows are immutable (trigger `spending_limit_immutable`); do not add migrations that update them.
- Avoid the SQL keyword `transaction` as an identifier; the table is `bank_transaction`.
- Money is `NUMERIC(19, 2)`, rates `NUMERIC(19, 8)`, moments `TIMESTAMPTZ`, codes `VARCHAR` (not `CHAR`).
- No comments in the SQL file.

## 3. Map it

- Update the JPA entity in `<feature>/infrastructure/persistence/*Entity.java` with exactly matching
  `@Column(name, nullable, length, precision, scale)`; entities keep `@Getter`, `@Setter` and a protected
  no-args constructor, nothing else from Lombok.
- Update the mapping in the `Jpa*Repository` adapter; domain classes in `<feature>/domain` must stay free of
  JPA (`ArchitectureTest` checks it).

## 4. Test

- An integration test with `support/IntegrationTest` that writes and reads the new column, and one that
  proves a new constraint rejects bad data through `JdbcClient`, like
  `TransactionRepositoryIT.databaseRejectsAProcessedTransactionWithoutItsResult`.
- `ApplicationSmokeIT` fails if the migration or the entity mapping is wrong.

```bash
./mvnw verify
```

A local database that already ran the previous migrations is migrated on the next start; to start from
scratch use `docker compose down -v`.
