# Milestone 8E.1: cookie authentication

## Audit and implementation design

The actual Angular workspace is `frontend/personal-finance-ui`. Spring's stateless resource-server chain already verifies HS256 signatures, issuer, audience, timestamps and database user existence. Finance and AI controllers derive ownership from that verified identity. Angular calls Spring `/api/v1` for both finance and AI; Spring uses a separate internal credential for FastAPI. No AI schema or finance contract changes are needed.

Reuse that chain with a cookie-aware bearer resolver. Header-only Bearer requests remain supported for existing API clients. Any request with both an authentication cookie and an Authorization header is rejected; an invalid cookie never falls back to a header. Only header-only Bearer requests to non-auth endpoints are exempt from CSRF. Login, registration and logout always require CSRF, even with an Authorization header.

The browser login contract returns `user` and `expiresIn`, never the JWT. The service's internal token result is not an HTTP response. There is no new public token-issuing endpoint. Existing trusted clients must provision credentials outside the browser login contract.

Production defaults: `__Host-finance_session`, HttpOnly, Secure, SameSite=Lax, Path=/, no Domain, 3,600-second maximum age. Startup rejects an insecure production configuration. Only the explicit `local` profile permits HTTP, using `finance_session`. Use `http://localhost:4200` with the Angular `/api` proxy to `localhost:8080`; do not mix localhost and 127.0.0.1. Production requires HTTPS and a same-origin reverse proxy for `/api`. Do not trust arbitrary forwarded headers; terminate TLS at a trusted proxy that overwrites forwarding headers. Cookie Secure is explicit and independent of request forwarding.

Spring's cookie CSRF repository uses readable `XSRF-TOKEN`, host-only Path=/, SameSite=Lax and the same Secure setting. Angular's built-in XSRF interceptor sends `X-XSRF-TOKEN` for own-origin mutations. GET `/api/v1/auth/csrf` bootstraps the token before login/registration/logout. Login and logout explicitly replace the CSRF token in the response. Session restoration also materializes the CSRF token. If the readable cookie is absent before a private mutation, the interceptor first obtains one through a shared bootstrap request, then uses Angular's token extractor to attach the matching header. This covers cookie loss or invalidation after authentication and browser restart. It never retries a dispatched write or masks a 403. The CSRF request handler resolves the plain header token; tokens are not reflected into HTML. No wildcard CORS or credentialed cross-origin requests are enabled.

Spring Resource Server normally adds a CSRF exemption for every resolved bearer credential. The final `CsrfFilter` matcher is explicitly replaced through Spring's object postprocessor so that cookie credentials never receive this exemption. Regression tests cover unsafe requests with cookie-only credentials and with both mechanisms. Missing or invalid CSRF returns 403 before authentication; with valid CSRF, missing/invalid authentication returns 401.

GET `/api/v1/auth/session` returns the safe user and remaining lifetime. Browser route guards share one restoration request and wait before rendering private views. A 401 clears the session; other failures route to a retry screen without claiming logout. Authenticated routes render in the browser, public login/registration remain prerendered, and authentication requests disable HTTP transfer caching.

Logout clears private state synchronously and calls the CSRF-protected server endpoint. Pending private requests are cancelled centrally. A failed server logout must be retried; local cleanup alone cannot delete HttpOnly cookies. Logout clears the browser credential, but does not revoke a copied stateless JWT, which remains valid until its original expiration. No refresh tokens, rolling lifetime or blacklist are introduced.

## HTTP contracts

| Endpoint | Access and result |
| --- | --- |
| `GET /api/v1/auth/csrf` | Public, 200, materializes readable CSRF cookie; no credential in body |
| `POST /api/v1/auth/register` | Public plus CSRF, existing 201 safe user response |
| `POST /api/v1/auth/login` | Public plus CSRF, 200 `{user, expiresIn}`, sets HttpOnly auth cookie and rotates CSRF |
| `GET /api/v1/auth/session` | Authenticated, 200 `{user, expiresIn}` with remaining lifetime; otherwise 401 |
| `POST /api/v1/auth/logout` | CSRF required, 204, clears auth cookie and rotates CSRF, works with stale cookies |

`LoginResponse` remains an internal service result, with its redacted `toString()`. Only `BrowserSession` is returned over HTTP. Historical manual Bruno scripts expecting a JSON access token need the new cookie/CSRF flow; use the cookie-aware QA harness below for the current end-to-end contract. Inline request-specific credentials were removed from the two historical Bruno requests; configure private environment values instead.

## Frontend lifecycle

`AuthService.status` distinguishes initializing, authenticated, unauthenticated, recoverable session failure and failed server logout. Guards wait for shared restoration. The error route contains no private layout and offers retry. Return destinations are limited to existing private route names; external/protocol-relative URLs and public auth/error destinations fall back to Dashboard. Themes retain their existing storage key and service.

The service has no JWT field, decoder, token getters or browser credential storage. It records the safe profile and expiry in signals/memory. The interceptor uses same-origin cookies, disables API transfer caching, cancels pending private requests on session revision changes and clears state only on relevant 401s. A CSRF 403 leaves the user authenticated and surfaces the failure to the feature.

Finance stores/resources and the AI assistant already scope data to the current user and session changes; those mechanisms are preserved. Login clears old state before requesting a new identity. Logout clears it synchronously, including while offline, and waits for an already-submitted login to settle before sending server logout. A failed logout shows an explicit retry screen; it does not claim the HttpOnly cookie was removed. Already-processed server writes cannot be undone by cancelling a client request.

Tabs use an origin-scoped `BroadcastChannel` containing only a change notification. A login or successful logout in one tab clears private state in the others and sends them to Login. Where BroadcastChannel is unavailable, there is no promise of immediate tab synchronization; after cookie deletion, the next protected request fails authentication. No JWT or user information is sent through this channel.

## Files

- Backend: `BrowserCredentials.java`, `SecurityConfiguration.java`, `JwtConfiguration.java`, `AuthController.java`, production/local application properties. JWT validation uses the same injected clock as issuance for deterministic expiration tests. The raw Hibernate JDBC-error logger is suppressed because concurrent unique-email failures can include email values.
- Frontend: auth model/service, interceptor, guards, XSRF application providers, client-rendered private server routes, login return destination/pending-logout behavior, and `session-error.ts`.
- Tests: cookie/configuration/expiration backend tests, updated authentication/OpenAPI and existing authorization fixtures, Angular restoration/XSRF/SSR tests and updated feature fixtures, `qa/test_backend_e2e.py`, `qa/test_cookie_browser.py`.
- Documentation: this report, root/frontend README pointers. Historical Bruno Health/Login embedded request variables removed.

## Repeatable commands

From `core-api`: `./mvnw.cmd package` runs the complete backend tests and creates the executable JAR. Finish this before starting QA processes: Windows will lock a running JAR against repackaging.

From `frontend/personal-finance-ui`, use Node 24 LTS: `npm test -- --watch=false` and `npm run build`.

From the repository root, after packaging:

```powershell
$env:RUN_BACKEND_E2E = '1'
ai-service/.venv/Scripts/python.exe -m pytest qa/test_backend_e2e.py -q

# Optional Playwright test dependency; can be installed in the QA environment.
# This execution used: pip install --target .tools/browser-qa playwright
$env:PYTHONPATH = (Join-Path (Get-Location) '.tools/browser-qa')
$env:RUN_BROWSER_E2E = '1'
ai-service/.venv/Scripts/python.exe -m pytest qa/test_cookie_browser.py -q --tb=short
```

The harness starts separate loopback ports, random signing/internal keys and in-memory H2. FastAPI runs its real adapters/repositories with mocked OpenAI responses and in-memory MongoDB; it has a tripwire forbidding live OpenAI calls. HTTP QA explicitly uses the `local` cookie profile. Browser QA launches an isolated headless installed Chrome/Edge profile and a same-origin static-artifact/API proxy. It tests the production Angular build and prerendered public markup; it does not validate a production TLS proxy.

## Manual deployment checklist

- Configure a random `JWT_SECRET_BASE64` of at least 32 decoded bytes; keep production out of the `local` profile. No production cookie-security override is necessary.
- Serve the frontend and `/api` from one HTTPS origin. Configure HSTS at the TLS edge, strip untrusted forwarding headers, and do not enable wildcard credentialed CORS. Proxy all Cookie/Set-Cookie/XSRF headers without rewriting cookie Domain/Path.
- Register, log in and inspect `__Host-finance_session`: HttpOnly, Secure, Path=/, SameSite=Lax, no Domain and at most 3,600 seconds. Confirm no JWT appears in the login/session JSON, browser storage, page source or `document.cookie`.
- Refresh Dashboard and directly open Categories, Transactions and Budgets; verify profile restoration and CRUD/reporting. Close/reopen the browser within the JWT lifetime and verify the first mutation has an XSRF token.
- Test GENERAL and FINANCE messages, history reopening and context retention with the deployment's MongoDB/provider. Log out during a request and confirm all visible private state disappears.
- Check missing/wrong XSRF tokens return 403 and correct tokens succeed. A hostile origin must not receive credentialed CORS access. Check both cookie and Bearer headers cannot bypass CSRF or select a different owner.
- Log out in one tab; check other tabs clear private screens. Refresh and verify Login. Sign in as a second user and confirm no old finance or conversation state appears.
- Simulate a session-check outage and use Retry. Simulate a logout outage and confirm the explicit failed-logout message, then reconnect and retry before relying on browser logout.
- Verify expiration, both themes and mobile navigation. Production HTTPS/Secure-cookie behavior needs deployment-specific verification.

## Limitations

HttpOnly reduces script access to credentials; it does not prevent an XSS payload from acting through the logged-in browser. Existing Angular escaping/sanitization remains in place; no unsafe HTML rendering is introduced. Logout cannot revoke a copied JWT. No refresh token, long-lived login, rate limiter, token blacklist or server session is added. Existing generic invalid-credential responses and registration validation/uniqueness are retained; production abuse throttling remains an edge/deployment responsibility.

The CSRF token is a deliberately readable double-submit cookie. Both cookies are host-only; avoid hosting untrusted applications under the production site's trust boundary. Private APIs retain Spring's no-store response headers. There is no authenticated SSR data forwarding or transfer state.

Implementation references: [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [Angular HTTP security](https://angular.dev/best-practices/security).

Suggested commit: `feat(auth): add HttpOnly cookie sessions with CSRF and browser restoration`

## Executed verification

Executed on 2026-10-26 (Asia/Manila), using Java 25 and Node 24 LTS:

| Check | Result |
| --- | --- |
| Spring `mvnw.cmd package` | Passed; 348 tests, zero failures/errors/skips, executable JAR packaged |
| Angular `ng test --watch=false` | Passed; 222 tests across 17 files |
| Angular production `ng build` | Passed, native exit code 0; two public routes prerendered |
| Isolated three-service HTTP E2E | Passed; 2 tests covering cookie finance/reporting/AI flows, ownership, CSRF and logout |
| Headless browser E2E | Passed; both final production-artifact workflows, 2 tests in 39.53 seconds, exit code 0 |
| Bruno collection static checks | Passed; 16 tests and 36 JavaScript blocks parsed without execution |
| Whitespace check | `git -c core.whitespace=cr-at-eol diff --check` passed; preserves repository CRLF conventions |

The browser workflows cover registration/login without a JSON token, HttpOnly visibility, no credential storage, refresh, direct navigation, missing-CSRF recovery, hostile-origin rejection, both AI modes and history, cross-tab logout, second-user cleanup, light/dark preference and mobile width. A second workflow covers session-check network failure/retry, controlled expired JWT rejection and return to the intended route after login. Backend tests additionally use a deterministic clock to cross the original expiration deadline without sleeps.

The production build reports the existing Dashboard stylesheet warning (6.50 kB versus a 4 kB warning threshold); its error threshold is not exceeded. No finance styles or calculations were changed. Initial failed test runs were corrected rather than disabled. A Windows JAR lock during parallel packaging/testing was resolved by completing QA before repackaging.

Live OpenAI calls, a deployed MongoDB instance, production TLS/reverse-proxy behavior and copied-token revocation were not tested or claimed. No FastAPI application code changed; its real integration routes and MongoDB repository behavior were exercised through the isolated fixture with in-memory persistence.
