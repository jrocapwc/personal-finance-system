# Financial Insights and Chat Service

Python 3.14, FastAPI, Pydantic, Uvicorn, Pytest and HTTPX. This service applies
explainable, deterministic rules to a supplied monthly financial summary. It has
no database or JWT validation. Spring supplies aggregates; chat optionally uses OpenAI.
Deterministic insights never call external services. Neither endpoint stores chat history.

## Windows PowerShell setup

From the repository root:

```powershell
Set-Location ai-service
python --version                          # Must select Python 3.14
python -m venv .venv                      # Only needed when creating the environment
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
```

The Python launcher `py` is optional; these instructions use `python`, which is
available in the current development environment. If script execution is blocked,
you can use `.\.venv\Scripts\python.exe` in place of `python` for every command,
without activating the environment or changing PowerShell's execution policy.

Set the credential in the **same PowerShell window** that launches Uvicorn:

```powershell
if (!$env:INTERNAL_API_KEY) {
    $env:INTERNAL_API_KEY = python -c "import secrets; print(secrets.token_urlsafe(32))"
}
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --no-access-log
```

The assignment captures a random secret without printing it. `INTERNAL_API_KEY`
has no default, must contain at least 32 UTF-8 bytes, and should be generated from
at least 32 random bytes as above. Startup fails if it is absent or too short.
Environment variables belong to this shell session; a newly opened shell must
configure the key again. Reuse a securely stored key when clients need continuity.
`.env.example` documents the name; `.env` files are **not automatically loaded**.
Do not commit secrets or paste keys into request URLs. Stop with Ctrl+C.

The server binds only to loopback. `--no-access-log` also keeps request URLs out
of Uvicorn access logs. Application code never logs bodies or API keys. Public
health and documentation reveal no financial data. Keep this standalone service
local; broader network deployment and Spring Boot integration are future work.

- Health: http://127.0.0.1:8000/health
- Interactive OpenAPI: http://127.0.0.1:8000/docs
- OpenAPI JSON: http://127.0.0.1:8000/openapi.json
- Alternative docs: http://127.0.0.1:8000/redoc

`GET /health` returns 200 and `{"status":"healthy"}` once startup succeeds.
`POST /api/v1/insights/analyze` requires `X-Internal-API-Key`. Missing or invalid
keys return 401 with `{"detail":"Missing or invalid internal API key"}`. Keys
are compared with `hmac.compare_digest`. No bearer token should be sent here.

## Input contract

All eight top-level fields are required. Names are camelCase. See the complete
[example request](examples/analyze-request.json) and [example response](examples/analyze-response.json).

| Field | Validation |
| --- | --- |
| `month`, `year` | JSON integers, month 1..12, year 2000..2100; booleans/strings rejected |
| `totalIncome`, `totalExpenses` | Nonnegative decimal strings, at most 24 digits and 2 decimal places |
| `netSavings` | Signed decimal string; must equal income minus expenses |
| `savingsRate` | Decimal string equal to savings / income * 100, rounded to 2 places HALF_UP; required `null` for zero income |
| `expensesByCategory` | Required list, possibly empty, max 1000; each entry has positive integer `categoryId`, nonblank `categoryName` (max 100), nonnegative decimal-string `amount` |
| `budgetUtilization` | Required list, possibly empty, max 1000; each entry has positive integer `categoryId`, positive decimal-string `monthlyLimit`, nonnegative decimal-string `spentAmount`, integer `alertThreshold` 1..99 |

Financial values deliberately use **JSON strings**, such as `"1000.00"`, so a JSON
parser cannot first round them through a binary floating-point value. Calculations
use `Decimal` with a local precision of 64. Numeric JSON money values, NaN,
infinities, exponents, and more than two fractional digits are rejected. Savings
rate supports up to 28 digits to accommodate large negative ratios.

Category IDs must be unique within each list. Partial category breakdowns and
empty lists are allowed, but category amounts cannot sum above total expenses;
neither can budget spending. Budgets represent distinct expense categories for
this one month. Budget fields are the underlying amounts and threshold: the
service derives warning/exceeded conditions rather than trusting a client status.
Do not send income categories as expense breakdowns.

Unknown fields are rejected at every schema level, including `userId`, email,
password, tokens, raw transactions, and unrelated dashboard fields. Validation
failures return 422 with `detail` entries containing only `loc`, `msg`, and `type`;
rejected values and bodies are omitted. Malformed JSON also returns 422. The
service validates internal consistency, but cannot verify the truth, ownership,
currency, or date range of supplied aggregates; the trusted caller must do that.
The current Spring dashboard must be explicitly mapped into this contract in a
future milestone; do not forward its entire response unchanged.

Example body:

```json
{
  "month": 10,
  "year": 2026,
  "totalIncome": "1000.00",
  "totalExpenses": "850.00",
  "netSavings": "150.00",
  "savingsRate": "15.00",
  "expensesByCategory": [
    {"categoryId":1,"categoryName":"Food","amount":"400.00"},
    {"categoryId":2,"categoryName":"Housing","amount":"450.00"}
  ],
  "budgetUtilization": [
    {"categoryId":1,"monthlyLimit":"500.00","spentAmount":"400.00","alertThreshold":80}
  ]
}
```

The response always contains `summary` (text), `positiveObservations`, `concerns`,
`recommendations`, `suggestedActions` (arrays of text), and `disclaimer`. For this
sample it identifies Housing as the largest expense, savings of 150.00, a low
15.00% savings rate, and an 80.00% budget warning for category 1. The complete
response is checked against the engine by the tests.

## Rules and deterministic ordering

- Summarize month, income, expenses, and net savings using two decimal places.
- Positive savings produce a positive observation. Negative savings flag the
  shortfall and suggest reviewing essential costs and nonessential subscriptions.
- With positive income, a savings rate below 0 is flagged as negative; 0 through
  less than 20% is low; 20% or above produces a positive observation. The rule uses
  the validated, rounded savings rate. This threshold is a heuristic, not a
  universal personal savings target.
- Zero income produces no division and explains why the rate is unavailable.
  Zero income and expenses also prompt recording activity rather than claiming
  that spending was healthy.
- Select the highest positive expense by amount descending, then category ID
  ascending for ties. Suggest reviewing discretionary costs and exploring a 10%
  reduction, rounded to 2 decimals HALF_UP. Zero-only lists have no top category.
- A budget is EXCEEDED when spending is at least its limit, including exactly
  100%; otherwise it is WARNING when `spentAmount * 100 >= monthlyLimit *
  alertThreshold`. Classification uses exact amounts, so display rounding cannot
  trigger a warning early. Warnings suggest tracking the remaining allowance;
  exceeded budgets suggest reviewing further commitments. Budgets are processed
  in category ID order.
- Empty budgets produce no budget concerns; absent expense breakdowns with
  nonzero expenses prompt providing categories. Output is stable for equivalent
  input, including reordered category and budget lists. There is no randomness,
  clock-dependent text, prediction, or investment-product recommendation.

The disclaimer states that the insights are educational and are not professional
financial, investment, tax, or legal advice. All amounts assume a single currency;
no currency symbol is inferred. Historical months use the same rules. Large
category collections are bounded, but this milestone has no rate limiter, audit
store, external data validation, or production deployment configuration.

## PowerShell and Bruno verification

In another PowerShell window, health needs no secret:

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
```

To analyze using PowerShell, use the launching shell's key (or configure the same
key securely in the requesting shell):

```powershell
$body = Get-Content .\examples\analyze-request.json -Raw
Invoke-RestMethod -Method Post `
    -Uri http://127.0.0.1:8000/api/v1/insights/analyze `
    -Headers @{ 'X-Internal-API-Key' = $env:INTERNAL_API_KEY } `
    -ContentType 'application/json' -Body $body
```

The foreground Uvicorn command occupies its terminal. Stop it first if needed,
or use Bruno from its own window for testing while the server runs.

1. Open `ai-service/bruno` as a **separate collection**, independent of core-api.
2. Copy `environments/Local.bru` to `Local.private.bru`, select the private
   environment, and add a secret `internalApiKey` variable matching the server.
   Do not save the real value in the shared environment or request files.
3. Run **01 Health**: expect 200. Run **02 Analyze**: expect 200, the six response
   fields, Housing as highest spending, 15% savings concern, and budget warning.
4. Run **03 Missing Key** and **04 Invalid Key**: expect 401. Run **05 Invalid
   Summary** with a valid key: expect 422 without financial values being echoed.
5. For an exceeded budget, change the sample budget's limit to `"400.00"`:
   spending equals 100% and produces EXCEEDED. For no activity, use income,
   expenses and savings `"0.00"`, rate `null`, and both lists empty; expect 200.
6. Remove a required field or add `password` to a disposable sample: expect 422.
   Do not use real credentials when testing rejection behavior.

Private Bruno environments, `.env` files, the virtual environment, caches and
logs are ignored. No Spring authentication request is needed for this collection.

## Tests and key files

From `ai-service`, with the virtual environment activated:

```powershell
python -m pytest -q
python -m pip check
python -m compileall -q app
```

HTTPX's ASGI transport runs API requests in process. Tests explicitly enter the
application lifespan so missing startup credentials cannot be hidden by a test.
AnyIO (a FastAPI dependency) runs the async tests on asyncio; there is no extra
async test dependency. Fixtures generate ephemeral random API keys. Unit tests
exercise the engine separately, including exact budget boundaries, deterministic
ties, decimal rounding, low/negative/zero savings, and empty data. API tests cover
schema checks, key rejection, OpenAPI security, sanitized validation errors, and
absence of payload values in application logs. Tests do not use or access H2.

| File | Role |
| --- | --- |
| `app/main.py` | App factory, lifespan settings validation, public health, sanitized 422 responses |
| `app/core/config.py` | Environment configuration and secret-redacted settings |
| `app/api/insights.py` | Internal API-key dependency, constant-time comparison, analyze route |
| `app/schemas/financial.py` | Strict, bounded aggregate contract, Decimal parsing and consistency checks, safe response schema |
| `app/services/insight_engine.py` | Pure rules, documented thresholds, deterministic ordering and educational disclaimer |
| `tests/` | HTTPX API tests, configuration tests, engine and sample-contract tests |
| `requirements.txt` | Pinned dependencies verified on Python 3.14; includes the optional OpenAI adapter, no database libraries |
| `pyproject.toml` | Pytest discovery/import settings |
| `bruno/`, `examples/` | Manual API examples with no real secrets |

Troubleshooting:

- `INTERNAL_API_KEY must contain at least 32 bytes`: configure the key in the shell
  launching Uvicorn, then restart. A variable from another terminal is not inherited.
- 401: the requesting client's key must match the server exactly; no `Bearer`
  prefix is used. Keep the key out of URL query parameters.
- 422: check decimal strings, all required fields, savings consistency and unknown
  fields. Zero income requires an explicit `null` savings rate.
- Port 8000 already in use: stop your earlier Uvicorn run or select another port
  and update your local Bruno environment.
- Missing package: use `.\.venv\Scripts\python.exe -m pip install -r requirements.txt`
  and launch with that same interpreter.

Suggested commit: `feat: add standalone financial insights rules service`

## Financial chatbot (Milestone 7C)

The existing deterministic insights API is unchanged. A new protected endpoint
answers one financial question per request, with no persistent chat history:

```http
POST /api/v1/chat/respond
X-Internal-API-Key: <same existing internal key>
Content-Type: application/json
```

Request fields: `question` (trimmed, 1..1000 characters), `month` (1..12), `year`
(2000..2100), and `financialSummary` (the exact existing aggregate schema). Both
periods must match. Financial context is limited to 32 KiB of serialized UTF-8 JSON.
Unknown fields and invalid types return 422; missing/invalid internal keys return
401. Spring obtains the financial values from its verified user's dashboard;
normal clients should call Spring's `/api/v1/chat/messages`, not construct context.

Example internal body:

```json
{
  "question": "Explain my income, expenses, savings and budgets.",
  "month": 10,
  "year": 2026,
  "financialSummary": {
    "month": 10,
    "year": 2026,
    "totalIncome": "1000.00",
    "totalExpenses": "850.00",
    "netSavings": "150.00",
    "savingsRate": "15.00",
    "expensesByCategory": [{"categoryId":1,"categoryName":"Food","amount":"850.00"}],
    "budgetUtilization": []
  }
}
```

The response contains only `answer` (text, max 8000 characters), `provider`
(`openai` or `rule-based`), and the same educational financial/investment/tax/legal
disclaimer used by deterministic insights. There is no model-supplied disclaimer
that can remove or override the application's disclaimer.

### Optional OpenAI configuration

Install the updated official SDK dependency in the activated virtual environment:

```powershell
python -m pip install -r requirements.txt
```

Keep `INTERNAL_API_KEY` configured as before. For rule-based mode, leave
`OPENAI_API_KEY` and `OPENAI_MODEL` unset. To opt into OpenAI, in the **FastAPI
launching shell**, safely enter the key and select your available model:

```powershell
$secureKey = Read-Host 'OpenAI API key' -AsSecureString
$keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $env:OPENAI_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
    $secureKey.Dispose()
}
$env:OPENAI_MODEL = Read-Host 'Responses-compatible model available to your API project'
$env:OPENAI_TIMEOUT_SECONDS = '3'
$env:OPENAI_MAX_OUTPUT_TOKENS = '800'
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --no-access-log
```

No model name or actual secret is hardcoded. Both key and model are required to
select OpenAI. Optional timeout range is greater than zero through 30 seconds;
output limit range is 64..2048 tokens. Defaults are 3 seconds and 800 tokens.
SDK retries are disabled. An outer async deadline also bounds each provider call.
Keep Spring's read timeout above the configured provider deadline plus a margin.

Calls use the official SDK's `responses.create`, separate system instructions,
`max_output_tokens`, `store=False`, and no tools. Requests go to the official API
origin; alternate endpoints are not configured in this milestone. No conversation
or previous-response identifier is supplied. SDK debug logging is disabled to
avoid full-body diagnostics. The provider response must complete and contain
usable text within the character bound; otherwise the service returns rules.
See the [official Responses API reference](https://developers.openai.com/api/reference/python/resources/responses/methods/create)
and [official SDK documentation](https://developers.openai.com/api/reference/python).

OpenAI mode sends the bounded question, month, aggregate amounts, category names,
and budget values to OpenAI. It omits internal category IDs, user identity,
credentials, raw transaction descriptions and history. Do not include secrets or
unnecessary personal information in a question. `store=False` does not establish
a zero-retention agreement; applicable provider data policies still govern calls.
Real provider requests may incur charges. Automated tests never make them.

### Grounding and fallback behavior

System instructions require answers based only on supplied values, practical
budgeting suggestions, explicit insufficient-data responses, and educational
advice. User questions and category/description text are untrusted and cannot
supply higher-priority instructions. No tools or financial write methods exist,
so neither model output nor question text can execute a financial transaction.
Prompts are a constraint, not a proof that every generated statement is correct;
model output is checked for completion, shape and size, not audited line by line
against a ledger. Review generated numeric claims.

`ChatProvider` separates orchestration from SDK details. Missing key/model,
optional provider setup failure, SDK errors (including unavailable model,
authentication or rate limit), timeout, empty output or incomplete generation
select the deterministic fallback. No upstream exceptions or secrets are returned.
Fallback remains HTTP 200 and explicitly labels `provider: "rule-based"`.
A failure of FastAPI itself still becomes Spring's existing 502/504 response.

Rules recognize a limited set of English questions about income, expenses,
savings, spending, budgets and financial overviews, such as:

- `Explain my income, expenses, savings and budgets.`
- `How can I save money?`
- `What is my savings rate?`
- `Show my budget usage.`

The fallback reuses `insight_engine.analyze`, reports actual supplied totals,
shows up to five expense categories and five budgets, and reuses its concerns
and practical suggestions. It states when detail is capped. Empty months safely
report zero activity and unavailable savings rate. Other periods, transaction
lists, forecasts, transfers, investments, tax/legal questions and unrelated topics
receive an unsupported/insufficient-data message rather than invented facts.
This is not general natural-language understanding; ambiguous phrasing may be
unsupported or receive a general overview. It does not search or access other
months or accounts. No application database, chat log, cache or audit store is added.

### Manual and automated verification

Use the updated Core API Bruno requests 33..36 described in the root README.
The separate `ai-service/bruno/06 Chat Respond.bru` request tests this internal
contract directly with the existing private `internalApiKey` variable. It does
not need a Spring JWT. Missing keys return 401, invalid/oversized questions or
period mismatches return 422. Compare current aggregates with Spring's dashboard.

```powershell
python -m pytest -q
python -m pip check
python -m compileall -q app
```

Tests use mocked providers and mocked official SDK responses, including timeout
and connection failures, incomplete output, safe fallback, unknown questions,
empty data, input limits, API-key security and educational disclaimer. An autouse
fixture prohibits unmocked Responses API calls. Optional real OpenAI verification
requires your account access and is deliberately manual. Bruno desktop checks
also remain manual.

For free local testing after any real-provider experiment:

```powershell
Remove-Item Env:OPENAI_API_KEY -ErrorAction SilentlyContinue
Remove-Item Env:OPENAI_MODEL -ErrorAction SilentlyContinue
# Restart Uvicorn with the same internal key; chat now uses rules.
```
