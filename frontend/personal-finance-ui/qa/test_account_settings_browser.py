"""Opt-in account UI checks using production artifacts and isolated cookie-auth services.

From the repository root, build Angular and package Core API first, then run:
RUN_BROWSER_E2E=1 PYTHONPATH=.tools/browser-qa ai-service/.venv/Scripts/python.exe -m pytest
frontend/personal-finance-ui/qa/test_account_settings_browser.py -q
"""
import os
from pathlib import Path

import pytest
from playwright.sync_api import expect

from qa.test_cookie_browser import backend, website, browser_context, register_and_login

pytestmark = pytest.mark.skipif(os.getenv("RUN_BROWSER_E2E") != "1", reason="Opt-in isolated browser E2E")
SHOTS = Path(__file__).resolve().parents[1] / "tmp" / "account-settings"


def test_profile_password_session_themes_and_mobile(browser_context, website):
    SHOTS.mkdir(parents=True, exist_ok=True)
    page = browser_context.new_page()
    user, email, current = register_and_login(page, "Account Browser User")
    page.get_by_role("link", name="Account Settings", exact=True).click()
    expect(page.get_by_role("heading", name="Account Settings", exact=True)).to_be_visible()
    profile = page.locator(".settings-card").first
    expect(profile).to_contain_text(email)
    expect(profile).to_contain_text(user["name"])
    assert profile.evaluate("el => getComputedStyle(el).backgroundColor") == "rgb(255, 255, 255)"
    assert page.locator("#newPassword").evaluate("el => getComputedStyle(el).color") == "rgb(45, 45, 45)"
    page.screenshot(path=str(SHOTS / "desktop-light.png"), full_page=True)
    page.get_by_role("button", name="Edit Profile", exact=True).click()
    page.locator("#account-name").fill("   ")
    page.get_by_role("button", name="Save", exact=True).click()
    expect(page.locator("#name-error")).to_contain_text("Enter a full name")
    page.locator("#account-name").fill("x" * 101)
    page.get_by_role("button", name="Save", exact=True).click()
    expect(page.locator("#name-error")).to_contain_text("100 characters")
    page.locator("#account-name").fill("  Updated Browser Name  ")
    with page.expect_response(lambda response: response.url.endswith("/api/v1/users/me") and response.request.method == "PUT") as saved:
        page.get_by_role("button", name="Save", exact=True).click()
    assert saved.value.status == 200
    assert saved.value.request.post_data_json == {"name": "Updated Browser Name"}
    assert saved.value.request.header_value("x-xsrf-token")
    assert saved.value.request.header_value("authorization") is None
    expect(page.locator(".profile")).to_contain_text("Updated Browser Name")
    expect(profile).to_contain_text("Your profile has been updated")
    page.get_by_role("button", name="Switch to dark mode").click()
    expect(page.locator(".app-shell.dark-theme")).to_be_visible()
    assert profile.evaluate("el => getComputedStyle(el).backgroundColor") == "rgb(27, 36, 39)"
    assert page.locator("#newPassword").evaluate("el => getComputedStyle(el).color") == "rgb(247, 247, 247)"
    page.screenshot(path=str(SHOTS / "desktop-dark.png"), full_page=True)

    page.set_viewport_size({"width": 390, "height": 844})
    page.get_by_role("button", name="Toggle sidebar").click()
    page.get_by_role("link", name="Account Settings", exact=True).click()
    expect(page.locator(".mat-drawer-backdrop")).not_to_be_visible()
    assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
    page.screenshot(path=str(SHOTS / "mobile-dark.png"), full_page=True)
    page.get_by_role("button", name="Update Password", exact=True).scroll_into_view_if_needed()
    page.screenshot(path=str(SHOTS / "mobile-security-dark.png"), full_page=True)
    page.get_by_role("button", name="Switch to light mode").click()
    page.get_by_role("button", name="Update Password", exact=True).scroll_into_view_if_needed()
    page.screenshot(path=str(SHOTS / "mobile-security-light.png"), full_page=True)
    page.get_by_role("heading", name="Account Settings", exact=True).scroll_into_view_if_needed()
    page.screenshot(path=str(SHOTS / "mobile-light.png"), full_page=True)

    page.locator("#currentPassword").fill(current)
    page.locator("#newPassword").fill("界" * 25)
    page.locator("#confirmPassword").fill("界" * 25)
    page.get_by_role("button", name="Update Password", exact=True).click()
    expect(page.locator("#newPassword-error")).to_contain_text("72 UTF-8 bytes")
    next_password = "NewSecurePassword456!"
    page.locator("#newPassword").fill(next_password)
    page.locator("#confirmPassword").fill("DoesNotMatch456")
    page.get_by_role("button", name="Update Password", exact=True).click()
    expect(page.locator("#confirmPassword-error")).to_contain_text("Passwords do not match")
    page.locator("#confirmPassword").fill(next_password)
    page.get_by_role("button", name="Show new password", exact=True).click()
    expect(page.locator("#newPassword")).to_have_attribute("type", "text")
    page.get_by_role("button", name="Hide new password", exact=True).click()
    page.locator("#currentPassword").fill("IncorrectPassword123")
    page.get_by_role("button", name="Update Password", exact=True).click()
    expect(page.get_by_role("alert")).to_contain_text("Check your current password")
    page.locator("#currentPassword").fill(current)
    other = browser_context.new_page()
    other.goto("/account-settings")
    expect(other.locator("#newPassword")).to_be_visible()
    page.bring_to_front()
    expect(page.get_by_role("heading", name="Account Settings", exact=True)).to_be_visible()
    with page.expect_response("**/api/v1/users/me/password") as changed:
        page.get_by_role("button", name="Update Password", exact=True).click()
    assert changed.value.status == 204
    assert set(changed.value.request.post_data_json) == {"currentPassword", "newPassword"}
    assert changed.value.request.header_value("x-xsrf-token")
    page.wait_for_url("**/login")
    other.wait_for_url("**/login?**")
    expect(page.locator("mat-snack-bar-container")).to_contain_text("Please sign in again with your new password")
    assert not any(cookie["name"] == "finance_session" for cookie in browser_context.cookies())
    assert page.request.get(website + "/api/v1/auth/session").status == 401
    assert page.evaluate("Object.keys(localStorage).filter(k => k !== 'finance-theme')") == []
    assert page.evaluate("Object.keys(sessionStorage)") == []
    page.locator("#email").fill(email)
    page.locator("#password").fill(current)
    page.get_by_role("button", name="Sign In", exact=True).click()
    expect(page.get_by_role("alert")).to_contain_text("Incorrect email or password")
    page.locator("#password").fill(next_password)
    page.get_by_role("button", name="Sign In", exact=True).click()
    page.wait_for_url("**/dashboard")
    page.goto("/account-settings")
    expect(profile).to_contain_text("Updated Browser Name")
    expect(page.locator("#newPassword")).to_have_value("")
    page.reload()
    expect(profile).to_contain_text("Updated Browser Name")
    other.close()
    page.close()


def test_account_loading_retry_csrf_failure_and_expired_session(browser_context):
    page = browser_context.new_page()
    _, _, current = register_and_login(page, "Account Error Browser")
    page.route("**/api/v1/users/me", lambda route: route.abort(), times=1)
    page.goto("/account-settings")
    expect(page.get_by_role("alert")).to_contain_text("Unable to connect")
    page.get_by_role("button", name="Retry", exact=True).click()
    expect(page.locator("#newPassword")).to_be_visible()
    page.locator("#currentPassword").fill(current)
    page.locator("#newPassword").fill("NewPasswordForErrors456!")
    page.locator("#confirmPassword").fill("NewPasswordForErrors456!")
    calls = []

    def forbidden(route):
        calls.append(route.request.method)
        route.fulfill(status=403, content_type="application/json", body='{"status":403,"code":"FORBIDDEN","message":"sensitive-detail"}')

    page.route("**/api/v1/users/me/password", forbidden)
    page.get_by_role("button", name="Update Password", exact=True).click()
    expect(page.get_by_role("alert")).to_contain_text("Refresh the page")
    assert calls == ["PUT"]
    expect(page.locator("body")).not_to_contain_text("sensitive-detail")
    assert page.url.endswith("/account-settings")
    page.unroute("**/api/v1/users/me/password")
    page.route("**/api/v1/users/me/password", lambda route: route.fulfill(status=401, content_type="application/json", body='{"status":401,"code":"UNAUTHORIZED"}'))
    page.get_by_role("button", name="Update Password", exact=True).click()
    page.wait_for_url("**/login?**")
    expect(page.locator("app-account-settings")).to_have_count(0)
    expect(page.locator("#password")).to_have_value("")
    page.close()
