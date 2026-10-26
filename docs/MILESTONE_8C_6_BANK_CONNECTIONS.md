# Milestone 8C.6: Bank Connections and statement import

## Existing integration audit

- `integration-service/src/routes/mock-transactions.js` exposes **GET `/internal/mock-transactions`**, protected by a separate internal API key. `src/data/mock-transactions.js` contains five fixed fictional PHP records; the normalizer converts debit/credit to positive decimal-string INCOME/EXPENSE records. Express rejects unsupported queries, does not expose user credentials, and does not log provider payloads. It has no live bank connectivity. No Node changes were necessary.
- Spring's existing **POST `/api/v1/transactions/import/mock`** fetches through `MockTransactionClient`, validates the exact provider contract and bounded response, seeds existing default categories once, and calls `TransactionService.importMock` in a separate transaction per row. Errors produce imported/skipped/failed/processed counts. This endpoint remains available with its original contract.
- Existing `TransactionRequest` requires an owned, matching category; positive `BigDecimal` with at most 17 integer digits and two fraction digits; a date; and a description of at most 255 characters. Finance writes serialize on the existing pessimistic user lock. The database enforces category/owner/type with a composite foreign key.
- V5 introduced `MANUAL`/`MOCK_PROVIDER`, external IDs, and unique `(user_id, source, external_transaction_id)`. The bank module reuses that constraint and adds `CSV_STATEMENT` through V6. It does not add a balance to finance reporting.
- `SecurityConfiguration` authenticates versioned APIs using the existing HttpOnly JWT cookie; cookie mutations require XSRF tokens. The existing deliberate header-only bearer exception is preserved. Owners are derived from `Authentication`, never supplied by clients. Angular's existing interceptor bootstraps CSRF, invalidates expired sessions, cancels obsolete session requests and does not retry submitted mutations.
- Angular finance lists and dashboard track `FinanceChanges.revision`; confirmed imports and successful demo sync notify that same mechanism. Budgets and dashboard aggregate the existing transactions table and need no new reporting calculations.
- Persistence uses Flyway and H2 SQL in the current runnable Spring configuration. Docker Compose also defines MySQL, but existing migrations and current API dependencies are H2-specific; this milestone does not claim MySQL verification or introduce a new database. JUnit uses isolated H2 and fictional fixtures; Angular uses Vitest; Node uses `node:test`. Existing opt-in Python QA starts isolated services with mock AI/Mongo dependencies.

## API

All endpoints below use `/api/v1/bank-connections`, require the existing authenticated session, and inherit its CSRF policy and error envelope. Browser requests are same-origin; never send banking credentials. No response is automatically retried.

| Method | Suffix                     | Request                                     | Result                                                                                                                          |
| ------ | -------------------------- | ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| GET    | (empty)                    | none                                        | One fictional provider/account, masked number, `simulated: true`, connection status, demo PHP balance and last sync             |
| POST   | `/demo`                    | empty or `{}`                               | Connect/reconnect the current user's demo account                                                                               |
| DELETE | `/demo`                    | none                                        | Disconnect; retain existing imported transactions and last sync                                                                 |
| POST   | `/demo/sync`               | empty or `{}`                               | Require connected state; reuse existing Node provider and Spring mock import; update last successful sync; return count summary |
| POST   | `/statements/columns`      | multipart `file`                            | Validate size/encoding/CSV structure; return header names; retain nothing                                                       |
| POST   | `/statements/preview`      | multipart file and mapping fields below     | Store normalized preview only; return `id`, `expiresAt`, `headers`, `rows`                                                      |
| POST   | `/statements/{id}/confirm` | `{"rows":[{"rowNumber":1,"categoryId":7}]}` | Revalidate server-held rows and categories, atomically save selected transactions, return count summary                         |
| DELETE | `/statements/{id}`         | none                                        | Cancel and remove preview content; HTTP 204                                                                                     |

Mapping fields are zero-based indices: `date`, `description`, `amount`, `debit`, `credit`, `reference`, `currency`. Use `-1` for unused fields. Map either `amount` OR both `debit` and `credit`. Also supply `dateFormat` (`ISO`, `DMY`, `MDY`) and `account` (stable alias, 1�40 ASCII letters/digits/underscore/hyphen; do not use a real account number). Example: `date=0, description=1, amount=2, debit=-1, credit=-1, reference=3, currency=-1, dateFormat=ISO, account=checking`.

Preview row DTO: `rowNumber`, ISO `transactionDate`, literal `description`, `type`, decimal-string `amount`, `validationError`, `duplicate`, nullable `suggestedCategoryId`. Angular offers conservative category-name suggestions from the user's loaded categories; no category is selected automatically. Invalid values have generic field feedback rather than echoed rejected content. Confirmation accepts only row numbers and category IDs; owner, amount, date, description and source cannot be overridden. Unknown JSON fields are rejected by the existing Jackson configuration.

Summary: `importedCount`, `skippedCount`, `failedCount`, `totalProcessed`. CSV confirmation is atomic: any invalid selection rejects the whole request and rolls back all writes. Excluded rows and known duplicates count as skipped; successful CSV confirmation has zero failed rows. Mock sync retains existing per-row failure counts. Repeated confirmation returns the saved receipt even if a different selection is sent later; it never imports additional rows under the same ID.

Errors use the existing `ApiError` shape. Expect 400 for malformed CSV/mapping/selection, 401 for missing/expired session, 403 for missing/invalid cookie CSRF, 404 for an unavailable or another owner's import, 409 for disconnected sync/cancelled/expired preview, and 413 for oversized uploads. Provider errors retain existing 502/503/504 codes. Multipart or mapping binding failures return controlled 400 errors.

## Workflow and limitations

1. Open **Bank Connections** in the sidebar. Demo banking is explicitly marked SIMULATION throughout; connecting requests no banking secrets. Sync intentionally adds fictional transactions to finance reports. The displayed demo balance is a labeled constant and is never included in report balances.
2. Choose a UTF-8 comma-separated statement. Header detection proposes column mappings; review and change them. Give the account a stable alias, choose signed amounts or debit/credit, and explicitly confirm the date order. `ISO` uses `yyyy-MM-dd`; DMY/MDY accept slash or hyphen separators and four-digit years. Other bank layouts must be converted before upload.
3. Review validation errors and duplicate indicators; exclude unwanted rows. Choose owned matching categories for every selected row, including rows with a suggestion. Uploaded descriptions are interpolated as text, never HTML. No expressions or formulas are executed.
4. Confirm explicitly. The server uses stored normalized rows, verifies owner and expiry, validates the finance DTO and category owner/type again, serializes writes using the existing user lock, and commits all selected rows plus receipt together. Client memory is cleared on success/cancellation. Cancel and reselect a file to correct column mappings. Starting another preview replaces the owner's previous preview, including another tab.
5. Finance lists, budgets, and dashboard refresh through the existing change notification. The server remains the source of truth for aggregates.

Only PHP statements are supported because the existing finance ledger has no currency column. A mapped currency column must contain `PHP` in every valid row. Without a currency column, the user must ensure the statement is PHP. No currency conversion is inferred. The existing Angular finance safety guard supports individual amounts up to PHP 1 trillion. CSV rows above that limit are marked invalid server-side rather than creating records the current finance UI cannot safely display. The existing manual API precision contract is unchanged. Amount parsing preserves `BigDecimal` precision, accepts conventional comma thousands separators inside quoted cells and decimal points, and rejects symbols, exponents, expressions, zero, excess precision, negative debit/credit, or both debit and credit populated. Signed positives mean income and negatives mean expense.

## Deduplication and idempotency

- Demo sync retains the existing owner + `MOCK_PROVIDER` + fixed provider ID uniqueness, including imports through the legacy mock endpoint. Disconnect/reconnect does not reset imported IDs.
- With a mapped unique bank reference, the CSV external ID is SHA-256 of account alias + reference. This detects overlapping and reordered exports from the same account. Use a reference that uniquely identifies an individual bank transaction, not a shared transfer/batch reference.
- Without a reference, the ID uses account alias + SHA-256 of exact file bytes + one-based data-row position. Reimporting the identical file cannot duplicate its rows; identical legitimate rows at different positions remain distinct. Changed or overlapping exports without references require manual review and exclusion. The UI explains this limitation.
- Keys are scoped by the authenticated user and `CSV_STATEMENT`; the database's unique constraint is the final backstop. Date, amount and description are not used as proof of identity. Changing the account alias changes the deduplication scope. Deleting an imported transaction also removes its transaction-level deduplication key; reimport can recreate an intentionally deleted record.
- The same preview ID returns its persisted receipt for 30 days. After receipt removal it returns 404; transaction external IDs continue to prevent repeated rows while those transactions exist. Concurrent confirmations are serialized and covered by tests. There is no automatic write retry or queue.

## Schema, security and retention

V6 is additive and leaves V1�V5 checksums unchanged. It extends the source check, adds `demo_connections` (one state per owner), `statement_imports` (owner, expiry, state and counts) and `statement_rows` (normalized fields, external hash, generic errors). JDBC participates in the existing JPA transaction manager and shares the user lock. Database category foreign keys and source/external unique constraints remain active.

The initial application configuration had no explicit multipart limits. The module sets **1 MiB per file**, **1,100 KiB per multipart request**, and a **2 MiB memory threshold** (above the entire allowed request). Raw uploads are parsed in memory and are not written into application storage. Parser limits: 1�1,000 data records, at most 50 columns, at most 4,096 characters per field, strict UTF-8 with optional BOM, consistent record widths, strict quoting, and no binary control characters. Quoted commas, escaped quotes and multiline descriptions are handled. Filename extensions and browser MIME types are hints; server content parsing decides validity.

Normalized previews expire after 30 minutes; scheduled cleanup runs every minute. Confirmation, cancellation and replacement immediately remove preview rows. Cleanup removes expired rows and marks the batch expired; receipts without financial row content are kept for 30 days after the expiry timestamp. Cleanup resumes on application startup, so expired content can remain on disk during downtime but cannot be confirmed. DB backups and disk encryption follow deployment policy; cleanup does not retroactively erase backups. This defines retention for normalized previews, not permanent raw-statement storage.

No new credentials, external bank calls, AI requests or authentication contract changes. Private uploads never pass through Node, FastAPI, MongoDB or the AI assistant. Raw file content is never logged. Web logging remains INFO, JDBC diagnostics are INFO, and Hibernate bind/extract logging is OFF to prevent value leakage under parent debug/trace configuration. No financial data is stored in localStorage/sessionStorage. The existing theme preference remains the only localStorage use.

## Files

Created:

- `core-api/src/main/java/core_api/bank/{BankController,BankService,BankException,StatementCsv,StatementCleanup}.java`
- `core-api/src/main/resources/db/migration/V6__bank_connections_csv_import.sql`
- `core-api/src/test/java/core_api/bank/{BankIntegrationTests,StatementCsvTests}.java`
- `frontend/personal-finance-ui/src/app/core/services/bank.ts`
- `frontend/personal-finance-ui/src/app/features/bank-connections/bank-connections.{ts,html,scss,spec.ts}`
- This milestone document.

Modified:

- `core-api/src/main/java/core_api/transaction/{Transaction,TransactionSource}.java`: imported CSV source and factory.
- `core-api/src/main/java/core_api/error/ApiExceptionHandler.java`: controlled multipart errors.
- `core-api/src/main/resources/application.properties`: multipart limits and private-data logging controls.
- `core-api/src/test/java/core_api/CoreApiApplicationTests.java`: new OpenAPI path assertions.
- `core-api/src/test/java/core_api/auth/SessionExpirationTests.java`: expired sessions on the bank API.
- `core-api/src/test/java/core_api/migration/{BudgetMigrationTests,MockImportMigrationTests,UserMigrationTests}.java`: new migration-count expectations.
- `frontend/personal-finance-ui/src/app/app.routes.ts`: authenticated lazy route.
- `frontend/personal-finance-ui/src/app/layouts/main-layout/main-layout.{ts,html}`: sidebar and title.
- `frontend/personal-finance-ui/src/app/core/models/transaction.model.ts`: CSV source union.
- `frontend/personal-finance-ui/src/app/features/transactions/transactions.html`: distinct CSV/demo labels.
- `qa/test_cookie_browser.py`: isolated bank workflow, theme and mobile browser coverage.

## Executed validation

Validated on 26 October 2026 with Java 25, Node 24 LTS, isolated H2 databases and fictional fixtures. No personal statement or development database was used.

| Check | Actual result |
| --- | --- |
| Spring `mvn -f core-api/pom.xml test package` | 368 tests passed, zero failures/errors/skips; executable JAR packaged |
| Angular `ng test --watch=false` | 229 tests passed across 18 files, including seven new bank review tests |
| Angular production `ng build` | Passed, browser and SSR bundles generated; existing dashboard SCSS budget warning (6.50 kB vs 4 kB) remains |
| Node 24 `node --test --test-isolation=none integration-service/tests/service.test.js` | 39 tests passed; process isolation disabled to avoid sandbox child-process restrictions |
| AI `ai-service/.venv/Scripts/python.exe -m pytest ai-service/tests -q` | 164 passed, one existing opt-in live MongoDB integration test skipped; nonfatal pytest-cache permission warning |
| Isolated Python backend/browser QA | Five tests passed, including the new bank browser workflow, in 73.16 seconds |
| Diff whitespace check | Passed with `git -c core.whitespace=cr-at-eol diff --check`, preserving existing CRLF files |

The 20 new Java tests cover CSV quoting/multiline fields, malformed/binary/unsupported-encoding data, file/row/field/column limits, signed/debit-credit amounts, PHP validation, decimal precision and the existing UI amount limit, explicit date order, invalid rows, ownership/category tampering, atomic rollback, cancellation/replacement/expiry cleanup, exact-file/reference deduplication, concurrent/idempotent confirmation, demo state and repeated sync, real multipart endpoints and missing/invalid CSRF. Existing session-expiration coverage now checks the bank endpoint as well. Dashboard expenses and budget spending are asserted after import.

The real headless browser uses the production Angular artifacts and isolated Spring/Node/FastAPI services with a fresh in-memory H2 database. It verifies cookie authentication, CSRF, existing finance and AI flows, demo connection/sync/disconnection, invalid-row exclusion, explicit category choices, confirmed CSV import, repeat duplicate preview, cancellation, Light/Dark themes and 390px mobile width with internal table scrolling. The existing fixture blocks public font CDNs; screenshots consequently show fallback fonts/icons and do not represent production font loading. Screenshots are generated under `core-api/target/qa-bank-screenshots/` (`light-desktop.png`, `dark-desktop.png`, `dark-mobile.png`); these are local ignored QA artifacts.

To rerun browser QA after packaging Spring and building Angular, set `JAVA_HOME` to Java 25, `RUN_BACKEND_E2E=1`, `RUN_BROWSER_E2E=1`, and `PYTHONPATH` to the installed Playwright location (here `.tools/browser-qa`), then run:

```text
ai-service/.venv/Scripts/python.exe -m pytest qa/test_backend_e2e.py qa/test_cookie_browser.py -q --basetemp=core-api/target/qa-bank-run -o cache_dir=core-api/target/pytest-cache
```

Initial sandboxed Angular/Node test attempts could not spawn child processes (`EPERM`); Angular checks were rerun with execution permission and Node ran without test process isolation. An initial Java compile missed a date exception import; that was fixed. Early backend regression runs identified the expected endpoint/migration-count assertions, which were updated. A stale compiled class required clearing only generated classes. Maven `clean` encountered a pre-existing locked `target/qa-e2e-final` directory; no development service was stopped or database modified. Final test/package execution succeeded without `clean`. These initial attempts are not counted as successful validations.

## Manual verification checklist

- [ ] Start Node with the configured dedicated integration key and Spring with the matching key; start Angular through its existing same-origin API proxy.
- [ ] Sign in as A. Open sidebar Bank Connections. Check fictional provider, masked account, explicit simulation labels, balance label, disconnected state and no credential inputs.
- [ ] Connect, refresh, sync, repeat sync, disconnect and reconnect. The first sync imports five rows, repeat skips five, last sync persists, disconnection retains transactions. B has independent state and rows.
- [ ] Upload a fictional statement with quoted commas/escaped quotes, signed amounts and a bank reference. Check detected headers and correct mappings; confirm the date order; inspect preview without saving.
- [ ] Repeat with separate debit/credit, DMY and MDY dates, invalid dates/descriptions/precision/currency, UTF-16/binary data, malformed quoting, >1 MiB, >1,000 rows and missing headers. Check controlled feedback and no unwanted writes.
- [ ] Review category suggestions; explicitly select matching categories; exclude unwanted rows. Cancel and verify no CSV transactions were created. Upload again and confirm a subset.
- [ ] Repeat exact-file import and reference-bearing overlapping exports; duplicates skip. Without references, manually exclude overlaps from changed exports.
- [ ] Double-click confirmation or resend the same preview ID; verify the same result and one set of transactions. Try expired/cancelled IDs and another owner's ID/category; verify rejection.
- [ ] Visit transactions, budgets and the matching dashboard month; verify totals and source labels. Check finance CRUD and AI conversation history still work.
- [ ] Remove CSRF or expire the session; verify rejection and existing sign-in handling. Check no statement content in logs/browser storage.
- [ ] Switch Light/Dark at desktop and 390px mobile widths. Check navigation drawer, mapping controls, category controls, keyboard focus and horizontally scrolling preview with no page overflow.

Suggested commit: `feat: add simulated bank connections and reviewed CSV statement imports`
