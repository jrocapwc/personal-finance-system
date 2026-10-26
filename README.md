# Personal Finance Management System

Milestone 8E.1 adds secure HttpOnly cookie authentication and browser session restoration.
See [the current authentication design, configuration and verification](docs/milestone-8e1-authentication.md).
The older milestone reports below describe their original behavior. Browser login now returns a safe profile,
and browser mutations require CSRF tokens.

Milestone 9 stabilizes the completed backend: Spring Boot + H2, FastAPI insights/chat,
MongoDB conversation history, and the local Node mock transaction provider.

See [the verification report, coverage matrix, troubleshooting, and repeatable Bruno E2E sequence](docs/milestone-9.md).
The Milestone 9 backend report excludes frontend, Docker, AWS, payment processing, and real banking connectivity.

## Prerequisites

- JDK 25 and the committed Maven wrapper (`core-api/mvnw.cmd`). Set `JAVA_HOME` to JDK 25;
  an older `java` on PATH can otherwise cause startup errors.
- Python 3.14 and `ai-service/.venv`, with `requirements-dev.txt` installed for tests.
- Node.js 24 LTS and npm. The project's `.nvmrc` and package engines specify 24.x.
- Local MongoDB at `mongodb://127.0.0.1:27017`, plus mongosh for verification.
- Bruno for optional manual requests. Automated tests do not require Bruno or an OpenAI key.

## Configuration and local startup order

Use **MongoDB -> FastAPI -> Node -> Spring Boot**. Keep each application in its own terminal.
All instructions below use Windows PowerShell from the repository root.

Select your JDK 25 installation before Maven or QA commands (adjust the path as needed):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

| Setting | Service | Purpose |
| --- | --- | --- |
| `JWT_SECRET_BASE64` | Spring | Base64 random signing key, at least 32 decoded bytes |
| `AI_INTERNAL_API_KEY` | Spring | Must equal FastAPI's `INTERNAL_API_KEY` |
| `INTERNAL_API_KEY` | FastAPI | Internal caller authentication, at least 32 UTF-8 bytes |
| `INTEGRATION_INTERNAL_API_KEY` | Spring and Node | Separate matching internal key, at least 32 UTF-8 bytes |
| `AI_SERVICE_BASE_URL` | Spring | Default `http://127.0.0.1:8000` |
| `INTEGRATION_SERVICE_BASE_URL` | Spring | Default `http://127.0.0.1:3001` |
| `MONGODB_URI`, `MONGODB_DATABASE` | FastAPI | Defaults: local MongoDB, `personal_finance_ai` |
| `OPENAI_API_KEY`, `OPENAI_MODEL` | FastAPI | Optional; FINANCE falls back to rules, GENERAL needs a working provider |
| `PORT` | Node | Default `3001`; binds only to loopback |

Root `.env.example` documents Spring settings. Spring does **not** automatically load `.env`.
FastAPI loads `ai-service/.env`; Node loads `integration-service/.env` through npm start/dev.
Process variables take precedence. Keep secrets in ignored local configuration, never in Bruno
request files, tracked environment files, URLs, logs, or commits.

Verify MongoDB without changing data:

```powershell
mongosh 'mongodb://127.0.0.1:27017' --quiet --eval 'db.runCommand({ ping: 1 })'
```

FastAPI terminal:

```powershell
Set-Location ai-service
# First setup only: python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
# If absent, copy .env.example to .env and configure INTERNAL_API_KEY locally.
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --no-access-log
```

Node terminal (Node 24 LTS):

```powershell
Set-Location integration-service
npm ci
# Configure ignored .env with INTEGRATION_INTERNAL_API_KEY and PORT=3001.
npm run dev
# npm start runs without file watching.
```

[Node setup](integration-service/README.md) includes secure key generation and the optional
ignored workspace-local Node 24 runtime when the system installation is another version.

Spring terminal: load only the required matching key assignments from the service files.
This helper reads values; it never executes `.env` content or prints secrets.

```powershell
function Get-LocalSetting([string]$File, [string]$Name) {
    $line = Get-Content -LiteralPath $File |
        Where-Object { $_ -match ('^' + [regex]::Escape($Name) + '=') } |
        Select-Object -First 1
    if (!$line) { throw "Missing $Name in $File" }
    $value = $line.Substring($Name.Length + 1).Trim()
    if ($value.Length -ge 2 -and (
        ($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    if ([Text.Encoding]::UTF8.GetByteCount($value) -lt 32) { throw "Configure a valid $Name" }
    return $value
}
$env:AI_INTERNAL_API_KEY = Get-LocalSetting 'ai-service/.env' 'INTERNAL_API_KEY'
$env:INTEGRATION_INTERNAL_API_KEY = Get-LocalSetting 'integration-service/.env' 'INTEGRATION_INTERNAL_API_KEY'
$env:AI_SERVICE_BASE_URL = 'http://127.0.0.1:8000'
$env:INTEGRATION_SERVICE_BASE_URL = 'http://127.0.0.1:3001'
$env:AI_SERVICE_READ_TIMEOUT = '20s'

# Preserve your existing signing key when available. A new key invalidates old JWTs.
if (!$env:JWT_SECRET_BASE64) {
    $jwtBytes = New-Object byte[] 32
    $jwtRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $jwtRandom.GetBytes($jwtBytes) } finally { $jwtRandom.Dispose() }
    $env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtBytes)
    [Array]::Clear($jwtBytes, 0, $jwtBytes.Length)
}
Set-Location core-api
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

These commands assume the documented local `.env` files use one assignment per line, without
interpolation or multiline values. If you configured keys only through a secret manager or
process environment, set the matching Spring variables from that same source instead.
Restart Spring after changing its environment; log in again after changing the signing key.

H2 remains `core-api/data/personal-finance` under the local profile. Flyway applies additive
V1?V5 migrations. Do not reset the database, edit applied migrations, or enable Flyway clean.

## Endpoint overview

Spring defaults to port 8080. Its application API uses `/api/v1`, HttpOnly browser cookies and header-only Bearer compatibility.
Use the explicit `local` profile for plain-HTTP Angular development; production cookies require HTTPS.
Public endpoints are registration, login, actuator health, and Swagger documentation.

| Module | Methods and paths |
| --- | --- |
| Authentication | `POST /auth/register`, `POST /auth/login`, `GET /users/me` |
| Categories | `GET/POST /categories`, `PUT/DELETE /categories/{id}` |
| Transactions | `GET/POST /transactions`, `GET/PUT/DELETE /transactions/{id}` |
| Budgets | `GET/POST /budgets`, `GET/PUT/DELETE /budgets/{id}` |
| Dashboard | `GET /dashboard?month=10&year=2026` |
| Insights | `GET /insights?month=10&year=2026` |
| Stateless chatbot | `POST /chat/messages` with GENERAL or FINANCE mode |
| Conversations | `GET/POST /chat/conversations`, `GET /chat/conversations/{id}`, `POST /chat/conversations/{id}/messages` |
| Mock imports | `POST /transactions/import/mock` with no body/query parameters |

FastAPI (8000): public `GET /health`; internal-key-protected `/api/v1/insights/analyze`,
`/api/v1/chat/respond`, and conversation routes. Node (3001): public `GET /health` and
internal-key-protected `GET /internal/mock-transactions`. Internal services never authenticate
end users or accept client ownership claims; Spring resolves the verified JWT owner.

Spring money uses BigDecimal; public financial responses encode numbers, while internal AI
summaries and Node normalized amounts use decimal strings. Inputs must have positive monetary
values with at most two decimal places. Month/year ranges are 1?12 / 2000?2100. Spring errors
use `ApiError`; FastAPI retains its sanitized `detail` envelope and Node its `code/message`
envelope. Spring sanitizes upstream failures instead of forwarding upstream payloads.

Swagger: `http://localhost:8080/swagger-ui.html`; FastAPI docs: `http://127.0.0.1:8000/docs`.

## Automated testing

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

After packaging Spring, opt in to the isolated real-HTTP workflow:

```powershell
$env:RUN_BACKEND_E2E = '1'
try { .\ai-service\.venv\Scripts\python.exe -m pytest qa -q -p no:cacheprovider }
finally { Remove-Item Env:RUN_BACKEND_E2E }
```

The E2E harness starts separate processes on temporary loopback ports, uses in-memory H2,
mock MongoDB, and a mocked OpenAI SDK. It does not reuse running development services or their
credentials. It stops only processes it created. The separate opt-in local MongoDB test
creates/removes only a random test database; see [the verification guide](docs/milestone-9.md).

In this restricted Windows sandbox, use Maven
`'-Daether.syncContext.named.factory=rwlock-local'` with one Maven process (`-o` if cached),
and `npm test -- --test-isolation=none` when child test workers report EPERM. The E2E test needs
permission to spawn processes and create temporary files. Normal PowerShell does not require
these sandbox workarounds.

## Manual verification and limitations

Open `core-api/bruno` and select Local. Requests are grouped into Authentication, Categories,
Transactions, Budgets, Dashboard, Financial Insights, General Chat, Finance Chat, Conversations,
Mock Transaction Imports, and Negative and Security Tests. Registration generates a fresh
fictional user and runtime credentials unless you explicitly supply local environment values.
Do not run every request blindly: mutations/deletes and security requests require their own
setup. Follow [the ordered manual E2E sequence](docs/milestone-9.md#repeatable-manual-e2e-sequence).

Known limitations: GENERAL requires a provider outside the mock harness; health endpoints are
liveness checks, not dependency-readiness checks; standalone MongoDB message writes use best
effort compensation, not multi-document transactions; concurrent sends may share earlier
context; imported records deleted by the user can be imported again. H2 and all services are
configured for local development. Passing these tests is not a production deployment approval.

Additional service references: [FastAPI](ai-service/README.md),
[conversation persistence](docs/milestone-7d.md), and [mock imports](integration-service/README.md).
