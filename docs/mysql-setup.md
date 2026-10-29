# MySQL setup

The `local` and `prod` Spring profiles use MySQL. Both run Flyway and use
`ddl-auto=validate` with `open-in-view=false`. Hibernate does not modify the schema.
Flyway clean and automatic baselining remain disabled.

## Provision a local database

Use MySQL 8.0.16 or newer so CHECK constraints are enforced; MySQL 8.4 is recommended.
As a database administrator, run the following after replacing the password placeholder:

```sql
CREATE DATABASE `personal-financedb`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE USER 'personal_finance_app'@'localhost' IDENTIFIED BY 'REPLACE_WITH_A_LOCAL_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON `personal-financedb`.* TO 'personal_finance_app'@'localhost';
```

The permissions cover application operations and the current Flyway migrations. For production,
run migrations with a dedicated deployment account and give the application account only the
permissions it needs for normal operations. Configure the database account host for the actual
deployment network.

Alternatively, copy root `.env.example` to ignored `.env`, configure `MYSQL_PASSWORD` and
`MYSQL_ROOT_PASSWORD`, and run `docker compose up -d mysql`. On a fresh named volume,
the MySQL image creates the database and dedicated user from the `MYSQL_*` values. Changing
these values does not update accounts in an already initialized volume. Keep existing volumes.

In the Spring terminal, export `DB_USERNAME` and `DB_PASSWORD` with the corresponding
application account values. Spring does not load `.env` automatically. `DB_URL` defaults to
`jdbc:mysql://localhost:3306/personal-financedb` locally. Set it explicitly for a custom port.
Then run from `core-api`:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

Keep the existing JWT and internal service keys configured as described in the root README.
For production, activate only `prod`, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, and
configure MySQL TLS in `DB_URL` for the deployment. The local profile enables HTTP development
cookies; production keeps the secure cookie defaults.

## Schema and existing data

The seven MySQL migrations in `core-api/src/main/resources/db/mysql` cover users, categories,
default-category initialization, transactions, budgets, mock imports, CSV imports, demo
connections, and token versions. UUIDs use `CHAR(36)` consistently in JPA and JDBC.
Dates use `DATE`, money uses `DECIMAL(19,2)`, and instants use UTC `DATETIME(6)` with a UTC
JDBC session. Tables explicitly use InnoDB and `utf8mb4_0900_bin` to preserve case-sensitive
constraints and identifiers. MySQL reserved identifiers are quoted.

The unchanged H2 migration SQL is retained under `src/test/resources/db/migration` and is
excluded from the production jar along with the H2 driver. The H2 console is removed.
New migrations need corresponding MySQL and test changes where applicable.

Changing the database connection does not transfer H2 data. Existing H2 files are left intact.
If records are needed, back up the H2 files, export the rows, and plan a separate import that
preserves UUIDs, numeric IDs, timestamps, password hashes, token versions, relationships, and
transaction external IDs. Load parent tables before dependent tables and adjust auto-increment
counters after explicit numeric IDs. Reconcile row counts and financial totals before switching
over. Do not copy H2 Flyway history into MySQL or baseline an unknown existing schema.

## Verification

Run `core-api/mvnw.cmd verify` with JDK 25 for the normal H2 suite and production packaging.
This also writes `target/test-runtime-classpath.txt` for the isolated Python HTTP QA harness;
the harness uses test classes and dependencies instead of adding H2 to the production jar.

To exercise real MySQL migrations and Hibernate validation, provision a separate disposable
database and account, then run from `core-api`:

```powershell
$env:MYSQL_TEST_URL = 'jdbc:mysql://localhost:3306/personal_finance_test'
$testCredential = Get-Credential -Message 'Disposable MySQL test account'
$env:MYSQL_TEST_USERNAME = $testCredential.UserName
$env:MYSQL_TEST_PASSWORD = $testCredential.GetNetworkCredential().Password
try {
    .\mvnw.cmd '-Dtest=MySqlPersistenceIntegrationTests' test
} finally {
    Remove-Item Env:MYSQL_TEST_URL, Env:MYSQL_TEST_USERNAME, Env:MYSQL_TEST_PASSWORD
}
```

The test writes fictional records and leaves them in that disposable database. It checks UUID
round trips, category initialization, budgets, demo connections, CSV confirmation and duplicate
detection, amount and ownership constraints, expiry cleanup, and a second idempotent Flyway run.
It is skipped unless `MYSQL_TEST_URL` is supplied. Never point it at a development or production
database containing records you need.
