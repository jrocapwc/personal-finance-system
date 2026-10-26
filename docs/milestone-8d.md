# Milestone 8D: Angular AI Assistant integration

Implementation date: October 26, 2026 (Asia/Manila).

## Audit and integration decision

Actual projects: Angular `frontend/personal-finance-ui`, Spring Boot `core-api`, FastAPI
`ai-service`. `integration-service` is a separate Node integration service; it is not the
chat transport. The existing main layout already contains one `app-ai-assistant`.

No Spring Boot or FastAPI implementation changes were needed. `ConversationController`,
`ConversationService`, and `ConversationClient` already expose every required operation
through authenticated Spring routes. There are no rename/delete operations, no standalone
GET messages route, and no server idempotency key. The browser never calls FastAPI directly.
Existing route guards, JWT interceptor, dashboard routes, theme toggle, and finance APIs
are retained.

## Actual endpoint inventory

All success responses below are HTTP 200. Routes are literal; `{id}` / `{conversation_id}`
mean a MongoDB ObjectId encoded as 24 hexadecimal characters.

### Spring Boot public API

Every route requires a valid Bearer JWT. Spring verifies the JWT subject UUID and that the
user exists. Missing, invalid, or expired JWTs return 401. Request JSON cannot introduce
owner IDs, financial summaries, or arbitrary fields. Authentication failures use `ApiError`.

| Method | Route                                      | Request                                                                      | Response                                              | Expected operation errors (in addition to 401)                                                |
| ------ | ------------------------------------------ | ---------------------------------------------------------------------------- | ----------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| POST   | `/api/v1/chat/conversations`               | `ConversationRequest` below                                                  | `Conversation`                                        | 400 validation, 502 upstream failure, 503 storage unavailable, 504 timeout                    |
| GET    | `/api/v1/chat/conversations`               | `page=0&size=20` by default; no body                                         | `{items: Conversation[], page: number, size: number}` | 400 invalid query, 502, 503, 504                                                              |
| GET    | `/api/v1/chat/conversations/{id}`          | `page=0&size=100` by default; no body                                        | `ConversationDetails`                                 | 400 invalid ID/query, 404 missing or not owned, 502, 503, 504                                 |
| POST   | `/api/v1/chat/conversations/{id}/messages` | `{question: string}`                                                         | `ChatResponse`                                        | 400 invalid ID/question, 404 missing or not owned, 502, 503 provider/storage unavailable, 504 |
| POST   | `/api/v1/chat/messages`                    | `{question, mode?, month?, year?}`; default mode FINANCE requires month/year | `ChatResponse`; stateless, no persistence             | 400, 502, 503 provider unavailable, 504                                                       |
| GET    | `/api/v1/insights`                         | Required `month`, `year` query parameters                                    | `InsightResponse`                                     | 400, 502, 504                                                                                 |

Conversation query bounds: `page` integer 0..100000, `size` integer 1..100; only `page`
and `size` parameter names are supported. Unsupported methods/media types use the existing
405/415 error handling. Unexpected service failures can also produce a generic server error.

`ConversationRequest` is `{mode: 'GENERAL'|'FINANCE', title?: string, month?: number,
year?: number}`. Mode is required. Omitted title defaults to `New conversation`; title
is trimmed, 1..100 characters. GENERAL must omit month/year. FINANCE requires month 1..12,
year 2000..2100. Conversation mode and financial period cannot be changed after creation.

`Conversation` is `{id: string, mode: 'GENERAL'|'FINANCE', title: string, month?: number,
year?: number, createdAt: string, updatedAt: string}`. Dates are serialized datetimes.
GENERAL metadata omits month/year. Owner ID is deliberately excluded from responses.

`ConversationDetails` adds `{messages: ChatMessage[], page: number, size: number,
disclaimer: string}` to metadata. `ChatMessage` is `{id: string, conversationId: string,
role: 'user'|'assistant', content: string, provider: 'user'|'openai'|'rule-based',
createdAt: string}`. User entries have provider `user`; assistant entries have the actual
provider. There is no total count or has-next field in either page response.

Questions are trimmed and must be 1..1000 characters. `ChatResponse` is
`{answer: string, provider: 'openai'|'rule-based', disclaimer: string}`; answer length
is 1..8000. It contains no conversation/message IDs or message timestamps, so Angular
reloads persisted history after a successful send rather than fabricating those values.

`ApiError` is `{timestamp: string, status: number, code: string, message: string,
path: string, fieldErrors: Record<string,string>}`. Conversation errors include
`INVALID_REQUEST`, `CONVERSATION_NOT_FOUND`, `PERSISTENCE_UNAVAILABLE`, `CHAT_UNAVAILABLE`,
and `CHAT_TIMEOUT`; validation can use `VALIDATION_FAILED`. Generic upstream errors use
`INSIGHTS_UNAVAILABLE` / `INSIGHTS_TIMEOUT` from the existing global handler. The UI
uses status and the allowlisted `CHAT_UNAVAILABLE` code, never arbitrary server text.

`InsightResponse` is `{summary: string, positiveObservations: string[], concerns: string[],
recommendations: string[], suggestedActions: string[], disclaimer: string}`.

### FastAPI internal API

All routes below require `X-Internal-API-Key`; an invalid or missing key returns 401.
Conversation routes additionally require `X-Owner-Id`, supplied by Spring from its verified
JWT identity. A malformed owner UUID returns 400; a missing header returns 422. FastAPI
does not authenticate browser JWTs. It has no browser CORS integration; keep its internal
listener inaccessible to untrusted callers. No CORS changes or public identity headers
were introduced.

| Method | Route                                                   | Request                                                                      | Response                  | Expected operation errors (in addition to 401)                                                  |
| ------ | ------------------------------------------------------- | ---------------------------------------------------------------------------- | ------------------------- | ----------------------------------------------------------------------------------------------- |
| POST   | `/api/v1/chat/conversations`                            | Same conversation creation JSON                                              | `Conversation`            | 400 owner, 422 validation, 503 MongoDB                                                          |
| GET    | `/api/v1/chat/conversations`                            | Same pagination, default 0/20                                                | `{items, page, size}`     | 400 owner, 422 pagination/header, 503 MongoDB                                                   |
| GET    | `/api/v1/chat/conversations/{conversation_id}`          | Same pagination, default 0/100                                               | `ConversationDetails`     | 400 ID/owner, 404 ownership/missing, 422, 503 MongoDB                                           |
| POST   | `/api/v1/chat/conversations/{conversation_id}/messages` | `{question: string, financialSummary?: FinancialSummary}`                    | `ChatResponse`            | 400 owner/ID/mode-summary mismatch, 404 ownership/missing, 422 validation, 503 provider/storage |
| POST   | `/api/v1/chat/respond`                                  | `{question, mode?, month?, year?, financialSummary?}`; mode defaults FINANCE | `ChatResponse`, stateless | 422 validation, 503 provider                                                                    |
| POST   | `/api/v1/insights/analyze`                              | `FinancialSummary`                                                           | `InsightResponse`         | 422 validation                                                                                  |

FastAPI conversation sends derive mode/period from owned metadata. GENERAL forbids
financialSummary; FINANCE requires a summary matching the stored month/year. Spring maps
upstream 422/400 to public 400, preserves ownership 404, allowlists 503 codes, maps transport
timeouts to 504, and hides arbitrary upstream responses behind 502. FastAPI errors use
`{detail: {code,message}}` for domain failures, `{detail: [{loc,msg,type}, ...]}` for
validation, and `{detail: string}` for internal-key authentication failure. Rejected
request values are not included in validation responses.

`FinancialSummary` fields are `month`, `year`, `totalIncome`, `totalExpenses`, `netSavings`,
`savingsRate` (decimal string or null), `expensesByCategory` (`categoryId`, `categoryName`,
`amount`), and `budgetUtilization` (`categoryId`, `monthlyLimit`, `spentAmount`,
`alertThreshold`). Money/rate values use decimal strings, IDs and period use integers.
The backend enforces financial consistency and a 32 KiB chat-summary limit.

## Request flow and MongoDB persistence

1. Angular HttpClient calls same-origin `/api/v1/...`. The existing interceptor adds the
   in-memory JWT. Development `proxy.conf.json` targets Spring on localhost:8080.
2. Spring derives ownership from authentication; no browser user ID is accepted as proof.
3. Spring reads owned conversation metadata before sending. For FINANCE only, it calls
   DashboardService for fresh authorized relational aggregates for the stored period.
4. ConversationClient calls FastAPI with the internal key and trusted owner UUID.
5. FastAPI filters conversation access by both ObjectId and ownerId. It retrieves at most
   12 previous persisted messages for AI context. Context is treated as untrusted text.
6. The existing chatbot returns a real provider response or FINANCE rule-based fallback.
   GENERAL returns 503 if its provider cannot answer; no fabricated general response exists.
7. MongoDB saves user and assistant messages and updates conversation activity before
   acknowledging success. Angular then fetches all chronological message pages.

MongoDB collections are `conversations` and `messages`. Conversations include ownerId,
mode, title, optional period, and timestamps. Messages include ObjectId conversationId,
role, content, provider, and createdAt. Conversation list order is updatedAt descending,
then ObjectId descending. History order is createdAt ascending, then ObjectId ascending.
Indexes support both orders. Synchronous PyMongo operations run in worker threads.

Standalone MongoDB has no multi-document transaction here. The existing repository uses
ordered insert_many and best-effort compensation on failure. An outage can leave an
ambiguous partial write; the UI does not interpret a 503 or timeout as proof of no save.

## Implemented behavior and state

- One bottom-right floating assistant, using the shared light/dark surface/text variables.
- Close/reopen preserves the current authenticated session's draft and messages.
- New Chat begins a fresh local draft; the server conversation is created on the first send.
- Explicit GENERAL/FINANCE mode buttons. Switching modes begins a fresh conversation.
- New FINANCE chats let the user choose a month. Existing chats show their immutable period.
- Titles use the first 100 characters of the initial question through the real title field.
- History shows titles, modes, periods, and activity timestamps, with load-more pagination.
- Existing chats load every message page before allowing further sends.
- Safe text interpolation, separate bubbles, actual persisted timestamps, automatic scroll,
  Enter to send, Shift+Enter newline, and IME composition protection.
- Pending state, loading/empty states, sanitized error messages, refresh controls, and
  backend-provided disclaimers.
- Signals hold view state; RxJS composes create/send/read operations. Selection changes
  cancel history requests. Generation and session checks ignore obsolete responses.
- A pending write blocks another send, new chat, mode changes, and conversation selection.
  Closing the panel and viewing its history remain possible while a write completes.
- AuthService now emits synchronous session-change notifications. Logout, JWT expiry, and
  login replacement clear private state and cancel pending chat/list requests immediately.
  Existing 401 handling still logs out only the relevant JWT session.
- GET requests disable Angular SSR transfer caching. No token, chat content, or draft is
  written to localStorage/sessionStorage; the theme preference remains unchanged.

FINANCE supports educational expense, budget, and savings explanations from the selected
month's aggregate only. No complete datasets are sent by Angular, no client-side financial
analysis was added, and the AI cannot access arbitrary transactions, execute actions, or
infer historical comparisons. The existing educational disclaimer is displayed after
history retrieval or a response. Provider configuration and financial fallback are retained.

## Failure and retry policy

Validation/access errors and explicit CHAT_UNAVAILABLE responses preserve the draft and
permit a deliberate retry when appropriate. There are no automatic write retries.

After an ambiguous send (network error, 502, storage 503, or 504), the assistant reloads
history. If new history contains the submitted user question followed by an assistant
message, it recovers the saved turn and clears the draft. Otherwise it preserves the draft,
blocks further sends to that conversation, and offers another refresh or New Chat. The
write guard is retained when selecting another conversation and returning in the same
session. Failure of reconciliation does not release the guard.

After a confirmed send whose history reload fails, the draft is cleared because the
backend acknowledged persistence. Further sends wait for a successful history refresh.
A failed conversation-creation request preserves text and warns that creation may have
completed; the user should check history before creating another conversation.

## Files created or modified

Created:

- `frontend/personal-finance-ui/src/app/core/models/chat.model.ts`
- `frontend/personal-finance-ui/src/app/core/services/ai-chat.ts`
- `frontend/personal-finance-ui/src/app/core/services/ai-chat.spec.ts`
- `frontend/personal-finance-ui/src/app/shared/components/ai-assistant/ai-assistant.spec.ts`
- `docs/milestone-8d.md`

Modified:

- `frontend/personal-finance-ui/src/app/core/services/auth.ts`
- `frontend/personal-finance-ui/src/app/shared/components/ai-assistant/ai-assistant.ts`
- `frontend/personal-finance-ui/src/app/shared/components/ai-assistant/ai-assistant.html`
- `frontend/personal-finance-ui/src/app/shared/components/ai-assistant/ai-assistant.scss`

No backend source, routes, finance services, database schema, or dependencies were changed.

## Verification

The following checks were executed against the final implementation. The new tests use
Angular HttpTestingController, the actual chat service, the actual auth service, and the
existing JWT interceptor; they do not call a paid AI provider.

- Angular: `npm test -- --watch=false`: **206 passed across 16 test files**, no unhandled errors. New chat specs add 32 cases; existing auth/finance/dashboard tests are included.
- Angular: `npm run build`: **passed**, browser and SSR bundles generated, six routes prerendered; initial browser bundle 343.27 kB. Existing `dashboard.scss` size warning remains (6.50 kB versus 4 kB warning budget). No assistant stylesheet warning.
- Spring: `./mvnw.cmd '-Dtest=ConversationClientTests,ConversationIntegrationTests,ChatIntegrationTests' test`:
  **42 passed, zero failures/errors**. Includes JWT requirement, trusted owner handling,
  cross-user 404, invalid requests, upstream failures, and finance/general behavior.
- FastAPI: `.venv/Scripts/python.exe -m pytest tests/test_conversations.py tests/test_chat.py tests/test_chat_configuration.py`:
  **87 passed, 1 skipped**. Includes repository ownership, persistence/compensation,
  context handling, mode validation, provider fallback/timeouts, and storage outages.
  The skipped test requires `RUN_MONGODB_INTEGRATION=1` and a reachable local MongoDB.
  A read-only ping to localhost:27017 timed out in this execution environment. Pytest also
  reported a cache-write permission warning; test assertions passed.

The first Angular test run had all assertions pass but exited with two unhandled routing
errors in the new logout tests. Their router navigation is now mocked consistently with
existing auth tests. The subsequent suite passed cleanly. Initial sandboxed Angular and
Maven commands hit child-process/cache restrictions and were rerun with approved access.

## Runtime setup and known limitations

Follow `docs/milestone-7d.md` for existing MongoDB/FastAPI/Spring startup and secret setup.
GENERAL needs the existing server-side OpenAI configuration. Never put provider or internal
keys into Angular. Use matching internal keys for Spring and FastAPI. Start Angular from
`frontend/personal-finance-ui` with `npm start`. Production hosting must route same-origin
`/api` to Spring, as already required for authentication and finance.

Spring defaults: connect timeout 2s, read timeout 5s; configurable through
`AI_SERVICE_CONNECT_TIMEOUT` and `AI_SERVICE_READ_TIMEOUT` (bounded at 60s). The previous
conversation milestone recommends a 20s read timeout to allow provider and persistence
work. FastAPI's provider timeout defaults to 3s and is configurable up to 30s. PyMongo
has existing bounded selection/connect/socket/operation timeouts. No timeouts were changed.

- There is no end-to-end idempotency guarantee: the API lacks a turn identifier. The
  reconciliation guard is conservative and held in memory for the current session only.
  Concurrent tabs/clients can affect matching and message order. Inspect saved history
  after a browser reload or interrupted session before manually repeating a question.
- A partial saved turn without an assistant message remains blocked for this session;
  this UI cannot repair MongoDB writes. New Chat remains available.
- Closing preserves state; logging out/reloading intentionally clears local state.
- History uses offset pagination, not a snapshot. Concurrent activity can shift pages;
  Refresh history reloads the first page in current backend order.
- Rename/delete, streaming, and client-side Markdown are absent because no existing
  established contract requires them. Very long histories are loaded in full and may
  eventually warrant a backend cursor and incremental UI.
- Automated DOM tests do not verify physical layout or on-screen keyboards. Browser
  checks for both themes, mobile sizing, and a real provider/MongoDB round trip remain
  manual. No full live-stack browser success is claimed.

## Manual verification checklist (not yet executed)

- [ ] Start MongoDB, FastAPI, Spring, Angular; sign in as User A.
- [ ] Open/close assistant; ensure one floating button remains at bottom-right.
- [ ] GENERAL: send a question and verify a real response, timestamps, and disclaimer.
- [ ] FINANCE: select a month with known data; verify its spending/budget/savings context.
- [ ] Mutate a budget/transaction, then ask again; verify fresh server-side context.
- [ ] New Chat and mode switching start fresh conversations; old mode/period remain intact.
- [ ] Refresh history, select a prior chat, see saved messages, and continue it.
- [ ] Reload/sign in again; retrieve the same MongoDB conversation from history.
- [ ] Exercise empty history, empty messages, loading states, and multi-page history.
- [ ] Enter sends once; Shift+Enter inserts newline; repeated sends do not duplicate turns.
- [ ] While sending, attempt New Chat/mode/selection; verify these are blocked.
- [ ] Switch between chats while a history read is pending; only the latest selection appears.
- [ ] Stop FastAPI or MongoDB; verify clear errors and preserved drafts.
- [ ] Cause a timeout after dispatch; verify history reconciliation and no automatic resend.
- [ ] Restore services; refresh messages to recover a saved response or start a new chat.
- [ ] Log out during a pending send/read; verify immediate cancellation and state clearing.
- [ ] Let JWT expire or submit with an invalid JWT; verify login redirect and cleared state.
- [ ] Sign in as User B; A's titles/messages never appear. Replay A's ID with B's JWT:
      GET and POST messages must return 404. Requests without JWT must return 401.
- [ ] Light/dark: check bubble, field, placeholder, border, error, and timestamp contrast.
- [ ] Mobile (320px, 375px, portrait/landscape): no horizontal overflow; composer remains
      usable with the keyboard; history/messages scroll independently of the dashboard.
- [ ] Repeat all ten existing Finance Management checks; verify charts, mutations,
      sidebar navigation, logout, and theme toggle still work.

Recommended commit: `feat(ai): integrate authenticated assistant with persisted conversations`
