# Milestone 8 — Mock Transaction Integration Service

This local Node.js 24 LTS / Express 5 service simulates an external transaction provider.
It uses JavaScript ES Modules and Node's built-in test runner. Five deterministic, fictional
PHP transactions are normalized and returned to Spring Boot. There is no bank, payment,
database, or public user-authentication connection in this service.

Spring retains JWT verification, ownership, category rules, and all H2 writes. FastAPI,
MongoDB, insights, and chatbot behavior are unchanged.

## Architecture and key components

```mermaid
sequenceDiagram
    participant Client
    participant Spring
    participant Node
    participant H2
    Client->>Spring: POST /api/v1/transactions/import/mock + JWT
    Spring->>Spring: Verify JWT; resolve owner
    Spring->>Node: GET /internal/mock-transactions + dedicated internal key
    Node->>Node: Normalize fictional PHP fixtures
    Node-->>Spring: Bounded normalized records
    loop Each record in an independent transaction
        Spring->>H2: Lock owner; check duplicate; map owned category; save
    end
    Spring-->>Client: importedCount / skippedCount / failedCount / totalProcessed
```

- `src/app.js`: injectable Express application, public health route, protected internal
  route, and sanitized JSON errors. Importing it does not open a listening socket.
- `src/server.js`: validates environment configuration, binds only to `127.0.0.1`, and
  closes the server on shutdown. `src/config.js` validates the key and port.
- `src/middleware/internal-auth.js`: compares SHA-256 digests with `timingSafeEqual`.
  Missing/invalid internal keys return 401. JWTs and user IDs are never accepted as ownership proof.
- `src/data/mock-transactions.js`: immutable fictional source fixtures. No randomness or
  current-date calculation changes the import dataset between calls.
- `src/services/normalizer.js`: validates the provider contract, supports PHP only, maps
  `credit` → `INCOME`, `debit` → `EXPENSE`, and emits `source: MOCK_PROVIDER`.
  Amounts stay strings, padded to two decimal places. A BigInt positivity check avoids
  JavaScript floating-point financial arithmetic. Dates must be real ISO calendar dates.
- Spring `MockTransactionClient`: dedicated HTTP client with an independent internal key,
  bounded response size (256 KiB / 100 records), explicit timeouts, JSON-envelope validation,
  and redirects disabled. It forwards no JWT, cookie, user ID, or FastAPI credential.
- Spring `MockTransaction`: strict per-record boundary validation using `BigDecimal` and
  `LocalDate`. Extra fields, numeric amounts, invalid dates, invalid sources, zero/negative
  amounts, and values exceeding DECIMAL(19,2) are rejected.
- Spring `MockImportService`: fetches the dataset before opening database transactions,
  invokes existing one-time category initialization, and accounts for each record's outcome.
- Spring `TransactionService.importMock`: uses an independent transaction for each record,
  the existing per-owner database lock, and existing owned-category/type checks. It keeps
  duplicate detection and insertion under the same owner lock, including concurrent requests.
- Flyway `V5__mock_transaction_imports.sql`: adds immutable external IDs and expands the
  source column. The unique constraint `(user_id, source, external_transaction_id)` backs
  application duplicate checks. Manual rows retain a null external ID; multiple manual
  rows remain valid. Existing owner/category/type foreign keys and amount checks remain.

## Contracts and fixtures

Source fields are `externalTransactionId`, `amount`, `currency`, `direction`, `description`,
`transactionDate`, and `categoryHint`. Currency is validated before normalization and is not
included in the standardized contract. This project has no currency column or conversion
flow: fixtures represent PHP only, and other currencies are rejected.

```json
{
  "transactions": [
    {
      "externalTransactionId": "mock-2026-10-salary",
      "type": "INCOME",
      "amount": "35000.00",
      "description": "Fictional monthly salary",
      "transactionDate": "2026-10-01",
      "categoryHint": "SALARY",
      "source": "MOCK_PROVIDER"
    }
  ]
}
```

The actual response has five records:

| Hint | Type | Amount (PHP) | Date | Owned category |
| --- | --- | --- | --- | --- |
| SALARY | INCOME | 35000.00 | 2026-10-01 | Salary |
| FREELANCE | INCOME | 2500.50 | 2026-10-05 | Freelance |
| FOOD | EXPENSE | 425.75 | 2026-10-06 | Food |
| TRANSPORTATION | EXPENSE | 120.00 | 2026-10-07 | Transportation |
| HOUSING | EXPENSE | 8000.00 | 2026-10-08 | Housing |

Hints use the fixed mapping above, with existing normalized category names and the expected
type. Existing category initialization creates defaults once for new users. Deleted/renamed
defaults are not recreated by imports. An unknown hint, missing owned category, or wrong type
counts as a failed record; another user's category is never used as a substitute.

Amounts must be positive decimal strings with at most 17 integer digits and two fractional
digits; normalization pads missing fractional digits. Descriptions are nonempty and limited
to 255 characters; external IDs use 1–100 ASCII letters, digits, underscores, or hyphens.
Node rejects malformed provider fixtures as a whole. Spring independently validates records
and can count invalid rows in an otherwise valid upstream response without saving those rows.

## Environment variables

| Variable | Used by | Default / requirement |
| --- | --- | --- |
| `INTEGRATION_INTERNAL_API_KEY` | Node and Spring | Required for imports; same secret in both, at least 32 UTF-8 bytes, no line breaks |
| `PORT` | Node | `3001`, integer 1–65535; host is fixed to `127.0.0.1` |
| `INTEGRATION_SERVICE_BASE_URL` | Spring | `http://127.0.0.1:3001`, HTTP(S) origin without credentials/path/query/fragment |
| `INTEGRATION_CONNECT_TIMEOUT` | Spring | `2s` |
| `INTEGRATION_READ_TIMEOUT` | Spring | `3s` |
| `JWT_SECRET_BASE64` | Spring | Existing JWT signing configuration; see root README |

Both Spring timeouts must be 1 ms–60 s. Node loads ignored `integration-service/.env` via
`npm start` or `npm run dev`; existing process variables take precedence. Spring does not automatically read
`.env`. No secrets are embedded in fixtures, source, lockfiles, examples, or Bruno requests.
The integration key is separate from `AI_INTERNAL_API_KEY` / FastAPI's `INTERNAL_API_KEY`.

## Windows PowerShell startup

Use Node.js **24 LTS** ([release policy](https://github.com/nodejs/Release)), Java 25,
and the existing local H2 configuration. Check `node --version` before running commands.
If this workspace's system Node is v25, the implementation was tested with a separate,
ignored Node v24.22.1 runtime; it did not replace the system installation. You can use it
in the current shell from the repository root:

```powershell
# Optional workspace-local LTS setup when the system Node is not 24.x:
npm install --prefix .tools/node-lts --no-save --package-lock=false --no-audit --no-fund node@24
$env:Path = "$((Resolve-Path .tools/node-lts/node_modules/node/bin).Path);$env:Path"
node --version
```

In the Node terminal, from the repository root, create local configuration only if absent:

```powershell
if (!(Test-Path integration-service/.env)) {
    $integrationBytes = New-Object byte[] 32
    $integrationRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $integrationRandom.GetBytes($integrationBytes) } finally { $integrationRandom.Dispose() }
    $integrationLocalKey = [Convert]::ToBase64String($integrationBytes)
    [IO.File]::WriteAllLines(
        (Join-Path (Get-Location) 'integration-service/.env'),
        @("INTEGRATION_INTERNAL_API_KEY=$integrationLocalKey", 'PORT=3001')
    )
    [Array]::Clear($integrationBytes, 0, $integrationBytes.Length)
}
Set-Location integration-service
npm ci
npm start
```

This generates a random credential without displaying it and preserves an existing `.env`.
The server listens at `http://127.0.0.1:3001`. Stop it with Ctrl+C.
Use `npm run dev` instead of `npm start` to restart automatically when source files change.

In the Spring terminal, from the repository root:

```powershell
# Reads only the generated, unquoted key assignment; does not execute .env content.
$integrationAssignment = Get-Content integration-service/.env |
    Where-Object { $_ -match '^INTEGRATION_INTERNAL_API_KEY=' } |
    Select-Object -First 1
$env:INTEGRATION_INTERNAL_API_KEY = $integrationAssignment.Substring('INTEGRATION_INTERNAL_API_KEY='.Length)
$env:INTEGRATION_SERVICE_BASE_URL = 'http://127.0.0.1:3001'
$env:INTEGRATION_CONNECT_TIMEOUT = '2s'
$env:INTEGRATION_READ_TIMEOUT = '3s'
# Set the existing JWT_SECRET_BASE64 and any existing FastAPI settings as documented in root README.
Set-Location core-api
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

On Spring startup Flyway applies V5 to the existing local H2 database automatically.
Do not delete the database, edit V1–V4, enable Flyway clean, or reset migration history.
Migration tests verify the upgrade using a separate temporary H2 file, preserving manual
records. This implementation's tests do not open or migrate the development H2 file.

## Public import API and outcomes

`POST http://localhost:8080/api/v1/transactions/import/mock` requires the existing JWT.
It accepts **no body or query parameters**, including `userId`, categories, source, or
provider URL overrides. Spring derives the owner exclusively from verified authentication.
The internal Node GET endpoint returns the same fictional dataset to the authenticated
service caller; Node has no per-user database or ownership decisions.

For a fresh account with default categories:

```json
{"importedCount":5,"skippedCount":0,"failedCount":0,"totalProcessed":5}
```

Repeating the import:

```json
{"importedCount":0,"skippedCount":5,"failedCount":0,"totalProcessed":5}
```

Counters always sum to totalProcessed. Each successful row commits independently; known
invalid/category failures and rolled-back row database failures increase failedCount.
HTTP 200 can therefore contain failed records: clients must inspect the counters. Existing
external IDs are skipped, without overwriting saved amounts/descriptions/categories. Manual
transactions remain MANUAL and are untouched. The existing transaction response contract
is preserved; external IDs are stored internally, while `source` identifies imported records.

Provider failures before processing return errors with no transaction writes. A timeout
between a client and Spring can occur after some commits; retrying safely skips committed
provider IDs. Deleting an imported record removes its uniqueness reservation, so a later
import can recreate it. Editing an imported record through existing CRUD retains its source
and external ID and causes subsequent imports to skip it.

## Errors

| Situation | HTTP | Code / behavior |
| --- | --- | --- |
| Missing/invalid JWT on Spring | 401 | Existing authentication error envelope |
| Body/query ownership overrides | 400 | `INVALID_REQUEST` |
| Node unavailable or Spring integration key absent | 503 | `INTEGRATION_UNAVAILABLE` |
| Node connect/read timeout | 504 | `INTEGRATION_TIMEOUT` |
| Node rejects internal authentication | 502 | `INTEGRATION_AUTH_FAILED`; refreshing a user JWT will not fix this |
| Invalid envelope/content type/oversized response | 502 | `INTEGRATION_INVALID_RESPONSE` |
| Invalid row/unknown hint/missing or mismatched owned category | 200 | Increase failedCount; row not saved |
| Existing owner/source/external ID | 200 | Increase skippedCount |
| Accessing another user's saved transaction | 404 | Existing owner-scoped transaction behavior |
| Missing/invalid internal key on Node | 401 | JSON `UNAUTHORIZED` |
| Invalid fixture on Node | 502 | JSON `INVALID_PROVIDER_RECORD` |

Spring uses the existing `ApiError` envelope. Upstream exception bodies, secret keys,
transaction payloads, and stack traces are never copied into controlled error responses.
Node does not log requests or provider exception text. Its health endpoint is public and
returns only `{"status":"healthy"}`; unknown paths return sanitized JSON 404 errors.

## Bruno verification

Open `integration-service/bruno` as a collection. Select Local and set `internalApiKey`
as a local secret variable matching Node's key. Do not put secrets in tracked environment files.

1. Run **01 Health** → 200.
2. Run **02 Mock Transactions** → 200 with five normalized records.
3. Run **03 Missing Key** and **04 Invalid Key** → 401.

Open the existing `core-api/bruno` collection, select Local, and use register/login to set
`accessToken` for a new account. The Spring integration environment must contain the same
internal key as Node; Bruno sends only the user JWT to Spring.

1. **48 Import Mock Transactions** → five imported for a fresh account.
2. **49 Repeat Mock Import** → zero imported and five skipped.
3. **14 List Transactions** → verify `source: MOCK_PROVIDER`. October 2026 dashboard,
   budgets, and insights include these records naturally through existing business logic.
4. **50 Mock Import Missing JWT** → 401; **51 Mock Import Owner Override** → 400.
5. Register/login as User B: listing excludes A's records. B's first import succeeds
   independently using the same external IDs. B cannot read/update/delete A's records.
6. Stop Node and repeat import → 503. Restart Node with a different internal key → 502
   `INTEGRATION_AUTH_FAILED`. Restore matching configuration before continuing.

Bruno requests are supplied for manual verification; automated Java tests mock Node responses
and independently exercise the import, isolation, mapping, errors, and concurrency paths.

## Tests and compilation

From the repository root in a shell using Node 24 LTS:

```powershell
Push-Location integration-service
npm ci
npm run check
npm test
# Restricted Windows sandbox fallback if test-worker spawning reports EPERM:
npm test -- --test-isolation=none
Pop-Location
Push-Location core-api
.\mvnw.cmd verify
Pop-Location
Push-Location ai-service
.\.venv\Scripts\python.exe -m pytest -q -p no:cacheprovider
Pop-Location
```

Node tests use ephemeral loopback ports and injected test keys, not developer credentials.
Java tests use mocked Node responses, ephemeral JWT keys, and isolated H2 databases. The
concurrency test verifies that two simultaneous imports save each owner's external ID once.
Python tests remain unchanged; the existing opt-in MongoDB test is skipped unless requested
with `RUN_MONGODB_INTEGRATION=1`. This milestone does not require starting MongoDB for tests.

For the current restricted Maven cache permissions, use
`'-Daether.syncContext.named.factory=rwlock-local'` with one Maven process, and `-o` only
if dependencies are cached. These flags affect the build environment, not application behavior.

Validation during implementation: **39 Node tests passed on v24.22.1**, **335 Maven tests
passed**, packaging verification succeeded, and **164 Pytest tests passed with 1 existing
opt-in MongoDB test skipped**. No real OpenAI or banking calls were made.

Suggested commit: `feat(integration): import fictional mock transactions through a local Node service`
