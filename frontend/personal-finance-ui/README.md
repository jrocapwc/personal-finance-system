# FinanceAI Angular frontend

## Milestone 8C - Live finance dashboard

The Option A dashboard now displays authenticated Spring Boot financial data. It uses only
`GET /api/v1/dashboard?month={month}&year={year}` and `GET /api/v1/categories`.
No backend endpoints, authentication implementation, theme service, layout shell or AI Assistant
source were changed. There are no CRUD pages and no demonstration-data fallback on failures.

### Services, contracts and state

- `DashboardService.getMonthly()` and `CategoryService.list()` are thin, stateless HttpClient
  wrappers with relative URLs. The existing functional JWT interceptor supplies authorization.
- TypeScript contracts mirror the Java dashboard/nested summary, category breakdown, daily trend,
  transaction, budget and category response records. Java BigDecimal fields arrive as JSON numbers;
  LocalDate values remain date strings and Instant values remain timestamp strings. `ApiError`
  describes the shared backend error shape. Currency is the existing PHP application convention,
  since the API does not return a currency field.
- `DashboardStore` is provided by the dashboard component, not globally. Signals expose the
  selected period and load state. RxJS `switchMap` cancels superseded period/session requests;
  explicit session-reference and period checks also reject responses racing before cancellation.
  Visible finance data clears immediately on a period change or logout/session change. Feature
  destruction cancels pending requests. There is no persistent finance cache.
- Monthly and category requests run independently of historical requests. A normal load makes
  six unique monthly dashboard requests plus one category request, reusing the selected month's
  response in the six-month chart. Current data renders without waiting for history. Historical
  failures show N/A rather than zero and do not erase the selected month's successful data.
- Finance requests are disabled on the server, even if an auth state is supplied in a server test.
  Both HTTP services also disable Angular transfer caching. Authenticated records are not prerendered.

### Display behavior

- Four cards show Monthly Income, Monthly Expenses, Net Savings and Savings Rate. Monetary values
  use PHP formatting. A null savings rate displays Unavailable. There is no Total Balance card.
- The reporting period is explicit. Month/year controls support the backend's 2000-2100 range.
  Refresh and retry reload data; loading, empty, category-lookup and API-failure states are distinct.
- The donut uses actual category amounts. Integer-cent/BigInt largest-remainder allocation makes
  displayed percentages total 100.00%. Valid backend hex colors are used; absent/invalid colors
  use a safe local palette. Zero expenses produce an empty ring and an explanatory message.
- Recent transactions come from the selected dashboard response, limited by the backend to five
  within that month. Category IDs map through the category lookup, with reporting breakdown names
  as a secondary source. Unknown IDs are explicitly labeled as unavailable. Expense amounts are
  negated only for presentation. Date-only transactions are formatted using explicit UTC midnight
  so local timezone conversion cannot move them to the previous calendar day.
- Budget utilization comes directly from the dashboard response. Actual percentages, remaining
  amounts and SAFE/WARNING/EXCEEDED statuses are retained. Bar width and ARIA numeric range are
  clamped to 100%; ARIA value text and visible text retain the real percentage, including overspending.
- The six-month chart ends at the selected month and handles year rollover. Axis scales, labels,
  donut stops and accessible descriptions use real response values. Zero-value bars have zero height.

### Verification - October 26, 2026

- `npm test -- --watch=false`: **85 tests passed across 8 files**.
- `npm run build`: **passed**, including browser/server bundles and three prerendered routes.
- The existing dashboard component-style warning remains: 6.50 kB against the 4 kB warning
  budget, below the 8 kB error budget. Budgets were not raised.
- Headless Chrome verified the live Angular proxy against the existing Spring Boot jar with
  Java 25, a temporary signing key, an isolated in-memory H2 database and disposable test accounts.
  No stored finance database was touched. Checks passed for real API totals, decimal/negative
  values, overspending, exact request count, month/year rollover, historical failure isolation,
  current-month failure with no fallback, retry, themes, mobile overflow, AI controls, logout,
  an empty second account and reload requiring login. No browser runtime/hydration errors occurred.
  Desktop light and mobile dark screenshots were inspected.
- Tests cover exact authenticated requests, null/zero/negative summaries, category mapping,
  percentage rounding/colors, dynamic charts, unsupported periods, API failures, cancellation,
  responses racing period/logout changes, session replacement, SSR suppression, budget progress,
  calendar dates and shared theme compatibility.

### Limits and prerequisites

- The existing JSON-number contract is retained. Money outside +/-PHP 1,000,000,000,000 or
  nonfinite numeric values is rejected with an explicit display-range error instead of being
  presented with potentially incorrect cents. The backend supports a larger decimal range;
  supporting all of it needs a future lossless numeric transport/parse strategy. No financial
  arithmetic is substituted for the server's authoritative summaries or budget calculations.
- Months before January 2000 are marked unavailable without sending invalid requests. A selected
  period near that boundary therefore has fewer than six supported historical months.
- Category lookup failure leaves the dashboard usable with available breakdown names or an
  explicit unknown-category label. Retry reloads the lookup and monthly reports.
- PHP remains the single display currency. No account balances, currency conversion, refresh-token
  flow, mutation pages, or AI message-sending integration was added.
- Run Spring Boot and Angular using the instructions below. The development proxy still targets
  port 8080. Production still requires a same-origin reverse proxy for `/api/**`.

### Manual verification checklist

1. Start Spring Boot and Angular, sign in, and confirm the reporting month/year.
2. Compare the four cards, category totals, five recent transactions and budgets with the selected
   user's `/api/v1/dashboard` response; verify that Total Balance and sample labels are absent.
3. Choose January and confirm the chart includes the preceding year; change periods rapidly
   and confirm old results do not flash into the new reporting period.
4. Check a month with no transactions, one with expenses but zero income, and one with negative
   savings. Null savings rate must remain Unavailable.
5. Confirm decimal peso amounts and calendar dates. Donut legend percentages should sum to
   100.00% when expenses exist. Check a category without a color.
6. Check SAFE, WARNING and EXCEEDED budgets, including >100% usage and negative remaining money.
7. Simulate one historical request failure: current-month data must remain visible and the
   failed month must read N/A. Retry after restoring connectivity.
8. Stop the backend and refresh: show an API error and Retry, with no fabricated fallback data.
   Restart it and retry successfully.
9. Check desktop/mobile layouts, both themes, theme persistence, and AI Assistant open/close
   plus GENERAL/FINANCE switching.
10. Log out during a pending load. Sign in as a different user and confirm no previous user's
    values remain. Reload while signed in and confirm the existing login requirement.

### Files for Milestone 8C

Created (relative to this Angular directory):

```text
src/app/core/models/api-error.model.ts
src/app/core/models/category.model.ts
src/app/core/models/dashboard.model.ts
src/app/core/services/category.ts
src/app/core/services/dashboard.ts
src/app/core/services/finance-api.spec.ts
src/app/features/dashboard/dashboard-controls.scss
src/app/features/dashboard/dashboard.mapper.ts
src/app/features/dashboard/dashboard.mapper.spec.ts
src/app/features/dashboard/dashboard.store.ts
src/app/features/dashboard/dashboard.store.spec.ts
src/app/features/dashboard/dashboard.spec.ts
src/app/features/dashboard/dashboard.testing.ts
```

Modified:

```text
README.md
src/app/app.spec.ts
src/app/features/dashboard/dashboard.ts
src/app/features/dashboard/dashboard.html
src/app/features/dashboard/dashboard.scss
```

The pre-existing change to `core-api/bruno/Authentication/03 Login.bru` was left untouched.
No dependencies were added.

Suggested commit message:

```text
feat(dashboard): integrate authenticated monthly finance reporting
```

## Milestone 8B baseline notes

The following records the authentication milestone. The 8C behavior and verification above
supersede its earlier dashboard demo-data status and test counts.

Milestone 8B adds authentication to the existing Option A dashboard. The actual app directory
is `frontend/personal-finance-ui`; there is no second nested `personal-finance-ui` folder.
The Spring Boot and FastAPI source code and the dashboard/AI Assistant components are unchanged.

## Architecture and API flow

The root component owns the shared theme wrapper and router outlet. `ThemeService` preserves
`finance-theme` in localStorage and restores it after rendering to avoid hydration differences.
Only the theme preference is persisted.

- `AuthLayout` renders a centered, responsive login/registration card and theme toggle.
- `MainLayout` contains the existing sidebar, toolbar, dashboard outlet and floating AI Assistant.
  The toolbar shows the authenticated name, exposes the email as a title and provides Logout.
- Login, registration, layouts and dashboard are lazy-loaded standalone components.
- Functional `authGuard` protects the main layout and its children. `guestGuard` sends authenticated
  visitors from login/registration to the dashboard. Root and unknown URLs resolve via dashboard.
- Forms use Reactive Forms, accessible labels, validation, visibility controls, loading states and
  safe error messages. Registration checks a nonblank name, email, 12-character password minimum,
  72-byte UTF-8 password maximum and matching confirmation. Passwords are never trimmed.

Registration sends **only** `{ name, email, password }` to `POST /api/v1/auth/register`.
HTTP 201 redirects to `/login?registered=true` with a success notice; it does not authenticate.
HTTP 409 displays an email-already-registered message. `confirmPassword` is never sent.
The existing `auth.model.ts` matches the Java request, login response and user response records.

Login sends `{ email, password }` to `POST /api/v1/auth/login`. The service retains `accessToken`
and the returned user in memory, tracks the deadline using `expiresIn`, and exposes a readonly
current-user signal. Successful login navigates to `/dashboard`; HTTP 401 displays an incorrect
credentials message. Failed requests leave no authenticated session.

The functional interceptor sends bearer credentials only to same-origin `/api/v1/` URLs,
including normalized absolute URLs. It excludes login and registration, other origins and
non-API requests. API 401 responses clear the matching active session; 403 leaves it intact.
A timer clears the session at expiry, and token access checks the clock again in case the
browser suspended timers. Logout clears the timer, token and user and navigates to `/login`.
An in-flight login cannot restore a session after logout. There are no refresh or revocation calls.
Client route guards are for navigation; Spring Security remains responsible for API authorization.

## Run Angular and Spring Boot

Use separate PowerShell terminals, starting at the repository root. Install the frontend's locked
dependencies with `npm ci` if needed. Configure Java 25 through `JAVA_HOME` and the backend prerequisites in the
[root README](../../README.md). The local Spring profile uses the existing H2 database and Flyway.
Do not reset it.

Terminal 1 (preserve an existing signing key if already configured):

```powershell
if (-not $env:JWT_SECRET_BASE64) {
  $jwtBytes = New-Object byte[] 32
  $jwtRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
  try { $jwtRandom.GetBytes($jwtBytes) } finally { $jwtRandom.Dispose() }
  $env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtBytes)
  [Array]::Clear($jwtBytes, 0, $jwtBytes.Length)
}
Set-Location core-api
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

Terminal 2:

```powershell
Set-Location frontend/personal-finance-ui
npm start
```

Open `http://localhost:4200/login`. Register a test account, then sign in. No real credentials
are embedded in source or tests. FastAPI and the integration service are not required for auth;
see the root README to run those separately.

The root README confirms Spring's default port 8080, and repository properties do not override
it. `angular.json` enables `proxy.conf.json`; `/api/**` forwards to `http://localhost:8080`.
If you explicitly override Spring's port, update this proxy target. The wildcard covers nested
API routes with Angular's Vite development server. Requests use relative API paths.

For production, configure a same-origin reverse proxy for `/api/**` to Spring Boot. Angular's
**development** proxy is not included in the built Express SSR server. Keep the built application's
API on the same public origin so the interceptor can scope credentials correctly.

## Verification results - October 25, 2026

```powershell
npm test -- --watch=false
npm run build
```

- Angular Vitest: **46 passing tests across 4 files**.
- Production build: **passed**, with browser/server bundles and prerendered routes.
- Existing warning: `dashboard.scss` is 6.57 kB against its 4 kB warning budget; below the
  8 kB error budget. The dashboard stylesheet was not changed.
- Headless Chrome: passed SSR/hydration without console errors, desktop light/dark login,
  mobile auth layout at 390px, registration success notice, login, authenticated dashboard,
  four cards, AI panel/mode switching, logout and theme persistence on reload. Screenshots
  of desktop login and mobile registration were inspected.
- Browser API checks first used an isolated temporary mock **through the real Angular development
  proxy**, then passed against the existing Spring Boot jar using Java 25, a temporary signing key
  and an isolated in-memory H2 database. Live checks covered HTTP 201 registration, JWT login,
  HTTP 409 duplicate email, HTTP 401 invalid credentials, dashboard/profile, logout and login
  required after reload. No stored finance data or backend source was changed. Unit tests mock HTTP.
- The execution host reports Node 25.8.0, which prints an odd-version/non-LTS warning.

### Milestone 8B checklist

| # | Verification | Result |
| --- | --- | --- |
| 1 | Login renders | Unit and Chrome passed |
| 2 | Registration renders | Unit and Chrome passed |
| 3 | Login validation | Unit passed |
| 4 | Registration name/email/password boundaries | Unit passed |
| 5 | Password confirmation and original-password edits | Unit passed |
| 6 | UTF-8 72-byte boundary, including multibyte/emoji passwords | Unit passed |
| 7 | Login updates session and user | Unit and live Spring Boot/Chrome passed |
| 8 | Invalid login error | Unit and live Spring Boot/Chrome passed |
| 9 | Duplicate registration error | Unit and live Spring Boot/Chrome passed |
| 10 | Registration redirects with success notice, without authentication | Unit and Chrome passed |
| 11 | Unauthenticated protected routes redirect | Unit and Chrome passed |
| 12 | Authenticated dashboard access | Unit and Chrome passed |
| 13 | Bearer restricted to own API; public/third-party paths excluded | Unit passed |
| 14 | Timer expiry and suspended-browser clock checks clear state | Unit passed |
| 15 | Logout clears session, redirects and blocks later dashboard access | Unit and Chrome passed |
| 16 | Shared light/dark theme and saved preference | Unit and Chrome passed |
| 17 | Dashboard cards, responsive shell and AI Assistant controls preserved | Unit and Chrome passed |
| 18 | Production Angular build including SSR/prerender | Passed |
| Live integration | Registration/login/errors/logout/reload against isolated Spring Boot | Passed |

## Limitations and deployment prerequisites

- Reloads, a new tab or browser restart require login because JWTs are intentionally memory-only.
  There is no secure backend session-restoration/refresh mechanism being used.
- Logout ends the client session. A previously issued JWT remains valid on the server until its
  normal expiry because the backend has no revocation endpoint.
- Financial data remains the existing dashboard demo data. The AI Assistant's open/close and
  GENERAL/FINANCE controls work; sending messages remains disabled as it was before this milestone.
- Production requires HTTPS and the same-origin API reverse proxy described above.
- To verify the live backend, start both services, create a disposable test account, sign in,
  check the displayed name, log out and confirm `/dashboard` redirects. Repeat in both themes,
  then reload while signed in and confirm a new login is required.

## Actual files created, updated and reused

Paths below are relative to this Angular directory. Some auth scaffolding and HttpClient/proxy
work was already uncommitted when implementation started; it was extended rather than replaced
with a second implementation.

Updated tracked files:

```text
README.md
angular.json
src/app/app.ts
src/app/app.html
src/app/app.scss
src/app/app.routes.ts
src/app/app.config.ts
src/app/app.spec.ts
```

Updated existing untracked scaffolding:

```text
proxy.conf.json
src/app/core/services/auth.ts
src/app/features/auth/login/login.ts
src/app/features/auth/login/login.html
src/app/features/auth/login/login.scss
src/app/features/auth/register/register.ts
src/app/features/auth/register/register.html
src/app/features/auth/register/register.scss
```

Created:

```text
src/app/core/auth.testing.ts
src/app/core/guards/auth.guard.ts
src/app/core/interceptors/auth.interceptor.ts
src/app/core/interceptors/auth.interceptor.spec.ts
src/app/core/services/theme.ts
src/app/core/services/auth.spec.ts
src/app/features/auth/auth.validators.ts
src/app/features/auth/auth-form.scss
src/app/features/auth/auth.spec.ts
src/app/layouts/auth-layout/auth-layout.ts
src/app/layouts/auth-layout/auth-layout.scss
src/app/layouts/main-layout/main-layout.ts
src/app/layouts/main-layout/main-layout.html
src/app/layouts/main-layout/main-layout.scss
```

Reused unchanged: `src/app/core/models/auth.model.ts`, all dashboard and AI Assistant source,
global Material theme, SSR entry points and server route configuration. No dependencies added.

Suggested commit message:

```text
feat(auth): add guarded Angular login and registration with in-memory JWT sessions
```

Implementation follows Angular's official [functional interceptor](https://angular.dev/guide/http/interceptors),
[route guard](https://angular.dev/guide/routing/route-guards) and
[SSR guidance](https://angular.dev/guide/ssr).
