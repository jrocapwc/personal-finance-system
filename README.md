# Personal Finance Management System

Milestone 6 adds a monthly financial dashboard, including transaction summaries and live budget utilization, to the authenticated
`core-api`, using Java 25, Spring Boot 4.1.1, Maven, Spring Security, JWT, JPA, Flyway, and H2.

## Standalone Financial Insights Service (Milestone 7A)

`ai-service` now provides a separate Python 3.14 FastAPI rules engine with public
`GET /health` and API-key-protected `POST /api/v1/insights/analyze`. It accepts
validated monthly aggregates and returns deterministic educational observations,
concerns and suggested actions. It does not connect to core-api or its database.

See [ai-service setup, API contract, rules, Bruno examples and tests](ai-service/README.md).
The local service uses port 8000 and `INTERNAL_API_KEY`; core-api retains port 8080
and its separate JWT configuration. Existing core-api setup instructions follow.

## Windows PowerShell setup and startup

Run from the repository root. Use your actual JDK 25 installation path:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
Push-Location core-api
.\mvnw.cmd --version
```

Generate the required JWT signing key in this PowerShell session without printing it:

```powershell
if (!$env:JWT_SECRET_BASE64) {
    $keyBytes = New-Object byte[] 32
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($keyBytes) } finally { $random.Dispose() }
    $env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($keyBytes)
    [Array]::Clear($keyBytes, 0, $keyBytes.Length)
}

.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

`JAVA_HOME` must select JDK 25. `JWT_SECRET_BASE64` must contain a Base64-encoded,
cryptographically random key of at least 32 decoded bytes. There is no default
signing key; a missing, malformed, or short key prevents startup. Set this variable
in the same shell or IDE run configuration that launches the API. A `.env` file
is not loaded automatically. Do not print or commit the key.

The generated key lasts for this shell session. Use the same securely stored key
across restarts if tokens must remain valid; changing it invalidates existing
tokens. Tests generate their own ephemeral key and need no environment variables.

Startup logs should report the `local` profile. Run from `core-api` so the JDBC URL
`jdbc:h2:file:./data/personal-finance` consistently opens
`core-api/data/personal-finance.mv.db`. The default HTTP port is 8080. Ctrl+C stops
the API; run `Pop-Location` when finished. Installed `mvn` may replace `mvnw.cmd`.
Maven Wrapper and uncached dependencies require network access on first use.

## Endpoints

| Method and path | Access | Result |
| --- | --- | --- |
| `POST /api/v1/auth/register` | Public | 201 and a safe user object |
| `POST /api/v1/auth/login` | Public | 200 with `accessToken`, `tokenType`, `expiresIn`, and safe `user` |
| `GET /api/v1/users/me` | Bearer JWT required | 200 and the authenticated user's current data |
| `GET /api/v1/dashboard?month=10&year=2026` | Bearer JWT required | 200 and monthly financial dashboard |
| `POST /api/v1/budgets` | Bearer JWT required | 201 and budget with live utilization |
| `GET /api/v1/budgets?month=10&year=2026` | Bearer JWT required | 200 and monthly budget array |
| `GET /api/v1/budgets/{id}` | Bearer JWT required | 200 and owned budget |
| `PUT /api/v1/budgets/{id}` | Bearer JWT required | 200 and replaced budget |
| `DELETE /api/v1/budgets/{id}` | Bearer JWT required | 204 with no response body |
| `POST /api/v1/transactions` | Bearer JWT required | 201 and created transaction |
| `GET /api/v1/transactions` | Bearer JWT required | 200 and a bounded page |
| `GET /api/v1/transactions/{id}` | Bearer JWT required | 200 and owned transaction |
| `PUT /api/v1/transactions/{id}` | Bearer JWT required | 200 and replaced transaction |
| `DELETE /api/v1/transactions/{id}` | Bearer JWT required | 204 with no response body |
| `GET /api/v1/categories?type=EXPENSE` | Bearer JWT required | 200 and owned categories; type is optional |
| `POST /api/v1/categories` | Bearer JWT required | 201 and created category |
| `PUT /api/v1/categories/{id}` | Bearer JWT required | 200 and updated category |
| `DELETE /api/v1/categories/{id}` | Bearer JWT required | 204 with no response body |
| `GET /actuator/health` | Public | 200 and JSON status `UP` when healthy |
| `GET /actuator/health/liveness` | Public | Application liveness |
| `GET /actuator/health/readiness` | Public | Application readiness |
| `/h2-console/` | Local profile only | Development database console |

Swagger UI and the OpenAPI document are also public. All other application paths
require a verified token. No form login, HTTP Basic,
session authentication, roles, refresh tokens, or logout API are implemented.
A bearer token is sent in `Authorization`, not cookies or URLs.
Tokens expire after 3,600 seconds and contain a user ID, issuer, audience, and
standard timestamps. Validation checks the signature, algorithm, timestamps,
issuer, audience, required claims, and whether the user still exists. JWTs do not
contain email addresses or password hashes. Use HTTPS when serving beyond local
development; bearer tokens grant access to whoever possesses them.

## Swagger UI and OpenAPI

After starting the API, open **http://localhost:8080/swagger-ui.html** (redirects
to `/swagger-ui/index.html`). The OpenAPI JSON is at
**http://localhost:8080/v3/api-docs**; YAML is at `/v3/api-docs.yaml`.
These documentation routes and UI assets are accessible without authentication.
The API still requires `JWT_SECRET_BASE64` at startup as described above.

1. Expand **Authentication → Register a user**, select **Try it out**, and enter
   your name, email, and a password of at least 12 characters (at most 72 UTF-8 bytes).
2. Execute **Log in** with the same email/password and copy `accessToken` from
   the response.
3. Click **Authorize** and paste only the token, without the `Bearer` prefix.
4. Execute **Users → Get my profile**. Swagger sends the bearer header for you.
5. Use **Authorize → Logout** to clear the token from Swagger. This clears the
   UI's authorization state; it does not revoke the token on the server.

Only `/api/v1/**` application endpoints appear in the document. Request validation,
success/error responses, and the protected profile's bearer requirement are
included. Passwords are marked write-only, and user response schemas exclude
password fields. Swagger does not persist authorization across page reloads.

`core-api/src/main/java/core_api/config/OpenApiConfiguration.java` supplies the API
name and bearer scheme. Controller annotations describe operations and responses;
springdoc derives DTO schemas and validation constraints from the Java classes.
The Boot 4-compatible UI dependency is pinned to springdoc 3.1.1 in `pom.xml`.

If Swagger returns 401 or 404 after upgrading, restart the API and reload Maven
dependencies. If it cannot load the definition, verify `/v3/api-docs` is reachable.
A 401 from `/users/me` means a valid, unexpired token is needed even when Swagger
itself is accessible.
## API walkthrough (second PowerShell window)

```powershell
$baseUrl = 'http://localhost:8080'
Invoke-RestMethod "$baseUrl/actuator/health"

$name = Read-Host 'Name'
$email = Read-Host 'Email'
$securePassword = Read-Host 'Password (12+ characters, at most 72 UTF-8 bytes)' -AsSecureString
$password = [System.Net.NetworkCredential]::new('', $securePassword).Password

$registrationBody = @{ name = $name; email = $email; password = $password } | ConvertTo-Json
$user = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/auth/register" `
    -ContentType 'application/json' -Body $registrationBody
$user

$loginBody = @{ email = $email; password = $password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/auth/login" `
    -ContentType 'application/json' -Body $loginBody
$login | Select-Object tokenType, expiresIn, user

$headers = @{ Authorization = "Bearer $($login.accessToken)" }
Invoke-RestMethod -Uri "$baseUrl/api/v1/users/me" -Headers $headers
```

Registration accepts only `name`, `email`, and `password`. Name is trimmed and must
be nonblank with at most 100 characters. Email is trimmed, lowercased, validated,
and limited to 254 characters. Passwords must be nonblank, at least 12 characters,
and at most 72 UTF-8 bytes (BCrypt's limit); passwords are never trimmed. Only the
BCrypt hash is stored. Fields such as `roles`, `id`, `createdAt`, and `passwordHash`
are rejected with 400. Login accepts only `email` and `password`.

A successful user response has this shape (timestamps are UTC):

```json
{
  "id": "90fd355e-22b1-4ce2-918f-fcf7b5ef2b64",
  "name": "Alex",
  "email": "alex@example.com",
  "createdAt": "2026-10-19T00:00:00Z",
  "updatedAt": "2026-10-19T00:00:00Z"
}
```

Re-submit the registration body to verify HTTP 409. Try an incorrect password to
verify HTTP 401. These commands verify missing and invalid bearer tokens without
changing data:

```powershell
curl.exe -i "$baseUrl/api/v1/users/me"
curl.exe -i -H 'Authorization: Bearer invalid-token' "$baseUrl/api/v1/users/me"
```

Both return 401 and `WWW-Authenticate: Bearer`. Expired tokens also return 401.
Invalid request values or malformed JSON return 400. Errors use one format:

```json
{
  "timestamp": "2026-10-19T00:00:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/auth/register",
  "fieldErrors": { "email": "must be a well-formed email address" }
}
```

Other errors have an empty `fieldErrors` object. Responses do not expose rejected
password values, SQL messages, or stack traces. Unknown emails and incorrect
passwords use the same generic credentials error. Registration duplicate detection
uses both an application pre-check and the database unique constraint.

## Category management (Milestone 3)

Every category belongs to the user resolved from the verified JWT. Requests accept
only `name`, `type`, and optional `color`; `userId`, `id`, and other extra fields
are rejected with 400. Responses contain category fields only, without a JPA user
object or password information.

Names must be nonempty and at most 100 characters after trimming and collapsing
repeated whitespace. The display name preserves case. Uniqueness compares the
lowercase normalized name within each user and category type: ` Food ` and `FOOD`
are duplicates for the same user/type, while the same name for a different type
or user is allowed. Types must be `INCOME` or `EXPENSE`. Colors may be null or
`#RGB`/`#RRGGBB` hexadecimal strings; stored colors use uppercase.

The first successful category list or create request initializes these defaults:

| Type | Names |
| --- | --- |
| INCOME | Salary, Freelance, Investment Income, Other Income |
| EXPENSE | Food, Housing, Transportation, Entertainment, Healthcare, Other |

This also works for existing users created before this milestone. Registration
and login are unchanged. Initialization runs once per user in the same transaction
as the category request. A persistent `category_default_initializations` marker
prevents defaults from reappearing after deletion or renaming. A database lock on
the current user's row serializes category initialization and writes, so repeated
or concurrent requests cannot create duplicate defaults. Deleting all categories
leaves the list empty; it does not reset initialization.

List returns a JSON array, ordered by type and then normalized name. Filtering
uses `GET /api/v1/categories?type=INCOME` or `?type=EXPENSE`. PUT replaces the full
name/type/color value; omitted or null color clears the color. Updating or deleting
a missing category or another user's category returns the same 404 response.
Since Milestone 4, categories referenced by transactions or budgets cannot be deleted or
changed to a different type (409); their name and color may still be edited.
Duplicate names return 409; invalid bodies, filters, and nonnumeric IDs return
400; missing, invalid, or expired JWTs return 401.

Create/update request:

```json
{
  "name": "Education",
  "type": "EXPENSE",
  "color": "#4F46E5"
}
```

A successful POST returns HTTP 201 and a response like this (actual ID/time vary):

```json
{
  "id": 42,
  "name": "Education",
  "type": "EXPENSE",
  "color": "#4F46E5",
  "createdAt": "2026-10-19T00:00:00Z",
  "updatedAt": "2026-10-19T00:00:00Z"
}
```

PUT returns the same response shape with HTTP 200. DELETE returns 204 with no
body. The common `ApiError` format is used with codes `DUPLICATE_CATEGORY` and
`CATEGORY_NOT_FOUND` for 409 and 404 respectively.

### Manual category verification

Start with the existing startup instructions, then register/log in using the
walkthrough above. Keep `$baseUrl` and the bearer `$headers` from that walkthrough:

```powershell
$defaults = @(Invoke-RestMethod "$baseUrl/api/v1/categories" -Headers $headers)
$defaults.Count  # A fresh user's first list contains 10 defaults.
$again = @(Invoke-RestMethod "$baseUrl/api/v1/categories" -Headers $headers)
$again.Count     # Still 10; IDs stay the same.
Invoke-RestMethod "$baseUrl/api/v1/categories?type=INCOME" -Headers $headers

$body = @{ name = 'Education'; type = 'EXPENSE'; color = '#4F46E5' } | ConvertTo-Json
$category = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/categories" `
    -Headers $headers -ContentType 'application/json' -Body $body

$update = @{ name = 'Courses'; type = 'EXPENSE'; color = '#ABC' } | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri "$baseUrl/api/v1/categories/$($category.id)" `
    -Headers $headers -ContentType 'application/json' -Body $update

Invoke-RestMethod -Method Delete -Uri "$baseUrl/api/v1/categories/$($category.id)" -Headers $headers
curl.exe -i "$baseUrl/api/v1/categories"  # 401 without JWT
```

Use a fresh name when rerunning the POST. To check duplicates, submit `Food` with
`EXPENSE` twice for a fresh user; expect 409 because it is already a default.
To verify ownership, register/log in as a second user, copy a category ID from the
first user's list, then attempt PUT/DELETE with the second user's bearer header.
Both must return 404; the first user's category must remain unchanged. A second
user's list contains only that user's defaults/custom categories.

Swagger UI includes the Categories group; Authorize with the login access token
before trying these operations. The existing Bruno collection also includes
List Categories, Income Categories, Create Category, Update Category, and Delete
Category. Create stores its new ID in the runtime variable `categoryId` for the
update/delete requests; Login supplies the existing `accessToken` runtime variable.

`V2__create_categories.sql` adds categories, its unique key and indexes, and the
initialization marker table. It references the existing users table without
changing V1 or deleting user records. Flyway applies V2 on the next local startup;
Hibernate remains in schema-validation mode. Stop and back up the local H2 file
before upgrading it. Automated migration tests upgrade a V1 file containing a
user, verify the retained user, and verify subsequent runs apply no migrations.

`CategoryController` resolves ownership from Spring Security; `CategoryService`
coordinates transactional initialization, duplicate checks, and scoped mutations.
`CategoryRepository` includes user IDs in list and ID lookups. `CategoryRequest`
validates accepted input; `CategoryResponse` prevents entity/ownership leakage.
`Category` maintains normalized names and lifecycle timestamps; `CategoryType`
stores enum names as strings. The initialization entity/repository records that
defaults have already been provided, independently of their editable names.

Category integration tests cover defaults, filtering, repeated/concurrent seeding,
CRUD, duplicate creation/update, normalization, color/input validation, ownership
field injection, missing JWTs, concurrent duplicate creation, and cross-user
isolation. Mockito tests verify unavailable categories cannot be saved or deleted.
The existing authentication suite is run alongside these tests.

Suggested commit message:

```text
feat: add authenticated category management with per-user defaults
```
When done, remove credentials and tokens from the example shell variables:

```powershell
Remove-Variable password, securePassword, registrationBody, loginBody, login, headers
```

## Transaction management (Milestone 4)

Transactions use the existing verified JWT for ownership. Request DTOs never accept
`userId`, `id`, `source`, or privileged fields. The service resolves the category
with both its ID and the current user's ID; foreign/missing categories return 404.
The existing `CategoryType` enum is reused for `INCOME` and `EXPENSE`.

Create with `POST /api/v1/transactions` or fully replace mutable fields using
`PUT /api/v1/transactions/{id}`:

```json
{
  "categoryId": 42,
  "type": "EXPENSE",
  "amount": 125.50,
  "description": "Groceries",
  "transactionDate": "2026-10-19"
}
```

Replace `42` with one of your EXPENSE category IDs. For income, use an owned INCOME
category and set `type` to `INCOME`. Amount must be positive, with at most 17 integer
digits and 2 decimal places. Java uses `BigDecimal` and H2 uses `DECIMAL(19,2)`;
excess decimals are rejected rather than rounded. Description is optional (maximum
255 characters). Transaction date is required in `yyyy-MM-dd` format; future valid
dates are allowed. PUT requires category/type/amount/date again; omitting description
clears it. Owner, creation time, and server-assigned `MANUAL` source cannot change.

POST returns 201; GET-by-ID and PUT return 200. A response has this shape:

```json
{
  "id": 73,
  "categoryId": 42,
  "type": "EXPENSE",
  "amount": 125.50,
  "description": "Groceries",
  "transactionDate": "2026-10-19",
  "source": "MANUAL",
  "createdAt": "2026-10-19T00:00:00Z",
  "updatedAt": "2026-10-19T00:00:00Z"
}
```

`DELETE /api/v1/transactions/73` returns 204 with no body. GET/PUT/DELETE all return
404 `TRANSACTION_NOT_FOUND` for missing IDs or another user's transactions. Responses
never serialize the JPA user/category entities or expose passwords.

### Listing, filtering, and sorting

Example:

```http
GET /api/v1/transactions?type=EXPENSE&categoryId=42&startDate=2026-10-01&endDate=2026-10-31&keyword=groceries&page=0&size=20&sort=amount&direction=ASC
Authorization: Bearer <accessToken>
```

| Parameter | Rules |
| --- | --- |
| `type` | Optional `INCOME` or `EXPENSE` |
| `categoryId` | Optional positive owned category ID; foreign/missing categories return 404 |
| `startDate`, `endDate` | Optional inclusive `yyyy-MM-dd` bounds; start must not exceed end |
| `keyword` | Optional literal, case-insensitive description substring; maximum 255 characters; `%`, `_`, and backslash are escaped |
| `page` | Zero-based, default 0; nonnegative and within JPA's supported integer offset range |
| `size` | Default 20; minimum 1, maximum 100 |
| `sort` | One of `id`, `amount`, `transactionDate`, `createdAt`, `updatedAt`, `description`, `type`; default `transactionDate` |
| `direction` | `ASC` or `DESC`, case-insensitive; default `DESC` |

Filters combine with AND and always include the current owner. Unknown sort fields,
invalid enums, malformed dates/numbers, reversed date ranges, excessive keywords,
and invalid pagination return 400. An ID tie-breaker follows the primary sort to
keep equal-valued rows in deterministic order. Out-of-range pages return empty
content with the same pagination structure. Lists use SQL pagination, not in-memory
slicing or unbounded entity retrieval.

```json
{
  "content": [
    {
      "id": 73,
      "categoryId": 42,
      "type": "EXPENSE",
      "amount": 125.50,
      "description": "Groceries",
      "transactionDate": "2026-10-19",
      "source": "MANUAL",
      "createdAt": "2026-10-19T00:00:00Z",
      "updatedAt": "2026-10-19T00:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "sort": "transactionDate",
  "direction": "DESC"
}
```

### Errors and category references

| Status | Meaning |
| --- | --- |
| 400 | Invalid fields/filters, invalid amount or date, or category/type mismatch |
| 401 | Missing, invalid, or expired JWT |
| 404 | Missing/inaccessible transaction or category |
| 409 | `CATEGORY_IN_USE` when deleting or changing the type of a referenced category |

Transaction errors use the existing `ApiError` response shape. Repeated identical
POST requests create separate transactions; this milestone does not deduplicate
financial entries. A referenced category may be renamed or recolored, but deleting
it or changing its type is blocked until all referencing transactions are removed
or reassigned to another owned matching category. This prevents category edits
from invalidating historical transaction types.

Transaction writes and category writes use the same per-user database lock. A
racing category deletion therefore either happens first (transaction creation
returns 404) or happens second (category deletion returns 409). A composite database
foreign key additionally enforces category owner/type matching and prevents
referenced category deletion/type changes independently of application checks.

### Step-by-step Bruno verification

1. Start the API using the Windows PowerShell startup/key commands above. Open
   `core-api/bruno`, select your private Local environment, then run Register and
   Login. Login fills the runtime `accessToken` variable.
2. Run **07 List Categories**. Its post-response script selects owned categories
   into `incomeCategoryId` and `expenseCategoryId`. Create a category first if you
   have previously deleted every category of either type, then rerun the list.
3. Run **12 Create Income Transaction**: expect 201 and `source: MANUAL`. It saves
   `incomeTransactionId` in memory.
4. Run **13 Create Expense Transaction**: expect 201. It saves `transactionId`.
5. Run **14 List Transactions** and **15 Filter Transactions**. Expect the pagination
   object above, with only the current user's matching rows. Change page/size to
   inspect pagination; use size 101 or a malformed date to verify 400.
6. Run **16 Get Transaction**, then **17 Update Transaction**. Expect 200; retrieve
   it again to confirm the new amount, description, and date.
7. Run **18 Delete Referenced Category**: expect 409 and `CATEGORY_IN_USE`. The
   category and transaction must remain present.
8. Run **19 Delete Transaction**: expect 204. Run Get Transaction again: expect 404.
   The category can be deleted once no other transaction references it.
9. Set an amount to 0, -1, or 1.001 and retry creation: expect 400. Pair INCOME with
   the expense category: expect 400. Do not include a `userId` or `source` field.
10. To verify isolation, note a transaction ID, register/log in as a second user,
    and rerun List Categories to refresh category variables. Use the first user's
    transaction ID with the second user's bearer token for GET/PUT/DELETE: expect
    404. Using the first user's category in a new transaction must also return 404.
    Removing the bearer token from any transaction request must return 401.

Bruno sample dates are fixed examples; adjust them and the filter range together.
Repeated create requests add new rows; the collection's Delete Transaction removes
only the most recently stored expense transaction ID. Swagger UI also includes
all transaction operations and bearer authorization.

### H2 schema, migration, tests, and key classes

`V3__create_transactions.sql` adds `transactions` with user/category references,
amount `DECIMAL(19,2)`, description `VARCHAR(255)`, `transaction_date DATE`, source,
and UTC lifecycle timestamps. Indexes cover `(user_id, transaction_date, id)`,
`(user_id, type)`, and `(user_id, category_id)`. V3 also adds a category composite
unique key used by the owner/type foreign key. It does not drop or rewrite existing
user/category data. V1 and V2 remain unchanged.

Stop the API and back up the local H2 file before the next startup applies V3.
The local profile, JDBC URL, H2 console, JWT key requirement, and port 8080 remain
unchanged. Hibernate still validates the migrated schema. In the H2 console:

```sql
SELECT id, category_id, type, amount, description, transaction_date, source
FROM transactions ORDER BY transaction_date DESC, id DESC;
SELECT "version", "description", "success" FROM "flyway_schema_history";
```

With JDK 25 selected, run from `core-api`:

```powershell
.\mvnw.cmd clean verify
```

Transaction HTTP tests cover income/expense CRUD, exact decimal persistence,
combined/individual filters, bounded pagination and sorting, wildcard escaping,
invalid amounts/dates/fields, foreign categories, missing JWTs, cross-user isolation,
category reference protection, and a category-delete/create race. Mockito tests
verify owner-scoped access and that invalid category/type choices cannot be saved.
Migration tests upgrade V1 and V2 files, preserve existing users/categories, verify
the database constraints/indexes, and verify repeat migration runs are no-ops.
Tests use isolated databases and do not modify the live local file.

| Class | Responsibility |
| --- | --- |
| `transaction/Transaction` | JPA mapping with `BigDecimal`, immutable ownership/source, and timestamps |
| `transaction/TransactionRequest`, `TransactionResponse` | Validated input and explicit safe output |
| `transaction/TransactionService` | Owner-scoped CRUD, category/type checks, and shared write locking |
| `transaction/TransactionRepository` | Owned ID lookup and paged specification execution |
| `transaction/TransactionSpecifications` | Mandatory owner predicate and composable SQL filters |
| `transaction/TransactionQuery`, `TransactionPageResponse` | Validated bounded pagination, safe sorts, and stable response metadata |
| `transaction/TransactionController` | Five authenticated routes and Swagger descriptions |
| `category/CategoryService` | Rejects referenced category deletion/type changes with 409 |

Suggested commit message:

```text
feat: add secure income and expense transaction management
```
## Budget management (Milestone 5)

Budgets set a monthly spending limit for one owned EXPENSE category. The existing
users table uses UUID IDs, so `budgets.user_id` is UUID (matching the existing
foreign key), while budget IDs and category IDs are BIGINT. Changing user IDs to
BIGINT would break existing references and JWT identities, so this milestone
preserves the working user model and data.

All five budget endpoints require a bearer JWT and resolve ownership from Spring
Security. Category ownership is checked separately. Missing/inaccessible budgets
return 404 `BUDGET_NOT_FOUND`; unavailable categories return 404 `CATEGORY_NOT_FOUND`.
Only the current user's data is returned, using explicit DTOs rather than entities.

POST `/api/v1/budgets` returns 201. PUT `/api/v1/budgets/{id}` fully replaces the
editable fields and returns 200. Both accept exactly:

```json
{
  "categoryId": 42,
  "month": 10,
  "year": 2026,
  "monthlyLimit": 1000.00,
  "alertThreshold": 80
}
```

Use your own EXPENSE category ID. Monthly limits must be positive, with at most 17
integer digits and 2 decimal places. Month is 1..12, year is 2000..2100 inclusive,
and alert threshold is an integer from 1..99. Income categories are rejected with
400. Missing/invalid values return 400; duplicate user/category/month/year budgets
return 409 `DUPLICATE_BUDGET`, including updates that collide with another budget.
The same category may have budgets for different months or years.

Never send `userId`, `id`, `spentAmount`, `percentageUsed`, or status fields. Unknown
fields are rejected, ownership cannot change, and spending is never stored as an
independently editable budget value. A budget may be moved to another owned expense
category/period through PUT; its utilization is recalculated for that new scope.

GET `/api/v1/budgets/{id}` and successful POST/PUT return:

```json
{
  "id": 9,
  "categoryId": 42,
  "month": 10,
  "year": 2026,
  "monthlyLimit": 1000.00,
  "alertThreshold": 80,
  "spentAmount": 250.00,
  "remainingAmount": 750.00,
  "percentageUsed": 25.00,
  "budgetStatus": "SAFE",
  "createdAt": "2026-10-19T00:00:00Z",
  "updatedAt": "2026-10-19T00:00:00Z"
}
```

GET `/api/v1/budgets?month=10&year=2026` requires both query parameters and returns
an array of these objects, ordered by budget ID. No matching budgets returns `[]`.
Missing, nonnumeric, or out-of-range period parameters return 400. DELETE
`/api/v1/budgets/{id}` returns 204 with no body and leaves transactions/categories
untouched. Budget timestamps track changes to the budget definition; transaction
changes update calculated utilization without changing the budget's `updatedAt`.

### Live spending and status calculations

For a budget in month M, year Y, SQL sums transaction amounts only when:

- `transaction.user_id` is the authenticated user;
- `transaction.category_id` equals the budget category;
- transaction type is `EXPENSE`;
- `transaction_date >= first day of M` and `< first day of the following month`.

This half-open range handles leap years, December-to-January boundaries, and the
entire last day without time-of-day assumptions. Income, other users/categories,
and records outside the month are excluded. No matching expenses means 0.00.
Adding, editing, moving, or deleting transactions automatically affects subsequent
budget responses; no synchronization job or stored spending counter is needed.

```text
spentAmount     = SUM(matching expense transactions)
remainingAmount = monthlyLimit - spentAmount
percentageUsed  = spentAmount * 100 / monthlyLimit
```

All arithmetic uses `BigDecimal`. Amounts and remaining amounts have two decimal
places; remaining amounts may be negative. The displayed percentage is rounded to
two decimals with `RoundingMode.HALF_UP`. Status uses exact comparisons before
rounding, implemented through multiplication rather than approximate division:

| Status | Exact condition |
| --- | --- |
| SAFE | spentAmount * 100 < monthlyLimit * alertThreshold |
| WARNING | spentAmount * 100 >= monthlyLimit * alertThreshold and spentAmount < monthlyLimit |
| EXCEEDED | spentAmount >= monthlyLimit |

Consequently, a percentage just below a boundary may round up visually while
remaining below that boundary for status purposes. For example, 7,999.99 spent out
of 10,000.00 displays 80.00% but remains SAFE at an 80% threshold. Rounding cannot
trigger an early warning or exceeded status.

Monthly lists fetch budget definitions plus one grouped spending query, avoiding
one sum query per budget. Single-budget responses use one category sum. Read
responses reflect database spending at query time; they are not historical snapshots.
Budget writes reuse the shared per-user database lock used by category/transaction
writes, serializing duplicate checks and category reference changes.

A category referenced by a budget cannot be deleted or changed to INCOME (409
`CATEGORY_IN_USE`), even if it has no transactions. Renaming/recoloring remains
allowed. Removing a budget never removes the underlying transactions. Category
removal becomes possible only after all budget and transaction references are gone.

### Bruno verification, step by step

1. Start the API with the existing JDK 25, `JWT_SECRET_BASE64`, and `local` profile
   commands. Open `core-api/bruno` and select your Local/private environment.
2. Register if needed, then run Login and **07 List Categories**. This fills
   `accessToken` and `expenseCategoryId`. Create an expense category if none remains.
3. Run **20 Create Budget**: expect 201 and an in-memory `budgetId`. Its sample
   period is October 2026, limit 1000.00, threshold 80. For predictable calculations,
   use a fresh category with no existing expenses in that month.
4. Run **21 List Budgets** and **22 Get Budget**: expect 200. A fresh category's
   budget has spent 0.00, remaining 1000.00, percentage 0.00, status SAFE.
5. Before updating the budget definition, run **13 Create Expense Transaction**
   with the same expense category, date `2026-10-19`, and amount 800.00. Run Get
   Budget again: expect spent 800.00, remaining 200.00, percentage 80.00, WARNING.
6. Use **17 Update Transaction** to change that transaction to 1100.00 in the same
   category/month. Get Budget must show spent 1100.00, remaining -100.00, percentage
   110.00, EXCEEDED. Use **19 Delete Transaction**, then get the budget again: spent
   returns to zero for this fresh category. Income transactions must not affect it.
7. Run **23 Update Budget**: expect 200 with the new limit/threshold. Repeating PUT
   on the same budget is allowed. To test duplicates, POST the same category/period
   again: expect 409. Keep the existing ID when the duplicate request clears the
   sample's runtime `budgetId`, or restore it manually before later requests.
8. Try limit 0, -1, or 1.001; month 13; year 1999; threshold 100; or an income
   category: expect 400. Use another user's category: expect 404.
9. Log in as a second user and use the first user's budget ID for GET/PUT/DELETE:
   expect 404. Monthly listing must show only the second user's budgets. Remove or
   corrupt the bearer token on any budget request: expect 401.
10. Return to the owning user's login and restore the budget ID. Try deleting or
    changing the type of its category: expect 409. Run **24 Delete Budget**: expect
    204. Get it again: expect 404. The category/transactions must still exist.

Sample dates and periods are fixed examples; adjust both together. Re-running a
successful Create Budget for the same tuple returns 409. The existing Swagger UI
also includes all budget operations with bearer authorization.

### Budget schema, migration, tests, and key methods

`V4__create_budgets.sql` creates budgets without editing V1/V2/V3 or resetting data.
The unique key is `(user_id, category_id, month, year)`; a monthly lookup index
covers `(user_id, year, month, id)`. `month` and `year` are quoted lowercase column
names for H2 compatibility. An internal, fixed `category_type = 'EXPENSE'` column
supports the composite foreign key to the existing category owner/type key. This
ensures ownership and expense eligibility at database level as well as in service
validation. It is not client-editable or exposed in responses.

The local file database, port, key setup, and H2 console remain unchanged. Stop and
back up the local H2 file before starting with V4. Flyway applies pending migrations
and Hibernate validates the resulting schema. In the local H2 console:

```sql
SELECT id, category_id, "month", "year", monthly_limit, alert_threshold
FROM budgets ORDER BY "year", "month", id;
SELECT "version", "description", "success" FROM "flyway_schema_history";
```

From `core-api`, with JDK 25 selected:

```powershell
.\mvnw.cmd clean verify
```

Tests use isolated H2 databases and random JWT keys. Budget HTTP tests cover CRUD,
monthly lists, duplicates/concurrent creation, input validation, expense eligibility,
category and budget ownership, missing/invalid JWTs, all statuses, month boundaries,
and live recalculation after transaction writes. Unit tests cover exact threshold
comparisons and rounding; Mockito tests cover scoped lookups and grouped aggregation.
Migration tests upgrade V3 with existing users/categories/transactions, verify their
preservation, and check budget uniqueness, ownership/type, and range constraints.
The existing authentication, category, transaction, Swagger, and migration tests
remain in the full suite.

| Class/method | Responsibility |
| --- | --- |
| `budget/Budget` | Stores budget definition, immutable owner, category, period, limit, threshold, and timestamps |
| `budget/BudgetRequest`, `BudgetResponse` | Validated input and safe output with derived spending/status |
| `budget/BudgetRepository` | Owner-scoped ID/month queries, duplicate checks, and category-reference checks |
| `budget/BudgetService.create/update/delete` | Enforces ownership, expense eligibility, uniqueness, and shared write locking |
| `budget/BudgetService.list/get` | Reads definitions and current expense sums, without storing derived spending |
| `budget/BudgetUtilization.calculate` | Exact BigDecimal formulas, two-decimal display rounding, and status classification |
| `budget/BudgetPeriod` | Validates the documented period range and derives half-open date bounds |
| `transaction/TransactionRepository.sumExpensesByCategory` | One grouped expense sum for a monthly budget list |
| `transaction/TransactionRepository.sumExpenseForCategory` | One owned category expense sum for single-budget responses |
| `budget/BudgetController` | Five authenticated budget routes and Swagger descriptions |

Suggested commit message:

```text
feat: add monthly expense budgets with live transaction utilization
```
## Financial dashboard (Milestone 6)

```http
GET /api/v1/dashboard?month=10&year=2026
Authorization: Bearer <accessToken>
```

Both `month` (1..12) and `year` (2000..2100) are required. Successful requests
return 200. Missing, malformed, or out-of-range periods and unsupported query
parameters return the existing 400 `INVALID_REQUEST` error envelope. Missing,
invalid, or expired bearer tokens return 401. The owner comes exclusively from
the verified JWT; a `userId` query parameter is rejected.

The response has this structure (an empty February is shown in full):

```json
{
  "month": 2,
  "year": 2026,
  "summary": {
    "totalIncome": 0.00,
    "totalExpenses": 0.00,
    "netSavings": 0.00,
    "savingsRate": null
  },
  "expensesByCategory": [],
  "incomeByCategory": [],
  "dailySpendingTrend": [
    {"date":"2026-02-01","amount":0.00}, {"date":"2026-02-02","amount":0.00},
    {"date":"2026-02-03","amount":0.00}, {"date":"2026-02-04","amount":0.00},
    {"date":"2026-02-05","amount":0.00}, {"date":"2026-02-06","amount":0.00},
    {"date":"2026-02-07","amount":0.00}, {"date":"2026-02-08","amount":0.00},
    {"date":"2026-02-09","amount":0.00}, {"date":"2026-02-10","amount":0.00},
    {"date":"2026-02-11","amount":0.00}, {"date":"2026-02-12","amount":0.00},
    {"date":"2026-02-13","amount":0.00}, {"date":"2026-02-14","amount":0.00},
    {"date":"2026-02-15","amount":0.00}, {"date":"2026-02-16","amount":0.00},
    {"date":"2026-02-17","amount":0.00}, {"date":"2026-02-18","amount":0.00},
    {"date":"2026-02-19","amount":0.00}, {"date":"2026-02-20","amount":0.00},
    {"date":"2026-02-21","amount":0.00}, {"date":"2026-02-22","amount":0.00},
    {"date":"2026-02-23","amount":0.00}, {"date":"2026-02-24","amount":0.00},
    {"date":"2026-02-25","amount":0.00}, {"date":"2026-02-26","amount":0.00},
    {"date":"2026-02-27","amount":0.00}, {"date":"2026-02-28","amount":0.00}
  ],
  "recentTransactions": [],
  "budgetUtilization": [],
  "topSpendingCategories": []
}
```

For a month with income 1000.00 and expenses 200.00, `summary` is:

```json
{"totalIncome":1000.00,"totalExpenses":200.00,"netSavings":800.00,"savingsRate":80.00}
```

Category breakdown entries contain `categoryId`, `categoryName`, nullable `color`,
and `amount`, for example:

```json
{"categoryId":12,"categoryName":"Food","color":"#FF8800","amount":200.00}
```

`recentTransactions` reuses the existing safe transaction response: `id`,
`categoryId`, `type`, `amount`, `description`, `transactionDate`, `source`,
`createdAt`, `updatedAt`. `budgetUtilization` reuses the existing budget response
including definition, timestamps, `spentAmount`, `remainingAmount`,
`percentageUsed`, and `budgetStatus`. Neither exposes owners or JPA entities.
Configured budgets still appear with zero spending when the month has no expenses.

### Calculations and ordering

All queries use the authenticated owner and transaction dates in
`[first day of month, first day of next month)`, including December/January rollover.
Only INCOME transactions contribute to income; only EXPENSE transactions contribute
to expenses, daily spending, budget usage, and top spending categories.

- `netSavings = totalIncome - totalExpenses`, including negative savings.
- `savingsRate = netSavings * 100 / totalIncome`, or `null` when income is zero.
- Money uses `BigDecimal` with two decimal places. Savings rate uses two decimal
  places with `HALF_UP` rounding; display rounding does not change stored amounts.
- Category breakdowns sort by amount descending, then category ID ascending.
  `topSpendingCategories` is the first five expense entries under that ordering.
- Daily spending includes every day, sorted ascending, filling absent days with
  0.00. Clients can use `date` as Chart.js labels and `amount` as values; no frontend
  or Chart.js dependency is added.
- Recent transactions are limited in SQL to five and sort by transaction date
  descending, then creation timestamp descending, then ID descending.
- Budget utilization delegates to `BudgetService.list`, preserving its exact
  threshold comparisons, rounding, and deterministic ordering. Transaction edits
  and deletions affect subsequent dashboard requests immediately.

The endpoint is read-only: it does not initialize default categories, write
aggregates, create tables, or introduce migrations. V1 through V4 remain unchanged.

### Bruno verification

1. Start with the existing PowerShell/JWT setup above, then open `core-api/bruno`
   in Bruno and select your private local environment.
2. Register/login and list categories. For predictable totals, use a new test user
   and select one owned INCOME category and one owned EXPENSE category.
3. With the existing transaction requests, create income 1000.00 on 2026-10-01 and
   expense 200.00 on 2026-10-15. Set the matching category IDs and types. Create a
   budget for that expense category for October 2026, limit 500.00, threshold 80.
4. Run **25 Financial Dashboard**. Expect 200, income 1000.00, expenses 200.00,
   savings 800.00, savings rate 80.00, expense daily point 200.00 on October 15,
   and budget utilization 40.00%, remaining 300.00, SAFE. Other existing data will
   change these totals. Recent transactions are restricted to the selected month.
5. Update the expense to 450.00, reload: expenses become 450.00 and budget usage
   90.00%, WARNING. Delete that expense and reload: expenses become zero and budget
   usage zero, SAFE. These checks use the existing transaction endpoints.
6. Run **26 Empty Month Dashboard** after selecting a month with no transactions
   or budgets. Expect zero totals, null savings rate, empty category/recent/budget
   arrays, and one zero daily entry per calendar day.
7. Run **27 Invalid Dashboard Period**: expect 400. Remove the bearer token or use
   an invalid token on request 25: expect 401. Login as another user and verify
   their dashboard contains only their own transactions, categories, and budgets.

### Tests, query cost, and limitations

From `core-api` with JDK 25 selected, run `.\mvnw.cmd clean verify`. Dashboard HTTP tests cover summaries, category grouping, zero
income, empty/leap months, daily series, latest/top-five limits, January/December
bounds, budget updates after transaction changes, cross-user isolation, invalid
parameters, and JWT failures. Unit tests verify exact decimal calculations,
rounding, category tie breakers, bounded recent queries, and owner/date propagation.
The complete build also runs all existing milestone tests.

The repository performs three dashboard reads (category sums, daily sums, latest
five rows). Existing budget logic adds one definition query and, when budgets
exist, one grouped expense query: at most five domain queries, plus JWT identity
validation. Category metadata is projected in the aggregate query; recent DTOs use
category IDs without fetching category objects, avoiding N+1 lookups. Existing
user/date and user/category transaction indexes are reused.

Aggregations scan matching monthly rows; returned category groups and budgets
scale with the user's category/budget count, while recent rows are capped at five
and daily points at 31. Category breakdowns and budget lists are not paginated.
There is no cache or stored total. Multiple reads use the existing database
isolation level; concurrent writes can produce slightly different snapshots
between sections of one response. This is a personal-finance dashboard, not a
locked accounting report. No currency conversion or multi-currency totals exist.

| Class or method | Responsibility |
| --- | --- |
| `dashboard/DashboardController.get` | Authenticated route, accepted query parameters, owner extraction and OpenAPI description |
| `dashboard/DashboardService.get` | Validated month bounds, grouped results, daily zero filling, recent/top-five limits, budget reuse |
| `dashboard/FinancialSummary.from` | BigDecimal savings and rate, null rate at zero income |
| `dashboard/DashboardResponse`, `CategoryBreakdown`, `DailyTrend` | Safe response records with chart-friendly dates and amounts |
| `TransactionRepository.summarizeCategories`, `sumDailyExpenses`, `findRecentForDashboard` | Scoped aggregate projections and bounded recent query |

Suggested commit message:

```text
feat: add authenticated monthly financial dashboard
```

## H2 and migration behavior

Flyway was introduced in Milestone 2. The Boot-managed `spring-boot-starter-flyway`
applies pending migrations before JPA starts. V1 adds `users` and Flyway's history
table; V2 adds categories and initialization tracking; V3 adds transactions and
reference constraints; V4 adds monthly budgets. These migrations preserve existing
user, category, and transaction data. Hibernate uses `ddl-auto=validate`, so Flyway owns schema changes. SQL
initialization stays disabled. Existing migrations must not be edited after use;
add a new version for future schema changes.

Before the first local upgrade, stop the API and copy your existing `.mv.db` file
to a private backup location. Do not copy an H2 database while another process is
writing it. Tests and automated smoke checks use isolated databases or a copy,
not your live local file.

Flyway cleaning and automatic baselining are disabled. If the database unexpectedly
contains tables without migration history, startup stops instead of guessing a
baseline or erasing data. Inspect that schema and plan an explicit migration
before retrying. The Milestone 1 database had no application tables.

Open `http://localhost:8080/h2-console/` while the `local` profile is active:

| Setting | Value |
| --- | --- |
| Driver Class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:file:./data/personal-finance` |
| User Name | `sa` |
| Password | Leave blank |

```sql
SELECT id, name, email, created_at, updated_at FROM users;
SELECT "version", "description", "success" FROM "flyway_schema_history";
```

The console permits only local connections. Its separate
local-profile security chain allows same-origin frames and console requests
without CSRF tokens. Those console exceptions do not apply to application paths.
The bearer-only application chain disables CSRF because it does not authenticate
with cookies. Outside `local`, the console servlet and its security exceptions are
absent; unauthenticated requests to that protected path return 401.

Database files and environment files remain ignored by Git:

```powershell
# From the repository root:
git check-ignore core-api/data/personal-finance.mv.db .env
```

## Tests

From `core-api`, with JDK 25 selected:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

Tests explicitly activate `test`, generate a random signing key, and use an
isolated in-memory H2 database. HTTP tests cover registration, duplicate normalized
email, BCrypt hashing, login, password rejection, missing/malformed/expired or
otherwise untrusted tokens, authenticated `/me`, password-free responses,
validation, field injection, public health, and a disabled test console. Mockito
unit tests cover duplicate pre-checks and concurrent unique-constraint failures.
Migration tests use temporary H2 files, verify retained user data on subsequent
migration runs, and verify that unexpected existing schemas are left intact.

## Bruno examples

Open the collection directory `core-api/bruno` in the Bruno desktop app and select
`Local`. Create your own private environment by copying `Local.bru` to
`Local.private.bru`; add `name`, `email`, and `password` variables there (mark the
password secret in Bruno). Private environment files are ignored by Git. Do not
save passwords or tokens into the committed sample files.

Run Health, Register, Login, and Me in order. Login stores the token in the runtime
variable `accessToken` for Me; it does not persist it to a collection file. Missing
JWT and Invalid JWT requests demonstrate 401 responses. No CLI or Node.js setup
is required. The Bruno UI requests should be run manually; Maven tests exercise
the corresponding HTTP flows independently.

## Key classes and files

| File or package under `core-api` | Responsibility |
| --- | --- |
| `pom.xml` | Java 25 and Boot 4.1.1; manages Security, resource-server/JWT, Flyway, Web MVC, JPA, H2, Validation, Actuator, and test dependencies. |
| `CoreApiApplication` | Existing entry point and component scanning; unchanged. |
| `user/User`, `UserRepository` | Entity mapping, normalized unique email, lifecycle timestamps, and database access. |
| `user/UserResponse`, `UserController` | Explicit safe response projection and authenticated `/me`. Entities are not returned from controllers. |
| `auth/RegisterRequest`, `LoginRequest` | Validate accepted fields and normalize email. Their string representations redact credentials. |
| `auth/BcryptPasswordValidator` | Enforces BCrypt's byte limit, including multibyte passwords. |
| `auth/AuthController`, `AuthService` | HTTP entry points and registration/login workflow. Registration is transactional; login delegates password checks to Spring Security. |
| `security/SecurityConfiguration` | BCrypt, database-backed authentication provider, stateless bearer authentication, and endpoint authorization. |
| `security/JwtConfiguration`, `TokenService` | Required key validation, HS256 encoding/decoding, token claims and 60-minute lifetime. |
| `security/LocalConsoleSecurityConfiguration` | Development-only console filter chain. |
| `error/ApiExceptionHandler`, `SecurityErrorHandler`, `ApiError` | Consistent JSON errors from MVC and the security filter chain. |
| `src/main/resources/db/migration/V1__create_users.sql` | Versioned users schema, unique email constraint, and normalization check. |
| `application.properties` | Shared JWT, schema-validation, Flyway, strict JSON, and health settings. |
| `application-local.properties` | File-based H2 and local console. |
| `src/test/resources/application-test.properties` | In-memory H2 and disabled console; test code supplies random key and database name. |

## Troubleshooting

- **H2 console 404:** confirm the `local` profile is active and that Maven/your IDE
  has refreshed dependencies. Restart after changing the profile.
- **Missing JWT key / key validation error:** generate `JWT_SECRET_BASE64` above
  and launch from the same shell. The value must decode to at least 32 bytes.
- **401 from `/me`:** log in and send the returned access token as `Bearer`.
  Check expiration, key changes, and whether the user still exists.
- **409 from registration:** that normalized email already exists; log in or use
  another email.
- **400 from registration:** inspect `fieldErrors`, use only supported fields,
  and respect the password's character and UTF-8 byte limits.
- **Flyway reports a nonempty schema without history:** preserve the database,
  inspect existing tables, and plan an explicit baseline/migration. Do not reset
  data or enable automatic baselining as a workaround.
- **H2 database locked:** stop other API processes using the same file. Use the
  running API's browser console to inspect the database.
- **Port 8080 occupied:** stop the other API or set `$env:SERVER_PORT = '8081'`
  before startup and update request URLs.
- **Wrong Java version:** check `JAVA_HOME`, `java -version`, and wrapper output.
  Put JDK 25's `bin` first on `PATH` in the current shell.
- **Dependency downloads fail:** check network/proxy access and Maven cache write
  permissions. Maven may log 401 metadata warnings for optional Flyway feeds;
  verify the final build result and Maven Central dependency resolution.
- **New dependencies unresolved in the IDE:** reload the Maven project, then run
  `mvnw.cmd clean verify`. A clean build replaces stale IDE-generated class files.
- **Flyway warns that H2 2.4 is newer than its verified version:** Boot currently
  manages H2 2.4.240. The migration and restart-preservation tests pass with it;
  keep the Boot-managed versions together rather than changing one independently.
