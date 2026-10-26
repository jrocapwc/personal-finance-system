# Provider card status follows demo connection state

The cards previously added an independent `isConnected: false` to every provider. The working demo workflow updated `connection`, so Metrobank's card never reflected its persisted connection.

Cards now derive their state directly from the `connection` signal: the server's `providerId` must match the card ID, and both `simulated` and `connected` must be true. A matching card shows a disabled Connected button, a green check-circle icon, and DEMO CONNECTED. All other cards show disabled Connect and COMING SOON. There are no click handlers on these buttons, no CSV picker action and no additional connection requests caused by rendering status.

The initial authenticated GET restores persisted connection state. Successful demo POST/DELETE responses update the same signal used by the details section and provider cards. Failed requests leave the previous confirmed state unchanged. Sync and its follow-up connection refresh form one RxJS request chain; the busy guard remains active until the refresh completes, preventing an older refresh from racing a newer local disconnect. Refresh errors use the existing API error display, and subscriptions are cancelled on component destruction.

## API addition

The current backend returns one Metrobank simulation; it does not support choosing other demo providers. Its existing DTO contained only a display name. To allow stable ID matching, GET `/api/v1/bank-connections`, POST `/api/v1/bank-connections/demo` and DELETE `/api/v1/bank-connections/demo` now also return `providerId: "metrobank"`. Existing fields and routes are preserved. No database migration, new endpoint, credentials or real bank connectivity were added. The existing user-owned `demo_connections.connected` value remains the persisted source of status. Rebuild/restart Spring alongside Angular so the additive field is available; without a recognized ID the UI leaves cards disconnected.

Other card IDs (`bpi`, `bdo`, `security`, `unionbank`, `other`) use the same derivation if trusted demo responses support them in a future milestone. Unit fixtures exercise those IDs; they do not add backend provider selection or simulate successful connections in the application. Display names are not used to determine status or select the details logo.

## Files changed

- `frontend/personal-finance-ui/src/app/features/bank-connections/bank-connections.ts`: shared state derivation, stable-ID logo lookup, static check icon, serialized sync refresh and busy guards.
- `frontend/personal-finance-ui/src/app/features/bank-connections/bank-connections.html`: reactive labels, badges, icons and accessible demo status; buttons remain disabled.
- `frontend/personal-finance-ui/src/app/features/bank-connections/bank-connections.scss`: connected badge color using existing theme tokens.
- `frontend/personal-finance-ui/src/app/features/bank-connections/bank-connections.spec.ts`: restored state, connect/disconnect transitions, provider matching, failures, race prevention, no card action and CSV regressions.
- `frontend/personal-finance-ui/src/app/core/services/bank.ts`: additive providerId response field.
- `core-api/src/main/java/core_api/bank/BankService.java`: stable Metrobank ID on connection responses.
- `core-api/src/test/java/core_api/bank/BankIntegrationTests.java`: HTTP response ID, persisted connection and disconnect checks in isolated H2.
- `qa/test_cookie_browser.py`: actual connection, reload, disabled card behavior, themes/mobile, synchronization and CSV workflow.
- This document and updated earlier implementation/asset notes.

## Manual verification

Executed validation:

- Angular: **237 passed in 18 test files**, including the final Material button template.
- Spring: **12 BankIntegrationTests passed**, zero failures/errors/skips, against isolated H2; Maven package succeeded.
- Angular production build: **passed**. Existing stylesheet warning thresholds remain: bank 7.44 kB, dashboard 6.75 kB and assistant 5.16 kB against a 4 kB warning threshold. No budget thresholds were changed. The Material icon projection warning observed during development was corrected before the final build.
- Browser: **1 passed, 3 deselected in 44.82 seconds**, against fresh isolated services. Verified actual Metrobank demo connect, restored status after reload, only Metrobank connected, inert provider buttons, dark/light themes, mobile width, two syncs with deduplication, disconnect, CSV preview/category selection/confirmation, duplicate upload and cancellation.
- `git diff --check` passed with the repository's CR-at-EOL whitespace setting.

Screenshots: [Metrobank connected, light](../core-api/target/qa-bank-screenshots/demo-connected-light.png) and [Metrobank connected, dark](../core-api/target/qa-bank-screenshots/demo-connected-dark.png). Screenshot records are fictional.

1. Open Bank Connections. All provider buttons remain disabled; their clicks do not open files.
2. Use Connect demo account in the simulation section. Verify only Metrobank shows Connected, the check icon and DEMO CONNECTED, with the Metrobank logo in the details section.
3. Reload the page and verify the status restores from the authenticated backend response.
4. Switch themes and use a mobile viewport. Check both connected and disconnected labels remain readable.
5. Sync twice; the repeated sync must skip already imported demo transactions.
6. Disconnect through the simulation controls. Verify Metrobank restores disabled Connect and COMING SOON.
7. Use the dedicated CSV upload area to preview, map categories, confirm and review duplicates. Balance Hide/Show remains available.

Suggested commit: `fix(frontend): sync bank provider cards with demo connection state`
