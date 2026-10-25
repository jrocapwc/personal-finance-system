# FinanceAI Angular frontend

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
