"""Real local HTTP workflow, isolated H2; external OpenAI and MongoDB are mocked.

Opt in with RUN_BACKEND_E2E=1 after packaging Spring. No running development service is used.
"""
import base64
from decimal import Decimal
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen, build_opener, HTTPCookieProcessor
from http.cookiejar import CookieJar
from uuid import uuid4

import pytest

ROOT = Path(__file__).resolve().parents[1]
pytestmark = pytest.mark.skipif(os.getenv("RUN_BACKEND_E2E") != "1", reason="Opt-in isolated three-service E2E")


def port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


class BrowserSession:
    """Real cookie jar; credentials are never returned to the application contract."""
    def __init__(self):
        self.cookies = CookieJar()
        self.opener = build_opener(HTTPCookieProcessor(self.cookies))


def call(base, path, method="GET", body=None, token=None, headers=None, expected=200, csrf=True):
    request_headers = {"Content-Type": "application/json", **(headers or {})}
    browser = token if isinstance(token, BrowserSession) else None
    # Give unauthenticated mutation tests valid CSRF so they exercise authentication.
    if csrf and method not in ("GET", "HEAD", "OPTIONS") and not token:
        browser = BrowserSession()
    if browser and csrf and method not in ("GET", "HEAD", "OPTIONS"):
        with browser.opener.open(Request(base + "/api/v1/auth/csrf"), timeout=20) as bootstrap:
            assert bootstrap.status == 200
        xsrf = next(cookie.value for cookie in browser.cookies if cookie.name == "XSRF-TOKEN")
        request_headers["X-XSRF-TOKEN"] = xsrf
    elif token and not browser:
        request_headers["Authorization"] = "Bearer " + token
    payload = None if body is None else json.dumps(body).encode()
    request = Request(base + path, data=payload, headers=request_headers, method=method)
    try:
        response = (browser.opener.open if browser else urlopen)(request, timeout=20)
    except HTTPError as error:
        response = error
    with response:
        assert response.status == expected, f"{method} {path}: HTTP {response.status}, expected {expected}"
        data = response.read()
        return json.loads(data, parse_float=Decimal) if data else None


def stop(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)


@pytest.fixture(scope="module")
def backend(tmp_path_factory):
    directory = tmp_path_factory.mktemp("backend-e2e")
    java_home = Path(os.environ.get("JAVA_HOME", "")) / "bin" / ("java.exe" if os.name == "nt" else "java")
    java = str(java_home) if java_home.is_file() else shutil.which("java")
    local_node = ROOT / ".tools/node-lts/node_modules/node/bin/node.exe"
    node = os.getenv("QA_NODE_EXECUTABLE") or (str(local_node) if local_node.exists() else shutil.which("node"))
    target = ROOT / "core-api/target"
    classpath_file = target / "test-runtime-classpath.txt"
    assert java and node and classpath_file.exists(), "Run Maven verify and install Node 24 LTS before running QA"
    classpath = os.pathsep.join([str(target / "test-classes"), str(target / "classes"),
                                 classpath_file.read_text(encoding="utf-8").strip()])
    version = subprocess.check_output([node, "--version"], text=True).strip()
    assert version.startswith("v24."), "QA requires Node 24 LTS"
    core_port, ai_port, node_port = port(), port(), port()
    assert len({core_port, ai_port, node_port}) == 3
    ai_key, integration_key = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
    signing_bytes = secrets.token_bytes(32)
    environment = dict(os.environ)
    for key in ["SPRING_APPLICATION_JSON", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"]:
        environment.pop(key, None)
    environment.update({
        "OPENAI_API_KEY": "", "OPENAI_MODEL": "", "QA_INTERNAL_API_KEY": ai_key,
        "INTEGRATION_INTERNAL_API_KEY": integration_key, "PORT": str(node_port),
        "AI_INTERNAL_API_KEY": ai_key, "AI_SERVICE_BASE_URL": f"http://127.0.0.1:{ai_port}",
        "AI_SERVICE_READ_TIMEOUT": "10s", "INTEGRATION_SERVICE_BASE_URL": f"http://127.0.0.1:{node_port}",
        "JWT_SECRET_BASE64": base64.b64encode(signing_bytes).decode(), "SPRING_PROFILES_ACTIVE": "qa,local",
        "SPRING_DATASOURCE_URL": "jdbc:h2:mem:qa_" + uuid4().hex + ";DB_CLOSE_DELAY=-1",
        "SPRING_DATASOURCE_USERNAME": "sa", "SPRING_DATASOURCE_PASSWORD": "",
        "SPRING_DATASOURCE_DRIVER_CLASS_NAME": "org.h2.Driver", "SPRING_H2_CONSOLE_ENABLED": "false",
        "SPRING_FLYWAY_LOCATIONS": "classpath:db/migration",
        "SPRING_JPA_PROPERTIES_HIBERNATE_TYPE_PREFERRED_UUID_JDBC_TYPE": "UUID",
        "PYTHONPATH": os.pathsep.join([str(ROOT), str(ROOT / "ai-service")]),
        "LOGGING_LEVEL_ROOT": "WARN", "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB": "INFO",
    })
    processes = []
    streams = []

    def launch(name, command, cwd):
        stream = (directory / (name + ".log")).open("w", encoding="utf-8")
        streams.append(stream)
        process = subprocess.Popen(command, cwd=cwd, env=environment, stdout=stream, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        processes.append(process)
        return process

    bases = {"core": f"http://127.0.0.1:{core_port}", "ai": f"http://127.0.0.1:{ai_port}", "node": f"http://127.0.0.1:{node_port}"}
    try:
        node_process = launch("node", [node, "src/server.js"], ROOT / "integration-service")
        ai_process = launch("ai", [sys.executable, "-m", "uvicorn", "qa.ai_app:create_test_app", "--factory",
                                    "--host", "127.0.0.1", "--port", str(ai_port), "--no-access-log", "--log-level", "warning"], ROOT)
        launch("core", [java, "-cp", classpath, "core_api.CoreApiApplication", f"--server.port={core_port}"], directory)
        for name, health in [("node", "/health"), ("ai", "/health"), ("core", "/actuator/health")]:
            deadline = time.monotonic() + 90
            while True:
                assert all(process.poll() is None for process in processes), f"QA service exited during startup; inspect isolated logs in {directory}"
                try:
                    call(bases[name], health)
                    break
                except (URLError, TimeoutError):
                    assert time.monotonic() < deadline, f"QA {name} startup timed out"
                    time.sleep(0.25)
        yield {**bases, "ai_key": ai_key, "integration_key": integration_key, "signing_bytes": signing_bytes,
               "node_process": node_process, "ai_process": ai_process}
    finally:
        for process in reversed(processes):
            stop(process)
        for stream in streams:
            stream.close()


def register(backend):
    email, password = f"qa-{uuid4().hex}@example.test", secrets.token_urlsafe(18)
    browser = BrowserSession()
    profile = call(backend["core"], "/api/v1/auth/register", "POST", {"name": "Fictional QA User", "email": email, "password": password}, token=browser, expected=201)
    assert "password" not in profile and "passwordHash" not in profile
    login = call(backend["core"], "/api/v1/auth/login", "POST", {"email": email, "password": password}, token=browser)
    call(backend["core"], "/api/v1/auth/login", "POST", {"email": email, "password": password + "wrong"}, token=browser, expected=401)
    assert "accessToken" not in login and login["expiresIn"] == 3600
    assert call(backend["core"], "/api/v1/auth/session", token=browser)["user"] == login["user"]
    return browser


def test_complete_backend_workflow(backend):
    core = backend["core"]
    a, b = register(backend), register(backend)
    categories = call(core, "/api/v1/categories", token=a)
    food = next(row["id"] for row in categories if row["name"] == "Food" and row["type"] == "EXPENSE")
    salary = next(row["id"] for row in categories if row["name"] == "Salary" and row["type"] == "INCOME")
    b_categories = call(core, "/api/v1/categories", token=b)
    b_food = next(row["id"] for row in b_categories if row["name"] == "Food")

    def transaction(category, kind, amount, date="2026-10-01", token=a, identifier=None):
        body = {"categoryId": category, "type": kind, "amount": amount, "description": "Fictional QA record", "transactionDate": date}
        return call(core, "/api/v1/transactions" + (f"/{identifier}" if identifier else ""),
                    "PUT" if identifier else "POST", body, token, expected=200 if identifier else 201)

    def dashboard(token=a):
        return call(core, "/api/v1/dashboard?month=10&year=2026", token=token)

    transaction(salary, "INCOME", "1000.10")
    expense = transaction(food, "EXPENSE", "100.05")
    transaction(food, "EXPENSE", "0.05", "2026-10-31")
    transaction(salary, "INCOME", "9999.99", "2026-09-30")
    transaction(food, "EXPENSE", "999.99", "2026-11-01")
    call(core, "/api/v1/transactions", "POST", {"categoryId": b_food, "type": "EXPENSE", "amount": "1.00", "transactionDate": "2026-10-01"}, a, expected=404)
    call(core, "/api/v1/transactions", "POST", {"categoryId": food, "type": "EXPENSE", "amount": "1.001", "transactionDate": "2026-10-01"}, a, expected=400)
    budget_body = {"categoryId": food, "month": 10, "year": 2026, "monthlyLimit": "125.00", "alertThreshold": 80}
    budget = call(core, "/api/v1/budgets", "POST", budget_body, a, expected=201)
    assert budget["budgetStatus"] == "WARNING" and budget["spentAmount"] == Decimal("100.10")
    safe = call(core, f"/api/v1/budgets/{budget['id']}", "PUT", {**budget_body, "monthlyLimit": "200.00"}, a)
    assert safe["budgetStatus"] == "SAFE"
    call(core, f"/api/v1/budgets/{budget['id']}", "PUT", budget_body, a)
    transaction(food, "EXPENSE", "150.10", identifier=expense["id"])
    exceeded = call(core, f"/api/v1/budgets/{budget['id']}", token=a)
    assert exceeded["budgetStatus"] == "EXCEEDED" and exceeded["spentAmount"] == Decimal("150.15")
    call(core, f"/api/v1/transactions/{expense['id']}", "DELETE", token=a, expected=204)
    assert dashboard()["summary"]["totalExpenses"] == Decimal("0.05")
    expense = transaction(food, "EXPENSE", "100.05")
    summary = dashboard()["summary"]
    assert summary == {"totalIncome": Decimal("1000.10"), "totalExpenses": Decimal("100.10"), "netSavings": Decimal("900.00"), "savingsRate": Decimal("89.99")}
    assert dashboard(b)["summary"]["totalIncome"] == 0
    for path in [f"/api/v1/transactions/{expense['id']}", f"/api/v1/budgets/{budget['id']}"]:
        call(core, path, token=b, expected=404)
    insights = call(core, "/api/v1/insights?month=10&year=2026", token=a)
    assert "1000.10" in insights["summary"] and "100.10" in insights["summary"]
    assert "educational" in insights["disclaimer"].lower()
    general = call(core, "/api/v1/chat/messages", "POST", {"mode": "GENERAL", "question": "Explain Python"}, a)
    assert general["provider"] == "openai" and "Mock general" in general["answer"]
    finance = call(core, "/api/v1/chat/messages", "POST", {"mode": "FINANCE", "month": 10, "year": 2026, "question": "Explain my savings"}, a)
    assert "1000.10" in finance["answer"] and "100.10" in finance["answer"]
    fallback = call(core, "/api/v1/chat/messages", "POST", {"mode": "FINANCE", "month": 10, "year": 2026, "question": "Explain my savings simulate-provider-failure"}, a)
    assert fallback["provider"] == "rule-based" and "900.00" in fallback["answer"]
    call(core, "/api/v1/chat/messages", "POST", {"mode": "GENERAL", "question": "simulate-provider-failure"}, a, expected=503)
    call(core, "/api/v1/chat/messages", "POST", {"mode": "FINANCE", "question": "Explain savings"}, a, expected=400)
    empty_finance = call(core, "/api/v1/chat/messages", "POST", {"mode": "FINANCE", "month": 10, "year": 2026, "question": "Explain savings"}, b)
    assert "1000.10" not in empty_finance["answer"]

    base = "/api/v1/chat/conversations"
    conversation = call(core, base, "POST", {"mode": "GENERAL", "title": "Fictional QA chat"}, a)["id"]
    call(core, f"{base}/{conversation}/messages", "POST", {"question": "Project name: fictional Orion"}, a)
    continued = call(core, f"{base}/{conversation}/messages", "POST", {"question": "What was the project name?"}, a)
    assert "Orion" in continued["answer"]
    saved = call(core, f"{base}/{conversation}", token=a)
    assert [row["role"] for row in saved["messages"]] == ["user", "assistant", "user", "assistant"]
    assert "ownerId" not in saved
    assert call(core, base, token=b)["items"] == []
    call(core, f"{base}/{conversation}", token=b, expected=404)
    call(core, f"{base}/{conversation}/messages", "POST", {"question": "Private?"}, b, expected=404)
    call(core, f"{base}/invalid-id", token=a, expected=400)
    call(core, f"{base}/000000000000000000000001", token=a, expected=404)
    financial_conversation = call(core, base, "POST", {"mode": "FINANCE", "month": 10, "year": 2026}, a)["id"]
    initial = call(core, f"{base}/{financial_conversation}/messages", "POST", {"question": "Explain savings"}, a)
    assert "1000.10" in initial["answer"]

    imports = "/api/v1/transactions/import/mock"
    result = call(core, imports, "POST", token=a)
    assert result == {"importedCount": 5, "skippedCount": 0, "failedCount": 0, "totalProcessed": 5}
    repeated = call(core, imports, "POST", token=a)
    assert repeated == {"importedCount": 0, "skippedCount": 5, "failedCount": 0, "totalProcessed": 5}
    summary = dashboard()["summary"]
    assert summary == {"totalIncome": Decimal("38500.60"), "totalExpenses": Decimal("8645.85"), "netSavings": Decimal("29854.75"), "savingsRate": Decimal("77.54")}
    assert dashboard()["budgetUtilization"][0]["spentAmount"] == Decimal("525.85")
    current = call(core, f"{base}/{financial_conversation}/messages", "POST", {"question": "Explain savings now"}, a)
    assert "38500.60" in current["answer"] and "8645.85" in current["answer"]
    assert len(call(core, f"{base}/{financial_conversation}", token=a)["messages"]) == 4
    assert call(core, imports, "POST", token=b)["importedCount"] == 5
    assert dashboard(b)["summary"]["totalIncome"] == Decimal("37500.50")
    assert dashboard()["summary"] == summary

    # Real transport authentication on both independent internal services.
    call(backend["node"], "/internal/mock-transactions", expected=401)
    call(backend["node"], "/internal/mock-transactions", headers={"X-Internal-API-Key": "invalid"}, expected=401)
    call(backend["node"], "/internal/mock-transactions", headers={"X-Internal-API-Key": backend["integration_key"]})
    call(backend["ai"], base, headers={"X-Owner-Id": str(uuid4())}, expected=401)
    call(backend["ai"], base, headers={"X-Owner-Id": str(uuid4()), "X-Internal-API-Key": "invalid"}, expected=401)
    for path, method in [("/api/v1/dashboard?month=10&year=2026", "GET"), (imports, "POST"), (base, "GET")]:
        call(core, path, method, expected=401)
        call(core, path, method, token="invalid.jwt", expected=401)
    header, payload, _ = next(cookie.value for cookie in a.cookies if cookie.name == "finance_session").split(".")
    claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
    claims.update(iat=int(time.time()) - 120, nbf=int(time.time()) - 120, exp=int(time.time()) - 60)
    encode = lambda value: base64.urlsafe_b64encode(value).decode().rstrip("=")
    unsigned = header + "." + encode(json.dumps(claims).encode())
    expired = unsigned + "." + encode(hmac.new(backend["signing_bytes"], unsigned.encode(), hashlib.sha256).digest())
    call(core, base, token=expired, expected=401)

    # Only stop processes created by this fixture, never the developer's running services.
    stop(backend["ai_process"])
    assert call(core, "/api/v1/insights?month=10&year=2026", token=a, expected=502)["code"] == "INSIGHTS_UNAVAILABLE"
    stop(backend["node_process"])
    assert call(core, imports, "POST", token=a, expected=503)["code"] == "INTEGRATION_UNAVAILABLE"
    assert dashboard()["summary"] == summary


def test_cookie_logout_and_csrf(backend):
    core = backend["core"]
    browser = register(backend)
    call(core, "/api/v1/categories", "POST", {"name": "Blocked CSRF", "type": "EXPENSE"}, token=browser, csrf=False, expected=403)
    call(core, "/api/v1/auth/logout", "POST", {}, token=browser, csrf=False, expected=403)
    call(core, "/api/v1/auth/logout", "POST", {}, token=browser, expected=204)
    assert all(cookie.name != "finance_session" for cookie in browser.cookies)
    call(core, "/api/v1/auth/session", token=browser, expected=401)
    call(core, "/api/v1/chat/conversations", token=browser, expected=401)
