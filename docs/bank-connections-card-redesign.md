# Bank Connections card and upload redesign

Provider-state follow-up: [Provider card status follows demo connection state](bank-provider-connected-status.md) supersedes the disconnected-placeholder behavior described below. The current Metrobank simulation now drives its matching card through the stable server-provided providerId.

The Bank Connections screen now follows the supplied reference: a three-column, two-row bank card grid, warm orange actions, and a wide CSV drag-and-drop upload panel with a Supported format sidebar. Tablet layouts use two columns; narrow mobile layouts use one column. Existing Light/Dark theme tokens style the cards, controls, table and preview.

## Working actions and honest capabilities

The five named bank cards now use locally stored bank SVGs from the source-pinned MIT-licensed PHLogos library. Colors and proportions are preserved with object-fit: contain and white logo panels in both themes. Other Bank uses Angular Material's account_balance SVG. Connection details reuse the same provider metadata for logos; fictional Harbor Demo Bank uses the generic icon. Asset provenance, retained license notices and the distinction between library licensing and bank approval are documented in [the asset README](../frontend/personal-finance-ui/public/banks/README.md).

The current API supports one fictional account, **Harbor Demo Bank**. Its connect/disconnect/sync controls remain in a compact SIMULATION strip with account name, masked number, clearly labeled demo balance, status and last sync timestamp.

The demo balance includes a keyboard-accessible Hide/Show eye button. Hiding replaces the amount with dots in the rendered markup while preserving the simulation label and synchronization timestamp. Visibility is local to the current page visit and does not change the account balance.

The six bank cards are future integration placeholders: BPI, BDO, Metrobank, Security Bank, UnionBank and Other Bank. Every **Connect** button is natively disabled, has no click handler, and is marked Coming Soon. The existing API provides no connection state for these providers, so all are disconnected. A future verified `isConnected` value renders a disabled **Connected** button with success styling and an accessible label; no provider currently receives that value in production. Harbor Demo Bank remains separate and uses its existing authenticated server state.

The defect was the bank-card `(click)="chooseBank(bank.name)"` binding: that method set a temporary `connectedBanks` value, selected a CSV source and called `browseFile()`. Those bindings, methods and temporary signals have been removed. Clicking a provider button cannot open files, navigate, mutate state or issue requests. CSV APIs, authentication, backend services and schemas are unchanged.

## CSV workflow

- Only Browse File, the upload drop-zone button and dropping one file initiate CSV selection. The separately labeled file input uses the existing file validation and column-discovery API.
- Zero-byte and greater-than-1-MiB files are rejected before upload. Malformed content, encoding and parsed row limits are still validated server-side.
- Multiple dropped files are rejected. Upload replacement is blocked while another request is active or a preview is being reviewed.
- The file input is cleared after selection so a cancelled or corrected statement can be chosen again.
- Supported format copy matches actual limits: UTF-8 CSV, PHP, signed amounts or debit/credit, maximum 1 MiB and 1,000 data rows.
- Column mapping, explicit date order, category choices, invalid/duplicate row exclusion, cancellation, confirmed import, server validation, idempotency and report refresh are preserved. Uploading alone never imports rows.
- Raw upload retention, temporary normalized previews, owner isolation and CSRF protections are unchanged.

No backend code, endpoints, database migrations or integration-service contracts changed.

## Files changed for this task

- `frontend/personal-finance-ui/src/app/features/bank-connections/bank-connections.html`
- `frontend/personal-finance-ui/src/app/features/bank-connections/bank-connections.scss`
- `frontend/personal-finance-ui/src/app/features/bank-connections/bank-connections.ts`
- `frontend/personal-finance-ui/src/app/features/bank-connections/bank-connections.spec.ts`
- `qa/test_cookie_browser.py`
- `frontend/personal-finance-ui/public/banks/` (five SVG logos, source notes and retained license notices).
- This document.

The preceding YamanTracker reference-design changes were already present in the workspace and remain intact.

## Validation

- Angular: **234 tests passed in 18 files**. Regressions cover disabled provider buttons, no picker/API/state changes, a template-only connected-state fixture, dedicated upload controls, and the existing CSV review/confirmation behavior. An initial run exposed a test fixture rendered before its setup and an unrelated dashboard timeout; after correcting fixture setup, the complete rerun passed without changing application timeout settings.
- Angular production build: **passed**, including the local-logo update. The bank stylesheet reports a 7.40 kB warning against the existing 4 kB warning threshold; dashboard (6.75 kB) and assistant (5.16 kB) warnings also remain. No budget limits were increased.
- Focused browser workflow: **1 passed, 3 deselected in 35.67 seconds**, using isolated H2 and fictional data. It uses the actual Browse File chooser for the initial import and a browser DataTransfer/DragEvent for the duplicate upload. Disabled placeholders produce no file chooser, API request, navigation or state change in light mode, dark mode and mobile layout. Review, confirmation, duplicate handling, cancellation and demo sync also passed. An initial run incorrectly counted a delayed local SVG asset load as an API action; the assertion was narrowed to API requests and the full workflow rerun successfully.

## Manual verification

Final logo validation: all **234 Angular tests passed**, the production build passed, and the focused browser workflow passed again in **26.56 seconds** (three unrelated checks deselected). Browser assertions verify all five local logo images load, preserve object-fit: contain with no filter, and the generic Material icon has a visible fill in light/dark and mobile layouts. Updated screenshots: [logos, light](../core-api/target/qa-bank-screenshots/logos-light.png) and [logos, dark](../core-api/target/qa-bank-screenshots/logos-dark.png).

1. Open Bank Connections in light and dark modes. All six provider buttons show Connect, Coming Soon and a disabled cursor; attempts do not open files, navigate or send bank requests.
2. Connect Harbor Demo Bank using its separate simulation controls. Verify its server-owned status changes without marking any provider card connected. Verify balance Hide/Show still works.
3. Use Browse File or click the CSV drop zone. Choose a fictional UTF-8 statement, review mappings and date order, preview, assign compatible categories, and explicitly confirm.
4. Drop the same statement again and verify duplicates are excluded. Cancel the preview without importing additional records.
5. At a 390px viewport, verify cards and upload area fit and the preview table scrolls internally.

Local screenshot artifacts: [bank cards, light](../core-api/target/qa-reference-screenshots/after/bank-light.png), [CSV upload, light](../core-api/target/qa-reference-screenshots/after/bank-upload-light.png), [bank cards, dark](../core-api/target/qa-reference-screenshots/after/bank-dark.png), [CSV upload, dark](../core-api/target/qa-reference-screenshots/after/bank-upload-dark.png). These are ignored QA artifacts generated with fictional records and an isolated in-memory database.

Suggested commit:

```text
fix(frontend): separate bank connection placeholders from CSV import
```
