"""Opt-in browser regression against production Angular artifacts and isolated services.

RUN_BACKEND_E2E=1 RUN_BROWSER_E2E=1 enables this test. Install Playwright in
the QA Python environment or include .tools/browser-qa in PYTHONPATH.
Uses installed Chrome/Edge; no developer browser profile or live AI services.
"""
import base64
import hashlib
import hmac
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import secrets
import threading
import time
from urllib.parse import unquote, urlsplit
from uuid import uuid4

import pytest
from qa.test_backend_e2e import backend, ROOT

pytestmark = pytest.mark.skipif(os.getenv("RUN_BROWSER_E2E") != "1", reason="Opt-in isolated browser E2E")


@pytest.fixture(scope="module")
def website(backend):
    artifacts = ROOT / "frontend/personal-finance-ui/dist/personal-finance-ui/browser"
    assert (artifacts / "index.csr.html").exists(), "Build Angular first"
    target = urlsplit(backend["core"])

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def handle_request(self):
            if urlsplit(self.path).path.startswith("/api/"):
                connection = HTTPConnection(target.hostname, target.port, timeout=25)
                try:
                    body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
                    headers = {k: v for k, v in self.headers.items() if k.lower() not in ("host", "connection")}
                    connection.request(self.command, self.path, body, headers)
                    response = connection.getresponse()
                    data = response.read()
                    self.send_response(response.status)
                    for key, value in response.getheaders():
                        if key.lower() not in ("transfer-encoding", "connection", "content-length"):
                            self.send_header(key, value)
                    self.send_header("Content-Length", str(len(data)))
                    self.end_headers()
                    self.wfile.write(data)
                finally:
                    connection.close()
                return
            requested = unquote(urlsplit(self.path).path).lstrip("/")
            file = (artifacts / requested).resolve()
            if not file.is_relative_to(artifacts.resolve()):
                self.send_error(404)
                return
            if file.is_dir() and (file / "index.html").exists():
                file = file / "index.html"
            elif not file.is_file():
                file = artifacts / "index.csr.html"
            content_type = {".html": "text/html", ".js": "application/javascript", ".css": "text/css",
                            ".svg": "image/svg+xml", ".woff2": "font/woff2"}.get(file.suffix, "application/octet-stream")
            data = file.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = handle_request

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}"
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


@pytest.fixture
def browser_context(website):
    from playwright.sync_api import sync_playwright
    executable = os.getenv("QA_BROWSER_EXECUTABLE") or next((str(path) for path in [
        Path("C:/Program Files/Google/Chrome/Application/chrome.exe"),
        Path("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"),
    ] if path.exists()), None)
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=executable, headless=True)
        context = browser.new_context(base_url=website, viewport={"width": 1440, "height": 1000})
        context.set_default_timeout(20000)
        # Public font CDNs are unnecessary for testing and may not be reachable.
        context.route("https://fonts.googleapis.com/**", lambda route: route.abort())
        context.route("https://fonts.gstatic.com/**", lambda route: route.abort())
        try:
            yield context
        finally:
            context.close()
            browser.close()


def register_and_login(page, name):
    email = f"browser-{uuid4().hex}@example.test"
    password = secrets.token_urlsafe(18)
    page.goto("/register")
    page.wait_for_load_state("networkidle")
    page.locator("#name").fill(name)
    page.locator("#email").fill(email)
    page.locator("#password").fill(password)
    page.locator("#confirmPassword").fill(password)
    page.get_by_role("button", name="Create account", exact=True).click()
    try:
        page.wait_for_url("**/login?registered=true")
    except Exception:
        raise AssertionError("Registration did not navigate: " + str(page.locator('[role=alert]').all_text_contents())) from None
    page.locator("#email").fill(email)
    page.locator("#password").fill(password)
    with page.expect_response("**/api/v1/auth/login") as logged_in:
        page.get_by_role("button", name="Sign In", exact=True).click()
    assert logged_in.value.status == 200
    page.wait_for_url("**/dashboard")
    page.locator("app-dashboard .summary-grid").wait_for()
    # Chromium can discard the fetch response body as prerendered pages navigate.
    # Read the authoritative restored profile through the stable API context.
    result = page.request.get('/api/v1/auth/session').json()
    assert "accessToken" not in result and result["user"]["email"] == email
    return result["user"], email, password


def test_refresh_cookie_csrf_chat_logout_and_switching(browser_context, website):
    from playwright.sync_api import expect
    page = browser_context.new_page()
    user, _, _ = register_and_login(page, "Browser User A")
    cookie = next(cookie for cookie in browser_context.cookies() if cookie["name"] == "finance_session")
    assert cookie["httpOnly"] and cookie["sameSite"] == "Lax" and cookie["path"] == "/"
    assert "finance_session" not in page.evaluate("document.cookie")
    assert page.evaluate("Object.keys(localStorage).filter(k => k !== 'finance-theme')") == []
    assert page.evaluate("Object.keys(sessionStorage)") == []
    page.reload()
    expect(page.locator("app-dashboard .summary-grid")).to_be_visible()
    assert page.url.endswith("/dashboard")
    page.goto("/transactions?search=browser")
    expect(page.locator("app-transactions")).to_be_visible()
    # A raw browser request carries the auth cookie but no Angular XSRF header.
    assert page.request.post(website + "/api/v1/categories", data={"name": "Blocked", "type": "EXPENSE"}).status == 403
    assert page.request.get(website + "/api/v1/auth/session").json()["user"]["id"] == user["id"]
    attacker = browser_context.new_page()
    attacker.goto(website.replace("127.0.0.1", "localhost") + "/login")
    assert attacker.evaluate("""async target => {
      try {
        await fetch(target + '/api/v1/categories', {method: 'POST', credentials: 'include',
          headers: {'Content-Type': 'application/json'}, body: JSON.stringify({name: 'Cross-origin', type: 'EXPENSE'})});
        return 'allowed';
      } catch { return 'blocked'; }
    }""", website) == "blocked"
    attacker.close()
    page.goto("/dashboard")
    page.wait_for_load_state('networkidle')
    page.get_by_role("button", name="Switch to dark mode").click()
    expect(page.locator(".dark-theme")).to_be_visible()
    page.wait_for_load_state('networkidle')
    page.reload()
    expect(page.locator(".dark-theme")).to_be_visible()
    page.get_by_role("button", name="Open AI Assistant", exact=True).click()
    panel = page.get_by_role("region", name="AI Finance Assistant")
    panel.get_by_role("button", name="General", exact=True).click()
    # Session restoration must also tolerate a missing readable CSRF cookie.
    page.evaluate("document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'")
    panel.get_by_role("textbox", name="Message AI Assistant").fill("Browser user A private context")
    panel.get_by_role("button", name="Send message").click()
    expect(panel.locator(".message.assistant")).to_contain_text("Mock general")
    panel.get_by_role("button", name="Finance", exact=True).click()
    panel.get_by_role("textbox", name="Message AI Assistant").fill("Explain my savings")
    panel.get_by_role("button", name="Send message").click()
    expect(panel.locator(".message.assistant")).to_contain_text("Mock finance")
    page.reload()
    page.get_by_role("button", name="Open AI Assistant", exact=True).click()
    panel = page.get_by_role("region", name="AI Finance Assistant")
    panel.get_by_role("button", name="History", exact=True).click()
    expect(panel.locator(".conversation-history")).to_contain_text("Browser user A private context")
    second = browser_context.new_page()
    second.goto("/budgets")
    expect(second.locator("app-budgets")).to_be_visible()
    page.get_by_role("button", name="Logout", exact=True).click()
    page.wait_for_url("**/login")
    second.wait_for_url("**/login?**")
    assert not any(cookie["name"] == "finance_session" for cookie in browser_context.cookies())
    assert page.locator("app-ai-assistant").count() == 0
    assert page.request.get(website + "/api/v1/auth/session").status == 401
    page.reload()
    expect(page.locator("app-login")).to_be_visible()
    register_and_login(page, "Browser User B")
    page.get_by_role("button", name="Open AI Assistant", exact=True).click()
    panel = page.get_by_role("region", name="AI Finance Assistant")
    panel.get_by_role("button", name="History", exact=True).click()
    expect(panel.locator(".conversation-history")).to_contain_text("No conversations yet")
    expect(page.locator("body")).not_to_contain_text("Browser user A private context")
    page.set_viewport_size({"width": 390, "height": 844})
    assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")


def test_expiration_network_retry_and_intended_destination(browser_context, backend, website):
    from playwright.sync_api import expect
    page = browser_context.new_page()
    user, email, password = register_and_login(page, "Browser Expiration")
    page.route("**/api/v1/auth/session", lambda route: route.abort())
    page.goto("/budgets")
    expect(page.get_by_role("heading", name="Unable to check your session")).to_be_visible()
    assert page.locator("app-budgets").count() == 0
    page.unroute("**/api/v1/auth/session")
    page.get_by_role("button", name="Retry", exact=True).click()
    expect(page.locator("app-budgets")).to_be_visible()
    # Controlled test credential already expired; no sleeps or production lifetime changes.
    encode = lambda value: base64.urlsafe_b64encode(value).decode().rstrip("=")
    now = int(time.time())
    header = encode(json.dumps({"alg": "HS256"}).encode())
    payload = encode(json.dumps({"iss": "personal-finance-core-api", "aud": ["personal-finance-core-api"],
                                 "sub": user["id"], "iat": now-120, "nbf": now-120, "exp": now-60}).encode())
    unsigned = header + "." + payload
    expired = unsigned + "." + encode(hmac.new(backend["signing_bytes"], unsigned.encode(), hashlib.sha256).digest())
    browser_context.add_cookies([{"name": "finance_session", "value": expired, "url": website,
                                  "httpOnly": True, "sameSite": "Lax"}])
    page.reload()
    expect(page.locator("app-login")).to_be_visible()
    assert "returnUrl=%2Fbudgets" in page.url
    assert page.locator("app-budgets").count() == 0
    page.locator("#email").fill(email)
    page.locator("#password").fill(password)
    page.get_by_role("button", name="Sign In", exact=True).click()
    page.wait_for_url("**/budgets")
    expect(page.locator("app-budgets")).to_be_visible()


def test_bank_connections_csv_review_themes_and_mobile(browser_context, website):
    """Real cookie/CSRF browser workflow with fictional statements and isolated H2."""
    from playwright.sync_api import expect
    page = browser_context.new_page()
    register_and_login(page, "Bank Import Browser")
    page.get_by_role("link", name="Bank Connections").click()
    expect(page.get_by_role("heading", name="Bank Connections", exact=True)).to_be_visible()
    expect(page.get_by_text("SIMULATION", exact=True)).to_be_visible()
    assert page.locator("input[type=password]").count() == 0
    page.get_by_role("button", name="Connect demo account", exact=True).click()
    expect(page.get_by_text("Demo connected", exact=True)).to_be_visible()
    page.get_by_role("button", name="Sync demo transactions", exact=True).click()
    expect(page.locator('[role=status]').filter(has_text="Import result")).to_contain_text("5 imported")
    page.get_by_role("button", name="Sync demo transactions", exact=True).click()
    expect(page.locator('[role=status]').filter(has_text="Import result")).to_contain_text("0 imported")
    page.get_by_role("button", name="Disconnect", exact=True).click()
    expect(page.get_by_role("button", name="Connect demo account", exact=True)).to_be_visible()
    statement = b'Date,Description,Amount,Reference,Currency\n2026-10-15,"Fictional Food, shop",-25.50,qa-food-1,PHP\n2026-02-30,Invalid date,-2.00,qa-invalid,PHP\n2026-10-16,Salary,100.25,qa-salary-1,PHP\n'

    def upload_and_preview():
        page.locator('input[type=file]').set_input_files({"name": "fictional.csv", "mimeType": "text/csv", "buffer": statement})
        page.get_by_label("Stable account label", exact=False).fill("fictional-checking")
        page.get_by_label("I confirm the date order", exact=False).check()
        page.get_by_role("button", name="Validate and preview", exact=True).click()
        expect(page.get_by_role("heading", name="Review statement")).to_be_visible()

    upload_and_preview()
    expect(page.get_by_role("button", name="Confirm selected transactions")).to_be_disabled()
    expect(page.get_by_label("Include row 2", exact=True)).to_be_disabled()
    page.get_by_label("Category for row 1", exact=True).select_option(label="Food")
    page.get_by_label("Category for row 3", exact=True).select_option(label="Salary")
    expect(page.get_by_role("button", name="Confirm selected transactions")).to_be_enabled()
    screenshots = ROOT / "core-api/target/qa-bank-screenshots"
    screenshots.mkdir(parents=True, exist_ok=True)
    page.screenshot(path=str(screenshots / "light-desktop.png"), full_page=True)
    page.get_by_role("button", name="Switch to dark mode").click()
    expect(page.locator(".dark-theme")).to_be_visible()
    page.screenshot(path=str(screenshots / "dark-desktop.png"), full_page=True)
    page.set_viewport_size({"width": 390, "height": 844})
    page.wait_for_timeout(400)
    assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"), "Page must fit mobile width; preview scrolls internally"
    page.screenshot(path=str(screenshots / "dark-mobile.png"), full_page=True)
    page.get_by_role("button", name="Confirm selected transactions").click()
    expect(page.locator('[role=status]').filter(has_text="Import result")).to_contain_text("2 imported")
    page.set_viewport_size({"width": 1440, "height": 1000})
    upload_and_preview()
    expect(page.get_by_label("Include row 1", exact=True)).to_be_disabled()
    expect(page.get_by_label("Include row 3", exact=True)).to_be_disabled()
    page.get_by_role("button", name="Cancel import / change mapping", exact=True).click()
    expect(page.get_by_role("heading", name="Review statement")).not_to_be_visible()
    assert page.request.get(website + "/api/v1/transactions?size=100").json()["totalElements"] == 7
    page.close()
