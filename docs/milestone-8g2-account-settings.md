# Milestone 8G.2: Angular Account Settings

## Verified backend contracts

The actual Core API implementation supports all required account operations. No backend source, security settings, AI service, integration service, or database code was changed in this milestone.

| Endpoint | Contract used by Angular |
| --- | --- |
| `GET /api/v1/users/me` | Existing `UserResponse`: id, name, email, createdAt, updatedAt |
| `PUT /api/v1/users/me` | Request `{name}`; 200 with the same `UserResponse` |
| `PUT /api/v1/users/me/password` | Request `{currentPassword,newPassword}`; 204 with no body |

A password change revokes all existing tokens, clears the current authentication cookie, and rotates the readable CSRF cookie. Incorrect current passwords and password reuse both produce the safe 400 `INVALID_PASSWORD_CHANGE` error. There is no backend contract that distinguishes these two causes; the UI explains both without claiming the current password alone was wrong. No backend requirements are missing.

## Integration and behavior

`/account-settings` is lazy loaded under the existing authenticated layout and its parent/child auth guards. Its server route uses client rendering, as do other private pages. Clicking the current user's name or avatar in the top toolbar opens Account Settings; on mobile the avatar remains visible and clickable. The profile link has an accessible label, keyboard focus, and active-route feedback. Account Settings is omitted from the sidebar. Existing mobile navigation closes the drawer after selection. The toolbar title recognizes the route, and the existing sign-in return destination allowlist includes it.

The existing `AuthService` owns all current-user state and account HTTP calls. Profile loads and updates write to its current-user signal only if the session revision and user ID still match, preserving the expiration timer and finance caches. Header names and initials update through their existing computed signals; no duplicate profile or authentication store is introduced.

The standalone page uses typed reactive forms, signals, and `takeUntilDestroyed`. Profile loading has a loading view and a retryable error view. Editing supports Save/Cancel, trimmed nonblank names up to 100 characters, submission locks, and success/error feedback. Email and creation date are read-only.

Password fields have independent accessible visibility buttons and browser password-manager autocomplete attributes. Validation reuses the registration form's `TextEncoder` UTF-8 byte validator, nonblank validator, and 12-character minimum. TextEncoder is available in the supported browser and Node runtimes; the validator does not access browser globals. Group validation checks password reuse and confirmation. Only currentPassword and newPassword reach the backend.

On 204, the form is cleared, visibility is reset, a Material snackbar explains that a new sign-in is required, and `AuthService` clears session state, expiry timers, and pending private data through its existing session-change stream. Other tabs receive the existing session notification. No redundant logout request is made after the backend already clears the cookie. Destruction or session invalidation also clears password controls.

All new requests use relative `/api/v1/...` URLs through the existing HttpClient and interceptor. Same-origin cookie credentials are sent by the browser; no JWT is read, stored, or sent in an Authorization header. The current Angular XSRF configuration reads `XSRF-TOKEN` and attaches `X-XSRF-TOKEN`. The existing interceptor bootstraps a missing readable token before submitting a mutation. HTTP 403 is shown with a refresh instruction; an already-submitted password change is never automatically retried. Production continues to require HTTPS and a same-origin API reverse proxy; local development uses `localhost:4200` and the existing Angular `/api` proxy.

The shared interceptor handles 401 responses through the existing session-expiration flow and preserves the intended return route. The page shows fixed safe messages for 400, 403, 404, 409, 500, and connection errors. Raw backend messages, rejected values, passwords, hashes, and stack traces are never rendered or logged.

Cards, text, borders, errors, success feedback, inputs, and actions use existing semantic theme tokens. Desktop uses two columns and smaller screens stack the cards. Long profile values wrap, controls have associated labels and error descriptions, and mobile bottom spacing allows the last action to scroll above Ask Yaman Ai.

## Files

Created:

- `frontend/personal-finance-ui/src/app/features/account-settings/account-settings.ts`
- `frontend/personal-finance-ui/src/app/features/account-settings/account-settings.html`
- `frontend/personal-finance-ui/src/app/features/account-settings/account-settings.scss`
- `frontend/personal-finance-ui/src/app/features/account-settings/account-settings.validators.ts`
- `frontend/personal-finance-ui/src/app/features/account-settings/account-settings.spec.ts`
- `frontend/personal-finance-ui/qa/test_account_settings_browser.py`
- This report.

Modified:

- `src/app/core/services/auth.ts` and `auth.spec.ts`: account requests, shared state, sign-out after password change, safe return destination, stale-response tests.
- `src/app/app.routes.ts` and `app.routes.server.ts`: authenticated route and client rendering.
- `src/app/app.spec.ts`: route protection, shared layout/header, and theme regression tests.
- `src/app/layouts/main-layout/main-layout.html` and `main-layout.ts`: navigation and toolbar title.
- `public/ui-icons.svg`: local account settings icon.

The paths in the modified list are relative to `frontend/personal-finance-ui`. Existing backend/Bruno changes from earlier work are left intact.

## Repeatable verification

From `frontend/personal-finance-ui`, with Node 24 LTS on PATH:

```powershell
npm test -- --watch=false
npm run build
```

Browser tests reuse the existing QA harness with isolated H2 databases, local Spring and Node services, and mocked AI providers. They never connect to a development database or a live OpenAI provider. Package the existing Core API before running them (`./mvnw.cmd -DskipTests package` from `core-api`; no backend source changes).

From the repository root, using the existing QA Python environment and Playwright installation:

```powershell
$env:RUN_BROWSER_E2E = '1'
$env:RUN_BACKEND_E2E = '1'
$env:PYTHONPATH = (Join-Path (Get-Location) '.tools/browser-qa')
ai-service/.venv/Scripts/python.exe -m pytest frontend/personal-finance-ui/qa/test_account_settings_browser.py qa/test_cookie_browser.py qa/test_backend_e2e.py -q --tb=short
```

New browser checks cover profile load/update, header state, validation, real current-password rejection, password change, cookie clearing, sign-in with old/new passwords, other-tab sign-out, missing-session restoration, API errors, both themes, and mobile navigation/overflow. Existing browser and backend checks cover login/registration/logout, session restoration, CSRF, dashboard, transactions/categories/budgets, demo bank connections and synchronization, CSV review/import, Ask Yaman Ai and conversation history, and theme persistence.

Screenshots are written to the ignored `frontend/personal-finance-ui/tmp/account-settings` directory. Test/build logs are ignored `account-settings-*.log` files in the frontend workspace.

## Executed results — October 26, 2026

- Angular unit suite: **19 files, 272 tests passed**, including the existing authentication, finance, bank, AI, and session tests.
- Angular production build: **passed**, native exit code 0. Two public routes were prerendered; Account Settings remains client rendered. Existing component-style warning budgets were exceeded by dashboard (6.75 kB), bank connections (7.44 kB), and AI assistant (5.16 kB); the new account page did not trigger a budget warning. No budgets or unrelated styles were changed.
- Eight distinct live browser/HTTP workflows have successful results across the initial run and focused reruns. The initial combined run passed six and timed out in the new two-tab password workflow and the existing complete-backend workflow. A focused rerun passed the account error-handling workflow and complete-backend workflow. The remaining account workflow passed after the browser test explicitly brought the submitting tab to the foreground: **1 passed in 38.98 seconds**. This was a test focus adjustment, with no password retry added to the application.
- Desktop and 390px mobile screenshots were inspected in both themes. Browser assertions verified theme colors and mobile document width, profile persistence/header updates, real password rejection/change, matching CSRF headers, absence of Bearer credentials, cookie removal, other-tab sign-out, rejection of old-password login, new-password login, and restoration after reload.
- Final diff checked with CRLF-aware whitespace validation. Existing file line endings and unrelated backend/Bruno work were preserved.

Suggested commit: `feat(frontend): add account settings and password management`.
