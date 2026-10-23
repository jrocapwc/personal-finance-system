# Milestone 9 — Backend integration testing and stabilization

Verification date: 2026-10-23 (Asia/Manila). This milestone adds regression coverage and
repairs verified collection/logging defects. Financial business rules, authentication,
database schemas, fixture amounts, and persistence behavior are preserved.

## Inspection findings and fixes

| Finding | Resolution |
| --- | --- |
| Module tests lacked one complete real-HTTP backend workflow | Added `qa/test_backend_e2e.py`: temporary Spring, FastAPI, and Node processes with isolated in-memory H2, fake OpenAI SDK, and mongomock |
| Spring web DEBUG output exposed financial request/response data | Reproduced with a failing `SensitiveLoggingIntegrationTests` test; restricted `org.springframework.web` to INFO and verified the regression passes even with parent Spring DEBUG |
| Bruno contained literal credentials and request-level environment overrides | Removed overrides; runtime fictional registration credentials and collection environments now drive requests |
| Bruno hardcoded category IDs from one account | Replaced them with category IDs discovered for the authenticated user |
| Create Category sent server-managed timestamps and could not succeed | Removed unsupported fields; a collection contract test guards against recurrence |
| Two negative chat requests had malformed JSON | Restored valid JSON objects so they exercise authentication/validation rather than accidental JSON syntax failure |
| Collections were flat and lacked explicit finance conversation / cross-user requests | Organized 11 folders and added requests 52–59 using existing endpoints |
| Documentation was split across stale milestone instructions | Consolidated root README, environment examples, startup order, E2E commands, and troubleshooting |

Tracked request credential literals were removed without copying them into this report. If
those values were used outside fictional local testing, rotate them through your usual secret
management process; changing files does not remove values from previous Git history.

## Test strategy and data safety

The default Maven/Pytest/npm suites cover business rules and mocked failure paths. The QA
suite adds collection contract checks and an opt-in full HTTP workflow. No automated test
calls paid OpenAI APIs. The test-only FastAPI factory substitutes the OpenAI SDK constructor,
installs a live-SDK tripwire, and substitutes MongoDB before application startup. Production
entrypoints never load the QA factory.

The E2E harness:

- Honors JAVA_HOME and requires Node 24 LTS; `QA_NODE_EXECUTABLE` can select an installed
  Node binary. The ignored workspace runtime is used when present on Windows.
- Runs the packaged Spring JAR, real FastAPI routers/adapter, and real Node server on
  temporary loopback ports. Existing development processes and ports are not reused.
- Uses a random in-memory H2 database, random JWT/internal keys, fictional users at
  `example.test`, and fictional records. Developer `.env` files are never loaded.
- Exercises the real Spring HTTP clients, ownership resolution, financial aggregation,
  and Python/Java/Node serialization contracts. Only external OpenAI and MongoDB are mocked.
- Stops only subprocess handles it created; never kills services by executable name or
  resets a development database. Temporary logs contain only isolated QA activity.

The separate local MongoDB opt-in test uses a fresh `personal_finance_ai_test_<UUID>`
namespace and removes only that namespace. It never clears `personal_finance_ai`. Existing
migration tests use separate temporary H2 files and preserve records through V1–V5 upgrades.

## Coverage matrix

| Area | Automated evidence |
| --- | --- |
| Registration/login/invalid credentials | Existing auth suite and real-HTTP QA workflow |
| Missing, invalid, expired JWT; signature/issuer/audience | Existing auth suite; QA workflow also signs an expired token using only its ephemeral test key |
| Category ownership/type and one-time defaults | Category, transaction, budget suites; QA foreign-category rejection |
| Transaction/budget cross-user access | Existing CRUD suites; QA foreign transaction/budget reads |
| Dashboard isolation and dates | Existing dashboard suite; QA second-user isolation, Sep 30 / Oct 31 / Nov 1 boundaries |
| Decimal precision, savings, savings rate | FinancialSummary/budget/insight suites; QA exact cross-service totals and excessive precision rejection |
| SAFE/WARNING/EXCEEDED and edits/deletes | Budget suites; QA limit changes, expense edits/deletes, and recomputation |
| Correct user's financial summary and insights | Existing service/client suites; QA Spring → FastAPI HTTP and second-user zero summary |
| GENERAL/FINANCE and OpenAI abstraction | Existing SDK/provider suites; QA actual provider adapter with fake SDK responses |
| OpenAI failures/fallback/missing data | Existing timeout/API-error suites; QA FINANCE rule fallback, GENERAL 503, invalid/missing financial context |
| Conversation persistence/context/isolation | Python repository/API suites, Java client/security suites, QA multi-turn HTTP and owner 404s |
| MongoDB connection/lifecycle/unavailable/partial writes | Python repository/API mocks; separate actual local MongoDB persistence/index test |
| Node health/auth/normalization/currency/invalid records | Node's built-in runner; QA actual Node HTTP and internal key rejection |
| Duplicate imports and ownership | Existing concurrent import/database constraint tests; QA real Node imports by two owners |
| Imported records affect dashboard/budgets | QA exact totals after five imports and unchanged totals on repeat |
| Current FINANCE conversation after imports | QA sends before/after imports and verifies fresh amounts, not historical answers |
| FastAPI/Node unavailable | Existing mocked transport tests; QA stops its own service processes and verifies controlled errors |
| Request timeouts | Existing Java HTTP-client tests and Python SDK/orchestrator timeout tests |
| Malformed JSON, invalid IDs, missing resources | Existing controller/client suites; QA invalid/missing conversation IDs |
| Sensitive logging | Dedicated Spring regression with parent DEBUG enabled |
| Bruno organization, credentials, body contracts | 16 QA collection checks; Node VM compiles all embedded scripts without executing them |

## Running automated verification

From the repository root, using JDK 25, Python 3.14, and Node 24 LTS:

```powershell
Push-Location core-api
.\mvnw.cmd verify
Pop-Location
Push-Location ai-service
.\.venv\Scripts\python.exe -m pytest -q -p no:cacheprovider
Pop-Location
Push-Location integration-service
npm test
Pop-Location
.\ai-service\.venv\Scripts\python.exe -m pytest qa -q -p no:cacheprovider
node qa/check-bruno.mjs
```

Default QA skips the process-based E2E test. To run it after packaging Spring:

```powershell
$env:RUN_BACKEND_E2E = '1'
try { .\ai-service\.venv\Scripts\python.exe -m pytest qa -q -p no:cacheprovider }
finally { Remove-Item Env:RUN_BACKEND_E2E }
```

To include the actual local MongoDB test:

```powershell
Push-Location ai-service
$env:RUN_MONGODB_INTEGRATION = '1'
try { .\.venv\Scripts\python.exe -m pytest -q -p no:cacheprovider }
finally { Remove-Item Env:RUN_MONGODB_INTEGRATION }
Pop-Location
```

Restricted Windows sandbox workarounds used during verification:

- Maven: `'-Daether.syncContext.named.factory=rwlock-local' -o` with cached dependencies
  and one Maven process. The wrapper's Git test helper may print a sandbox signal-pipe
  warning even when Maven exits successfully; assess the Maven exit code and test reports.
- Node: `npm test -- --test-isolation=none` when test-worker spawning reports EPERM.
- QA E2E: allow temporary-file creation and subprocess execution. A unique `--basetemp`
  under `core-api/target` can locate logs predictably. Pytest may clean its chosen basetemp,
  so never point that option at a development data directory or a directory of valuable files.
- JAVA_HOME must select JDK 25; the system PATH on this workstation also contains JDK 8.

## Repeatable manual E2E sequence

Start services using the root README. Open `core-api/bruno`, select Local, and leave the
default October 2026 period when comparing Node's fixed October fixtures. Each request uses
`{{baseUrl}}`; JWTs, passwords, and internal keys are local/runtime variables, not committed.

Use a **new fictional account for each run**. Registration generates one unless you explicitly
provide email/password as local secret environment variables. An intentionally reused email
will correctly receive 409. Register does not delete records; every run remains isolated by
its new owner. Run requests individually in this order, not the entire collection blindly.

1. **Authentication / 01 Health → 02 Register → 03 Login → 04 Me**.
   Registration returns 201; login returns a bearer token saved as runtime `accessToken`.
   The runtime `email`, `password`, and `invalidPassword` are generated for the fictional user.
2. **Categories / 07 List Categories → 08 Income Categories**. The list request discovers
   this owner's Salary/Food category IDs for later requests. Optionally run **09 Create
   Category** to verify custom-category creation; it now sends only name/type/color.
3. **Transactions / 12 Create Income Transaction → 13 Create Expense Transaction →
   14 List Transactions → 16 Get Transaction**. These create fictional income 50000.00 and
   expense 16176.00 and save the transaction IDs as runtime variables.
4. **Budgets / 20 Create Budget → 21 List Budgets → 22 Get Budget**. Food's limit is
   20000.00 at 80%, so spending 16176.00 gives WARNING and 80.88% utilization.
5. **Dashboard / 25 Financial Dashboard**. Before other mutations, expect income 50000.00,
   expenses 16176.00, savings 33824.00, and savings rate 67.65. Check category totals and budgets.
6. **Financial Insights / 28 Monthly Insights** then **Finance Chat / 43 Explicit Finance
   Chat**. FINANCE can use the deterministic fallback without an OpenAI key. Its disclaimer
   must remain present. Empty-month insight/dashboard requests verify zero-data behavior.
7. **General Chat / 38 General Knowledge**. A configured provider is required for a manual
   200 response; without it, expect the controlled 503. Automated QA uses a fake SDK and
   verifies GENERAL without paid requests. Manually using your live provider can incur its
   normal API usage; it is not part of automated acceptance.
8. **Conversations / 44 Create Conversation → 47 Send Conversation Message → 46 Read
   Conversation → 45 List Conversations**. Repeat send with a follow-up to verify context
   and four saved user/assistant rows. General sends require the provider as above.
9. **Conversations / 52 Create Finance Conversation → 53 Send Finance Conversation
   Message → 54 Read Finance Conversation**. These use a separate runtime
   `financeConversationId`; GENERAL and FINANCE histories do not collide.
10. **Mock Transaction Imports / 48 Import Mock Transactions → 49 Repeat Mock Import**.
    Expect 5 imported initially and 5 skipped on repeat, with zero failures for default
    categories. Node's internal key belongs in Spring's environment, not the Bruno public request.
11. Re-run **25 Dashboard**. With the unchanged two manual records above, expect income
    87500.50, expenses 24721.75, savings 62778.75, and savings rate 71.75. Food spending is
    16601.75 and its budget remains WARNING (83.01%). Re-run **53 Send Finance Conversation
    Message**: it must use the new current summary. Repeating imports must not change totals.
12. Exercise edits/deletes after recording the baseline: **17 Update Transaction**, then
    dashboard/budget reads; **19 Delete Transaction**, then reads again. Spending must fall
    immediately. Category deletion/retyping remains prohibited while referenced. Budget
    update/delete requests are available for their own CRUD verification.

Internal service checks use separate collections: `ai-service/bruno` at port 8000 and
`integration-service/bruno` at port 3001. Set local secret `internalApiKey` separately in each
collection. Run their health, valid-key, missing-key, and invalid-key requests. Never forward
a user JWT to these internal services.

### Security and negative checks

- Under Negative and Security Tests, run missing/invalid JWT, invalid body/mode/period,
  missing JWT for imports, and owner-override requests with the expected 400/401 responses.
- **55 Invalid Login** uses the generated wrong password and expects 401.
- **56 Expired JWT** requires a genuinely expired fictional user's token set only as local
  secret `expiredToken`; malformed tokens also yield 401 but do not prove expiry handling.
  Automated JWT tests independently generate expired, correctly signed test tokens.
- For cross-user checks, save User A's IDs as local variables `foreignTransactionId`,
  `foreignBudgetId`, and `foreignConversationId`. Run Register/Login again for a new User B,
  leaving those foreign-ID variables intact. **57–59** expect 404. B's lists/dashboard must
  exclude A's records. B may import the same provider IDs independently under B's ownership.
- Stop/restart only services you launched for manual verification: FastAPI outages return
  controlled 502, Node outages 503, and MongoDB outages conversation storage 503. Restore
  service availability and configuration afterward. Automated tests cover these safely with
  mocked failures or only their own isolated process handles.

Bruno scripts and JSON contracts were validated statically. The Bruno GUI/CLI manual sequence
was not executed as part of this automated run; live-provider behavior is intentionally excluded.

## Troubleshooting and known limitations

| Symptom | Check |
| --- | --- |
| Import returns INTEGRATION_UNAVAILABLE / 503 | Node health, URL/port, and Spring's key presence; set keys before starting Spring |
| Import returns INTEGRATION_AUTH_FAILED / 502 | Match Spring and Node integration keys; refreshing a user JWT does not repair service authentication |
| Insights/chat returns a gateway failure | Match AI_INTERNAL_API_KEY to FastAPI INTERNAL_API_KEY; verify port 8000 and the configured URL |
| GENERAL returns CHAT_UNAVAILABLE / 503 | Optional provider is absent/failing; FINANCE retains rules fallback |
| Conversation storage returns 503 | Check MongoDB ping, URI/database settings, and availability; persistence is unconfirmed |
| JWT returns 401 after restarting Spring | Reuse the signing key or log in again; tokens expire after 60 minutes |
| Bruno category/resource is missing | Use a fresh fictional account and run category discovery before dependent requests |
| Import failedCount is nonzero with HTTP 200 | Inspect known category mapping/type/default changes; rows commit independently and counters must be checked |
| Startup reports unsupported class version | Select Java 25 through JAVA_HOME and rebuild the JAR |

Health endpoints are liveness checks and can remain healthy while dependencies are unavailable.
MongoDB standalone writes cannot atomically commit an entire message pair/activity update;
compensation is best effort, failures may leave ambiguous writes, and conversation sends are
not idempotent. Concurrent sends can share earlier context. Imported transaction IDs remain
deduplicated while their records exist; deleting one permits later reimport. Full-HTTP QA
uses mocked MongoDB/OpenAI; actual local MongoDB has a separate opt-in integration test.

The financial calculations and contracts passed the tested local workflows. This is a local
backend readiness assessment, not a load/security penetration test, production deployment
approval, or live OpenAI service certification. No Angular, Docker, AWS, or new business
features were implemented.

Recommended commit: `test(backend): stabilize end-to-end workflows and sanitize API collections`
