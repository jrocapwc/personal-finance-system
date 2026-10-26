# Milestone 8F.1 ? YamanTracker branding and warm UI theme

## Result

The visible FinanceAI identity is now **YamanTracker** across the sidebar, desktop navigation, authentication layout, login copy, dashboard welcome text and route titles. The base browser title is **YamanTracker | Personal Finance Management System**; route-specific titles remain informative, such as **Dashboard | YamanTracker**. Application name, description, browser theme color and favicon metadata are updated.

The floating assistant is branded **Ask Yaman Ai** in its header, welcome state, message author label, closed-button accessible name and tooltip. Close and message controls also use the new name. The robot/close icon toggle, bottom-right placement, GENERAL/FINANCE modes, financial month, requests, history and saved conversations are preserved.

The original wallet/growth SVG favicon is a distinct YamanTracker mark. No PwC logo or endorsement statement is used. The palette is inspired by the supplied warm color references.

## Theme architecture and color mapping

`src/styles/_theme.scss` owns semantic custom properties for the document and application shell, plus Angular Material system-token overrides. `styles.scss` configures Material's orange palette and a Roboto/Arial/sans-serif font stack. `app.scss` now handles shell layout only. The existing `finance-theme` storage key is retained.

`ThemeService` updates both the existing shell class and the document's dark class after client rendering and when the user toggles the theme. Material/CDK overlays outside the shell inherit the same colors and color scheme. Authentication, native finance dialogs, bank forms, tables, cards, chart states and chat all consume shared properties. No component or dashboard layout was recreated. Browser verification also found that Bank Connections was omitted from the existing private client-rendered SSR route list. It now uses `RenderMode.Client`, matching Dashboard/Transactions/Categories/Budgets, so direct visits and refreshes restore the authenticated screen rather than serving the signed-out prerender.

| Role | Previous | Light | Dark |
| --- | --- | --- | --- |
| Primary filled controls | Teal | `#D04A02`, white text | `#FFAD73`, dark brown text |
| Primary hover | Teal variations | `#A83D02` | `#FFC59B` |
| Small brand text / focus | Teal | `#A83D02` for contrast | `#FFAD73` |
| Page | Cool pale blue | `#F7F7F7` | `#191919` |
| Cards/dialogs | White / blue slate | `#FFFFFF` | `#272727` |
| Alternate surface / inputs | Cool gray / slate | `#F2F2F2` | `#333333` |
| Text | Navy / pale blue | `#2D2D2D` | `#F7F7F7` |
| Secondary text | Blue-gray | `#686868` | `#C1C1C1` |
| Selected navigation | Teal tint | `#FFF0E6` | `#482D1F` |
| Borders | Cool gray | `#E5E5E5` | `#494949` |
| Input boundaries | Subtle gray | `#858585` | `#8A8A8A` |
| Assistant header | Teal | `#A83D02`, white text | `#803000`, white text |
| Income / positive / SAFE | Green semantic values | `#247044` | `#8BD3A5` |
| Expense / negative / EXCEEDED | Red semantic values | `#B52C1E` | `#FF9B8C` |
| WARNING | Gold | `#855600` | `#FFD166` |
| Income chart | Teal | Complementary `#285C92` | `#93BFF1` |
| Expense chart | Blue | `#D04A02` | `#FFAD73` |

Tangerine `#EB8C00`, gold `#FFB600`, red `#E0301E` and charcoal `#2D2D2D` remain named palette references. Chart fallback colors use theme-aware orange, amber, gold, red, blue and purple. Existing category hex colors are preserved, not rewritten. Chart calculations, labels, values, percentages and finance status meanings remain unchanged. SAFE progress uses green, WARNING uses amber and EXCEEDED uses red; statuses also retain text labels.

The assistant uses a surface-colored circular launcher with orange outline/icon, a darker orange header, neutral assistant bubbles and primary-orange user bubbles and send controls. Form surfaces and text follow the selected theme. Hover, focus and reduced-motion behavior are retained.

## Contrast review

Computed sRGB contrast checks passed the 4.5:1 threshold for selected normal-text pairs: light text 13.77:1, light muted text on the page 5.20:1, white on primary orange 4.50:1, dark text 13.94:1, dark muted text 7.02:1, dark primary control text 9.12:1, light positive 6.04:1, light negative 5.66:1, light warning 5.59:1 and the dark chat header 8.97:1. Small orange labels use the darker `#A83D02` rather than the filled-button orange. Gold is not used as normal text on white. This is a targeted palette review, not a claim of a complete accessibility audit of every user-defined color or browser state.

## Preserved contracts

No backend source, database schema, environment variable, API endpoint, package/project name, authentication contract or MongoDB collection changed. The theme storage identifier remains `finance-theme`. HttpOnly cookies, CSRF, restored sessions, expiry, logout and cross-user isolation use their existing services. Category/transaction/budget CRUD, dashboard refresh, demo synchronization, reviewed CSV imports and AI conversation persistence are unchanged.

## Screenshots

Browser screenshots use fictional records and isolated services. Authentication comparisons:

| Screen | Before | After |
| --- | --- | --- |
| Login | [FinanceAI](../core-api/target/qa-branding-screenshots/before/login-light.png) | [YamanTracker](../core-api/target/qa-branding-screenshots/after/login-light.png) |
| Registration | [FinanceAI](../core-api/target/qa-branding-screenshots/before/register-light.png) | [YamanTracker](../core-api/target/qa-branding-screenshots/after/register-light.png) |

Additional captures: [dashboard](../core-api/target/qa-branding-screenshots/after/dashboard-light.png), [light assistant](../core-api/target/qa-branding-screenshots/after/assistant-light.png), [dark assistant](../core-api/target/qa-branding-screenshots/after/assistant-dark.png), [dark finance dialog](../core-api/target/qa-branding-screenshots/after/category-dialog-dark.png), [dark bank screen](../core-api/target/qa-branding-screenshots/after/bank-dark.png), and [mobile dark assistant](../core-api/target/qa-branding-screenshots/after/assistant-mobile-dark.png).

These are local, ignored `target` artifacts and are not committed. The QA fixture blocks public font CDNs, so Material icon ligatures may appear as fallback text in screenshots. The production icon dependency is unchanged; typography now has an explicit sans-serif fallback.

## Verification

- Angular tests: **229 passed across 18 files**, including updated assistant/layout selectors and existing auth, finance, dashboard and bank review coverage.
- Angular production build: **passed**, including browser and SSR output and favicon copying. Current warning details are recorded below.
- Isolated real HTTP/browser checks: **five existing regression checks passed**, followed by **one focused branding/theme/private-bank-refresh check passed** (22.37 seconds; three unrelated browser checks deselected for that focused rerun). The earlier full six-check run had five passes and one visual-check failure before the bank SSR fix; it is not reported as a fully passing suite. After fixing the route, the focused check passed against the final production build.
- Targeted contrast calculations: passed for the text/control pairs listed above.
- Production source search: no remaining `FinanceAI` or old hardcoded teal branding. Explicit user-specified category colors in fictional fixtures are retained.

The browser checks use the production Angular build, in-memory H2, the existing Node demo service, and FastAPI with test AI/Mongo dependencies. No personal statement, live provider or development database is used. They exercise auth/CSRF/session restoration/expiry/logout, finance rendering, bank synchronization, CSV review/import, both AI modes, history, dark native dialogs, Material buttons, theme persistence, favicon delivery and desktop/mobile overflow. Angular component tests preserve finance CRUD behavior.

The baseline captured login/registration comparisons, but its bank direct-visit check exposed the existing missing client-rendered SSR route described above; that run did not complete. An initial visual test also had an incorrectly capitalized `Add Category` selector, which was corrected. A subsequent direct bank visit reproduced the SSR issue; the final focused rerun passed after the frontend route fix. Failed attempts are not counted as passing.

## Remaining observations

- The existing dashboard stylesheet exceeds the configured 4 kB warning threshold; the themed version is about 6.58 kB. The assistant stylesheet is about 4.54 kB and also emits a warning. The production build succeeds; budget limits were not increased to suppress warnings.
- Public Material icon fonts remain a network dependency. Offline screenshots show fallback icon text; no robot behavior was removed or substituted.
- User-defined category colors can still be low contrast. They are preserved as financial/user data; category names, values and percentage labels remain readable independently of their swatches.
- Native finance dialogs are the dialogs currently used by the application. Their dark surfaces/inputs are checked in the browser; root-scoped Material tokens cover future CDK overlays without claiming an existing Material dialog workflow.

## Files created

- `frontend/personal-finance-ui/src/styles/_theme.scss`
- `frontend/personal-finance-ui/public/favicon.svg`
- `docs/milestone-8f1-yamantracker-branding.md`

## Files modified

- `frontend/personal-finance-ui/src/app/app.routes.server.ts` (private bank-route refresh fix)

- `frontend/personal-finance-ui/src/app/app.routes.ts`
- `frontend/personal-finance-ui/src/app/app.scss`
- `frontend/personal-finance-ui/src/app/app.spec.ts`
- `frontend/personal-finance-ui/src/app/core/services/theme.ts`
- `frontend/personal-finance-ui/src/app/features/auth/auth-form.scss`
- `frontend/personal-finance-ui/src/app/features/auth/login/login.html`
- `frontend/personal-finance-ui/src/app/features/bank-connections/bank-connections.scss`
- `frontend/personal-finance-ui/src/app/features/categories/categories.html`
- `frontend/personal-finance-ui/src/app/features/dashboard/dashboard-controls.scss`
- `frontend/personal-finance-ui/src/app/features/dashboard/dashboard.html`
- `frontend/personal-finance-ui/src/app/features/dashboard/dashboard.mapper.spec.ts`
- `frontend/personal-finance-ui/src/app/features/dashboard/dashboard.mapper.ts`
- `frontend/personal-finance-ui/src/app/features/dashboard/dashboard.scss`
- `frontend/personal-finance-ui/src/app/features/finance/finance-dialog.scss`
- `frontend/personal-finance-ui/src/app/features/finance/finance-page.scss`
- `frontend/personal-finance-ui/src/app/layouts/auth-layout/auth-layout.scss`
- `frontend/personal-finance-ui/src/app/layouts/auth-layout/auth-layout.ts`
- `frontend/personal-finance-ui/src/app/layouts/main-layout/main-layout.html`
- `frontend/personal-finance-ui/src/app/layouts/main-layout/main-layout.scss`
- `frontend/personal-finance-ui/src/app/layouts/main-layout/main-layout.ts`
- `frontend/personal-finance-ui/src/app/shared/components/ai-assistant/ai-assistant.html`
- `frontend/personal-finance-ui/src/app/shared/components/ai-assistant/ai-assistant.scss`
- `frontend/personal-finance-ui/src/app/shared/components/ai-assistant/ai-assistant.spec.ts`
- `frontend/personal-finance-ui/src/index.html`
- `frontend/personal-finance-ui/src/styles.scss`
- `qa/test_cookie_browser.py`

Recommended commit:

```text
style(frontend): rebrand FinanceAI to YamanTracker with PwC-inspired theme
```
