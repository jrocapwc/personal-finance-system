# YamanTracker ? reference-inspired visual refinement

The supplied reference guides the visual treatment of the existing working application. This update adds a warm skyline login panel, an original ascending-bar logo, a compact navigation shell, horizontal white summary cards, a personalized greeting, orange/red charts and a locally rendered robot launcher. Light and Dark modes and responsive layouts remain supported.

## Applied design

- Original three-bar financial-growth mark with orange/gold gradient, shared by the favicon, sidebar and authentication branding.
- Split desktop authentication layout: working form on the left, original golden-hour skyline photograph and HTML copy on the right. Small screens show the form in one column.
- Narrower sidebar, active orange edge/tint, compact navigation, profile initials and a two-state theme switch with the existing accessible labels and saved preference.
- Working sidebar **AI Assistant** and **History** actions open the existing floating assistant and conversation history. No placeholder Profile/Settings routes were introduced.
- Personalized **Welcome back, [first name]!** uses the authenticated profile. The four existing real metrics (income, expenses, savings and savings rate) and month/year controls are retained. There are no fictional growth percentages or replacement sample amounts.
- Summary cards use horizontal icon/value layouts, circular semantic icon backgrounds and lighter shadows. Income/expense bars use warm gradients with readable outlines; donut fallback colors use the warm palette while respecting user category colors.
- **Ask Yaman Ai** uses an original SVG robot, circular orange launcher, visible attached label, orange header, neutral assistant messages and warm user messages. GENERAL/FINANCE behavior and saved history are unchanged.
- Navigation, authentication, finance/report and assistant icons use a self-hosted SVG symbol sheet inside the existing Material icon hosts. These icons render even when public font CDNs are blocked.
- Bank Connections retains the clearly labeled fictional demo provider and confirmed CSV workflow. The reference's live-bank logos and live login implications were not introduced.

The backend APIs, authentication/CSRF contracts, Node integration, persistence and financial calculations are unchanged.

## New assets

- `frontend/personal-finance-ui/public/assistant-robot.svg`: original vector mascot.
- `frontend/personal-finance-ui/public/ui-icons.svg`: original local outline icon symbols.
- `frontend/personal-finance-ui/public/auth-skyline.png`: generated decorative skyline (approximately 2.4 MB).
- `public/favicon.svg` was updated to the new growth mark.

The skyline was generated with the **built-in imagegen tool**, inspected, and copied into the workspace. It is a stylized photographic city scene, not a claim of a particular real building or location. Prompt used:

```text
Use case: photorealistic-natural. Asset type: portrait website login background photograph for YamanTracker personal finance app. Primary request: an original polished editorial photograph of a modern Philippine city skyline at golden hour, inspired by the warm skyline panel in the user's UI reference. Scene: Manila-style modern downtown buildings seen from above surrounding trees, hazy sunset sky and warm glowing windows. Composition: vertical portrait 2:3, buildings in middle and lower half, ample sky upper half, lower third has dark unobtrusive tones for white HTML copy overlay. Warm amber/orange sunlight, deep warm charcoal shadows, photographic architectural detail, professional aspirational mood. No text, no logos, no watermarks, no people. This is a decorative background asset, not a UI mockup.
```

## Screenshots

Local QA artifacts compare the prior YamanTracker styling with this reference-inspired version:

| View | Before | After |
| --- | --- | --- |
| Login | [Prior login](../core-api/target/qa-reference-screenshots/before/login-light.png) | [Split skyline login](../core-api/target/qa-reference-screenshots/after/login-light.png) |
| Dashboard | [Prior dashboard](../core-api/target/qa-reference-screenshots/before/dashboard-light.png) | [Updated dashboard](../core-api/target/qa-reference-screenshots/after/dashboard-light.png) |
| Assistant | [Prior dark assistant](../core-api/target/qa-reference-screenshots/before/assistant-dark.png) | [Updated dark assistant](../core-api/target/qa-reference-screenshots/after/assistant-dark.png) |
| Mobile | [Prior mobile](../core-api/target/qa-reference-screenshots/before/assistant-mobile-dark.png) | [Updated mobile](../core-api/target/qa-reference-screenshots/after/assistant-mobile-dark.png) |

[Dark bank screen](../core-api/target/qa-reference-screenshots/after/bank-dark.png) and [dark finance dialog](../core-api/target/qa-reference-screenshots/after/category-dialog-dark.png) were also captured. Images are ignored local `target` artifacts, not committed files. Public font CDNs are blocked in QA; the new SVG icons continue to render.

## Executed verification

- Angular: **229 tests passed in 18 files**.
- Production build: **passed**, including browser/SSR bundles and the local static assets.
- Isolated browser/backend regression: **6 passed in 68.35 seconds**. This covers auth/CSRF/session restoration/expiry/logout, real finance and mock-bank flows, CSV preview/confirmation, GENERAL and FINANCE chat, saved history, desktop/mobile overflow, themes and persistence.
- The visual check additionally verified the personalized greeting, skyline image loading, SVG asset delivery, sidebar assistant/history actions, dark dialog/input colors and warm Material button colors.
- Final focused screenshot check after icon/spacing polish: **1 passed in 24.49 seconds**, with three unrelated checks deselected.

The build reports component stylesheet warnings (dashboard approximately 6.75 kB and assistant approximately 5.1 kB versus the existing 4 kB warning threshold). Compilation succeeds; the thresholds were not increased. The static hero PNG is the main added asset weight. No backend source was modified or production/private financial data used during QA.

## Modified files

- `frontend/personal-finance-ui/public/favicon.svg`
- `frontend/personal-finance-ui/src/app/features/auth/auth-form.scss`
- `frontend/personal-finance-ui/src/app/features/auth/login/login.html`
- `frontend/personal-finance-ui/src/app/features/auth/register/register.html`
- `frontend/personal-finance-ui/src/app/features/bank-connections/bank-connections.html`
- `frontend/personal-finance-ui/src/app/features/dashboard/dashboard-controls.scss`
- `frontend/personal-finance-ui/src/app/features/dashboard/dashboard.html`
- `frontend/personal-finance-ui/src/app/features/dashboard/dashboard.scss`
- `frontend/personal-finance-ui/src/app/features/dashboard/dashboard.ts`
- `frontend/personal-finance-ui/src/app/layouts/auth-layout/auth-layout.scss`
- `frontend/personal-finance-ui/src/app/layouts/auth-layout/auth-layout.ts`
- `frontend/personal-finance-ui/src/app/layouts/main-layout/main-layout.html`
- `frontend/personal-finance-ui/src/app/layouts/main-layout/main-layout.scss`
- `frontend/personal-finance-ui/src/app/layouts/main-layout/main-layout.ts`
- `frontend/personal-finance-ui/src/app/shared/components/ai-assistant/ai-assistant.html`
- `frontend/personal-finance-ui/src/app/shared/components/ai-assistant/ai-assistant.scss`
- `frontend/personal-finance-ui/src/styles.scss`
- `frontend/personal-finance-ui/src/styles/_theme.scss`
- `qa/test_cookie_browser.py`

Suggested commit:

```text
style(frontend): refine YamanTracker to match the visual reference
```
