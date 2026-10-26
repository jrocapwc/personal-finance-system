# Milestone 8G.1: account management

The pre-change audit found only `GET /api/v1/users/me`; no `UserService`, profile update, or password-change implementation existed. The existing authentication controllers, user repository, response DTO, BCrypt encoder (cost 12), browser cookie policy, CSRF policy, and JWT decoder are reused. Angular is unchanged.

## HTTP contracts

| Endpoint | Request | Success |
| --- | --- | --- |
| `GET /api/v1/users/me` | Existing authenticated request | Existing `UserResponse` |
| `PUT /api/v1/users/me` | `{"name":"Updated Full Name"}` | 200, existing `UserResponse` |
| `PUT /api/v1/users/me/password` | `{"currentPassword":"CurrentPassword123","newPassword":"NewSecurePassword456"}` | 204, empty body; clears authentication cookie and rotates CSRF |

Both PUT endpoints resolve the user from the verified Spring Security JWT. Cookie requests require the readable `XSRF-TOKEN` cookie and matching `X-XSRF-TOKEN` header. The existing header-only Bearer exemption is preserved. Missing or incorrect CSRF returns 403 before authentication; missing, expired, or revoked authentication returns 401 when CSRF passes or is exempt.

Names are stripped before validation, required, nonblank, and limited to 100 characters. Profile updates preserve email, password hash, ID, creation timestamp, and token version. Unknown fields, including frontend-supplied user IDs and email changes, remain rejected by the existing strict JSON policy.

Passwords follow registration's existing validation: a nonblank new password of at least 12 characters and no more than 72 UTF-8 bytes. The current password is required and checked through `PasswordEncoder.matches`. Reusing the password is rejected. Incorrect current passwords and reuse both return 400 `INVALID_PASSWORD_CHANGE` with the safe message `Password change rejected`. Validation errors use 400 `VALIDATION_FAILED`; malformed JSON and unknown fields use 400 `INVALID_REQUEST`. All errors retain `ApiError`; unexpected failures retain the generic 500 handler. Request string representations are redacted, and password values/hashes are never included in responses or exception messages.

## Revocation and transactions

Flyway `V7__user_token_version.sql` adds a nonnegative `BIGINT token_version`, initially zero for all existing users. Newly issued JWTs include this version. The existing resource-server converter loads the user and requires an integer version claim equal to the stored version, for both authentication cookies and retained Bearer headers. Signature, issuer, audience, and timestamp validation remain in the existing JWT decoder.

A password change locks the authenticated user's row, rechecks the request's token version, verifies the password, and saves the new BCrypt hash and incremented version in one transaction. Cookies are cleared only after that transaction commits. Login holds the same row lock through credential verification and token issuance, preventing an old-password login from receiving the new token version during a concurrent password change. Profile updates also lock the row and recheck the token version, preventing stale account writes from restoring an old hash/version. Failed changes leave credentials intact.

After a successful password change, all earlier tokens for that user fail authentication, including copied Bearer tokens and cookies retained by other browsers. Session restoration fails and the user must log in with the new password. Other users' credentials remain valid. Requests already authenticated before revocation can finish; account mutations recheck under their row lock.

Deployment requires Flyway V7 before serving the updated application. Tokens issued before this upgrade have no version claim and are rejected: existing users must log in again. All application instances serving these tokens must use the updated version checks. No refresh token, token blacklist, or new logout contract is introduced.

## Verification

Run `./mvnw.cmd test` from `core-api` using JDK 25. `UserAccountIntegrationTests` covers password validation and byte boundaries, BCrypt persistence, login with old/new passwords, cookie clearing, CSRF enforcement, revocation across cookie/Bearer/session restoration paths, profile persistence and validation, protected fields, account isolation, malformed version claims, and concurrent password changes/login. Existing authentication, finance, AI, ownership, JWT validation, migration, and sensitive-logging tests remain part of the Maven suite. Migration tests verify existing users and hashes survive V7 with an initial token version of zero.

Executed on October 26, 2026 with JDK 25: **BUILD SUCCESS — 395 tests, 0 failures, 0 errors, 0 skipped**, including 26 account integration tests. The local execution log is `core-api/milestone-8g1-tests.log` (ignored by Git).
