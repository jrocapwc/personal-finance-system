# Milestone 7D: MongoDB chatbot conversations

Spring Boot continues to own authentication and all H2 financial records. FastAPI owns
conversation storage in local MongoDB. No Docker, frontend, Node.js, MySQL migration,
or deployment is involved. Existing `POST /api/v1/chat/messages` remains stateless and
does not require a working MongoDB connection.

## Components and request flow

1. `ConversationController` validates requests and resolves the owner from the existing
   verified JWT authentication. Client `ownerId` body/query fields are rejected.
2. `ConversationService` reads owned metadata before a send. GENERAL skips dashboard
   access. FINANCE calls `DashboardService` for a fresh aggregate for the stored month/year
   on every turn. Raw transactions, identities, and complete dashboards are not forwarded.
3. `ConversationClient` reuses the bounded, authenticated internal HTTP client. It sends
   `X-Internal-API-Key` and `X-Owner-Id`, never client Authorization headers or cookies.
   Response/request wrapper objects redact their `toString()` for Spring diagnostic logs.
   The validation exception resolver's verbose diagnostic is disabled because it can
   include rejected field values, even when request objects redact themselves.
4. FastAPI's `conversations.py` requires the internal key before using the owner UUID.
   This header is trusted only from the authenticated internal caller. Keep FastAPI on loopback.
5. `MongoDatabase` creates one lazy pooled PyMongo client per application and closes it
   on shutdown. Database work runs via Starlette's worker thread pool, including cursor
   iteration and shutdown, so synchronous PyMongo never blocks the event loop.
6. `ConversationRepository` checks `_id` AND `ownerId` before every conversation read,
   history query, or append. Another owner's ID returns the same 404 as a missing ID.
7. The existing chatbot service/provider generates the answer, then the repository saves
   both messages and updates conversation activity. Only after all writes succeed does
   the endpoint return success. Financial fallback and both modes' disclaimers remain intact.

Only the latest 12 saved messages (each at most 8000 characters) enter OpenAI context.
History is explicitly labeled untrusted conversation data in a user input block, never
system instructions or authoritative financial records. Only the fresh supplied summary
may ground financial amounts. GENERAL never includes a financial summary. Neither owner
UUIDs nor internal credentials are part of OpenAI prompts. OpenAI storage remains disabled.
Question length is 1–1000 characters after trimming; assistant answers are 1–8000.
Financial aggregates retain the existing 32 KiB limit. Title length is 1–100.
Passwords, JWTs, service keys, and dashboard snapshots have no persistence fields;
users should avoid putting secrets in free-text questions or titles.

## Local Windows PowerShell startup

MongoDB must already be running as a local service. Verify it:

```powershell
mongosh 'mongodb://127.0.0.1:27017' --quiet --eval 'db.runCommand({ ping: 1 })'
```

In the FastAPI terminal, from the repository root:

```powershell
Set-Location ai-service
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
# Use the same securely stored random internal key in both terminals.
# You may generate it once without displaying it:
$env:INTERNAL_API_KEY = .\.venv\Scripts\python.exe -c 'import secrets; print(secrets.token_urlsafe(32))'
$env:MONGODB_URI = 'mongodb://127.0.0.1:27017'
$env:MONGODB_DATABASE = 'personal_finance_ai'
# Configure OPENAI_API_KEY and OPENAI_MODEL through ignored .env or your environment.
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --no-access-log
```

To reuse the generated key across shells, store it securely in ignored `ai-service/.env`
or your existing local secret mechanism; do not generate a different key for Spring.
FastAPI loads `ai-service/.env` automatically; explicit process settings take precedence.
MongoDB defaults match the commands above. URI credentials, if ever needed, belong only
in ignored configuration. The URI is redacted from the settings representation.

In the Spring terminal, set `AI_INTERNAL_API_KEY` to that same internal key and configure
`JWT_SECRET_BASE64` using the root README's key generation instructions. Then:

```powershell
Set-Location core-api
$env:AI_SERVICE_BASE_URL = 'http://127.0.0.1:8000'
# Allow the OpenAI deadline plus MongoDB reads/writes; keep provider timeout below this.
$env:AI_SERVICE_READ_TIMEOUT = '20s'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

PyMongo timeouts: server selection/connect 1500 ms, socket 2000 ms, pool wait 1000 ms,
and overall per-operation budget 4000 ms. Writes require acknowledgement (`w=1`).
MongoDB is lazy: outages produce 503 on conversation operations without disabling health,
insights, or the existing stateless chatbot. Indexes are installed idempotently on first
create/list and MongoDB can reconnect after recovery without restarting the application.

## API and Bruno verification

Use Spring at `http://localhost:8080`, register/login with the existing Bruno requests,
and set `accessToken` to the login JWT. New requests are in `core-api/bruno`:

1. **44 Create Conversation** sends `{"mode":"GENERAL","title":"Learning Python"}`.
   It stores the returned `id` in the Bruno runtime `conversationId` variable.
   For FINANCE, change the body to
   `{"mode":"FINANCE","title":"October budget","month":10,"year":2026}`.
   Month must be 1–12 and year 2000–2100. GENERAL must omit the financial period.
2. **45 List Conversations** requests `?page=0&size=20`. Check `items`, `page`, `size`.
   Ordering is `updatedAt DESC, _id DESC` for timestamp ties. Pagination is zero based,
   size 1–100, page 0–100000; offset pagination reflects current activity, not a frozen snapshot.
3. **46 Read Conversation** requests `/{conversationId}?page=0&size=100`.
   It returns metadata, mode-specific disclaimer, and chronological `messages` ordered by
   `createdAt ASC, _id ASC`. Increment page to read further messages. IDs are strings,
   timestamps are UTC ISO 8601; owner identities are omitted from responses.
4. **47 Send Conversation Message** sends `{"question":"Explain Python decorators"}`.
   Repeat with a follow-up such as `Give another example`. For FINANCE, ask
   `Explain my savings` or `Explain my budgets`. Each response contains `answer`,
   `provider` (`openai` or `rule-based`), and `disclaimer`. Re-read to verify message pairs.
5. Login as User B. Listing must exclude User A's conversations. With A's saved ID,
   both read and send must return 404. Remove JWT to verify 401 on all four endpoints.
6. Use a malformed ID for 400; an unused 24-character hex ID for 404. Try empty/overlong
   questions and invalid modes for 400. Stop local MongoDB to verify sanitized 503 errors,
   then restart it. Existing stateless FINANCE chat should still use the deterministic fallback.

GENERAL requires a configured working OpenAI provider, as before. Without one it returns
503 `CHAT_UNAVAILABLE` and saves no messages. FINANCE preserves rule-based fallback when
OpenAI fails or times out. Unsupported financial questions do not invent financial amounts.

## MongoDB schema and indexes

`conversations`:

```javascript
{
  _id: ObjectId("..."), ownerId: "verified-owner-uuid", title: "October budget",
  mode: "FINANCE", month: 10, year: 2026,
  createdAt: ISODate("..."), updatedAt: ISODate("...")
}
```

GENERAL omits month/year. Ownership uses the stable Spring user UUID, while MongoDB
identifiers and references use native ObjectIds. Metadata is immutable except activity time.

`messages`:

```javascript
{
  _id: ObjectId("..."), conversationId: ObjectId("..."), role: "assistant",
  content: "...", provider: "openai", createdAt: ISODate("...")
}
```

Roles are `user` or `assistant`; user rows use provider `user`, assistant rows use
`openai` or `rule-based`. The separate response disclaimer is derived from the immutable
conversation mode when history is read. No financial aggregate snapshot is stored.

Indexes are `{ownerId: 1, updatedAt: -1, _id: -1}` and
`{conversationId: 1, createdAt: 1, _id: 1}`, alongside MongoDB's automatic `_id` indexes.
Equality prefixes scope each query; timestamp and ID suffixes support deterministic sorting,
including reverse scans for recent context. Messages are queried only after owned metadata
is verified. Indexes are not unique: each conversation may have many messages.

Read-only verification (no cleanup against the development database):

```powershell
mongosh 'mongodb://127.0.0.1:27017/personal_finance_ai'
```

```javascript
db.runCommand({ping: 1})
show collections
db.conversations.getIndexes()
db.messages.getIndexes()
db.conversations.find({}, {title: 1, mode: 1, month: 1, year: 1, updatedAt: 1}).sort({updatedAt: -1, _id: -1}).limit(5)
// Substitute the conversationId returned by Bruno:
const conversationId = ObjectId("REPLACE_WITH_24_CHARACTER_HEX_ID")
db.messages.find({conversationId}, {role: 1, provider: 1, createdAt: 1}).sort({createdAt: 1, _id: 1})
db.messages.countDocuments({conversationId})
```

## Error and consistency behavior

Spring retains its `ApiError` envelope (`timestamp`, `status`, `code`, `message`, `path`,
optional field errors). Invalid bodies/IDs/pagination return 400; missing/invalid JWTs
return 401; missing or inaccessible conversations return 404 `CONVERSATION_NOT_FOUND`.
MongoDB failures return 503 `PERSISTENCE_UNAVAILABLE`; GENERAL provider failures return
503 `CHAT_UNAVAILABLE`. Internal transport timeouts return 504 `CHAT_TIMEOUT` with
persistence unconfirmed. Unexpected/malformed upstream responses use the existing sanitized
502 gateway handling. Internal FastAPI validation uses its existing sanitized 422 envelope,
and storage/missing errors use `detail: {code, message}`. Upstream payloads and exception text
are never copied to public error messages.

A local standalone MongoDB cannot atomically commit a multi-document turn. The repository
inserts both rows then updates activity, compensating by deleting only those new row IDs
if any step fails. If MongoDB becomes unreachable, compensation may also fail; a 503 or
504 means persistence is unconfirmed and a partial/complete write may remain. Read history
after recovery before deciding to retry; retries are not idempotent. Concurrent sends may
use the same earlier context; serialize sends per conversation in manual clients. This
milestone introduces neither replica-set transactions nor a distributed conversation queue.

## Automated tests

From the repository root:

```powershell
Push-Location ai-service
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m pytest -q
# Opt in to a real local MongoDB test, using only a random test database:
$env:RUN_MONGODB_INTEGRATION = '1'
try { .\.venv\Scripts\python.exe -m pytest -q } finally { Remove-Item Env:RUN_MONGODB_INTEGRATION }
Pop-Location
Push-Location core-api
.\mvnw.cmd clean verify
Pop-Location
```

If this restricted environment prevents Maven from creating its user-cache file lock,
use `'-Daether.syncContext.named.factory=rwlock-local'` with a single Maven process.
The `-o` flag can be used when dependencies are already cached. This affects build locking,
not application behavior. Tests mock OpenAI and do not spend API credits. Python unit tests
use mongomock; the opt-in test creates and removes only `personal_finance_ai_test_<random UUID>`.
No test deletes or clears `personal_finance_ai`.

Driver reference: [PyMongo connection options](https://www.mongodb.com/docs/languages/python/pymongo-driver/current/connect/connection-options/).

Suggested commit: `feat(chat): persist owned chatbot conversations in local MongoDB`
