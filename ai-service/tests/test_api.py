import logging

import pytest

from app.core.config import Settings
from app.main import create_app

pytestmark = pytest.mark.anyio

PATH = "/api/v1/insights/analyze"


async def test_health_is_public(client):
    client.headers.clear()
    response = await client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "healthy"}


async def test_valid_summary_and_disclaimer(client, payload_factory):
    response = await client.post(PATH, json=payload_factory())
    assert response.status_code == 200
    result = response.json()
    assert set(result) == {"summary", "positiveObservations", "concerns", "recommendations", "suggestedActions", "disclaimer"}
    assert "income was 1000.00" in result["summary"]
    assert "expenses were 200.00" in result["summary"]
    assert "net savings were 800.00" in result["summary"]
    assert result["positiveObservations"]
    for word in ("educational", "not professional financial", "investment", "tax", "legal"):
        assert word in result["disclaimer"]


@pytest.mark.parametrize("field", ["month", "year", "totalIncome", "totalExpenses", "netSavings", "savingsRate", "expensesByCategory", "budgetUtilization"])
async def test_missing_fields(client, payload_factory, field):
    payload = payload_factory()
    del payload[field]
    assert (await client.post(PATH, json=payload)).status_code == 422


@pytest.mark.parametrize(("field", "value"), [
    ("month", 0), ("month", 13), ("month", "10"), ("month", True),
    ("year", 1999), ("year", 2101), ("year", None),
    ("totalExpenses", "-0.01"), ("totalIncome", "-1"),
    ("totalExpenses", "200.001"), ("totalIncome", 1000),
    ("totalExpenses", 200.0), ("totalIncome", True),
    ("totalIncome", "NaN"), ("totalIncome", "Infinity"),
    ("totalIncome", "1e3"), ("totalIncome", "9" * 25),
    ("netSavings", "999"), ("savingsRate", "79.99"),
    ("savingsRate", None), ("expensesByCategory", None),
    ("budgetUtilization", {}),
])
async def test_invalid_fields(client, payload_factory, field, value):
    payload = payload_factory()
    payload[field] = value
    assert (await client.post(PATH, json=payload)).status_code == 422


@pytest.mark.parametrize("categories", [
    [{"categoryId": 1, "categoryName": "Food", "amount": "-1"}],
    [{"categoryId": 1, "categoryName": " ", "amount": "1"}],
    [{"categoryId": True, "categoryName": "Food", "amount": "1"}],
    [{"categoryId": 1, "categoryName": "Food", "amount": "201"}],
    [{"categoryId": 1, "categoryName": "A", "amount": "1"},
     {"categoryId": 1, "categoryName": "B", "amount": "1"}],
])
async def test_invalid_categories(client, payload_factory, categories):
    assert (await client.post(PATH, json=payload_factory(categories=categories))).status_code == 422


@pytest.mark.parametrize(("field", "value"), [("monthlyLimit", "0"), ("spentAmount", "-1"), ("alertThreshold", 0), ("alertThreshold", 100), ("alertThreshold", "80"), ("spentAmount", "201")])
async def test_invalid_budgets(client, payload_factory, field, value):
    budget = {"categoryId": 1, "monthlyLimit": "500", "spentAmount": "200", "alertThreshold": 80}
    budget[field] = value
    assert (await client.post(PATH, json=payload_factory(budgets=[budget]))).status_code == 422


async def test_duplicate_budgets_and_large_lists_rejected(client, payload_factory):
    budget = {"categoryId": 1, "monthlyLimit": "500", "spentAmount": "10", "alertThreshold": 80}
    assert (await client.post(PATH, json=payload_factory(budgets=[budget, budget]))).status_code == 422
    assert (await client.post(PATH, json=payload_factory(categories=[{"categoryId": i + 1, "categoryName": "Zero", "amount": "0"} for i in range(1001)]))).status_code == 422


@pytest.mark.parametrize("body", ["{", "[]", "null"])
async def test_malformed_payload(client, body):
    response = await client.post(PATH, content=body, headers={"Content-Type": "application/json"})
    assert response.status_code == 422


@pytest.mark.parametrize("location", ["root", "category", "budget"])
async def test_unknown_fields_and_secrets_not_echoed(client, payload_factory, location, caplog):
    secret = "accidental-credential-value-never-echo"
    payload = payload_factory(budgets=[{"categoryId": 1, "monthlyLimit": "500", "spentAmount": "200", "alertThreshold": 80}])
    target = payload if location == "root" else payload["expensesByCategory" if location == "category" else "budgetUtilization"][0]
    target["password"] = secret
    with caplog.at_level(logging.DEBUG):
        response = await client.post(PATH, json=payload)
    assert response.status_code == 422
    assert secret not in response.text
    assert secret not in caplog.text
    assert all("input" not in error for error in response.json()["detail"])


async def test_payload_values_not_logged(client, payload_factory, caplog):
    payload = payload_factory(categories=[{"categoryId": 1, "categoryName": "Private-category-marker", "amount": "200"}])
    with caplog.at_level(logging.DEBUG):
        assert (await client.post(PATH, json=payload)).status_code == 200
    assert "Private-category-marker" not in caplog.text
    assert "totalIncome" not in caplog.text


async def test_missing_key(client, payload_factory):
    client.headers.clear()
    response = await client.post(PATH, json=payload_factory())
    assert response.status_code == 401
    assert response.json() == {"detail": "Missing or invalid internal API key"}


async def test_invalid_key(client, payload_factory, api_key):
    response = await client.post(PATH, json=payload_factory(), headers={"X-Internal-API-Key": "wrong-key"})
    assert response.status_code == 401
    assert api_key not in response.text
    assert "wrong-key" not in response.text


async def test_zero_income_requires_null_rate(client, payload_factory):
    payload = payload_factory(income="0", expenses="0", categories=[])
    payload["savingsRate"] = "0"
    assert (await client.post(PATH, json=payload)).status_code == 422


async def test_openapi_declares_header_security_and_decimal_strings(client):
    schema = (await client.get("/openapi.json")).json()
    operation = schema["paths"][PATH]["post"]
    assert operation["security"] == [{"APIKeyHeader": []}]
    assert schema["components"]["securitySchemes"]["APIKeyHeader"]["name"] == "X-Internal-API-Key"
    assert schema["components"]["schemas"]["FinancialSummary"]["properties"]["totalIncome"]["type"] == "string"


async def test_environment_key_loaded_at_startup(monkeypatch, api_key):
    monkeypatch.setenv("INTERNAL_API_KEY", api_key)
    application = create_app()
    async with application.router.lifespan_context(application):
        assert application.state.settings.internal_api_key == api_key


@pytest.mark.parametrize("key", [None, "short"])
async def test_missing_or_short_configuration_fails_closed(monkeypatch, key):
    if key is None:
        monkeypatch.delenv("INTERNAL_API_KEY", raising=False)
    else:
        monkeypatch.setenv("INTERNAL_API_KEY", key)
    with pytest.raises(ValueError, match="INTERNAL_API_KEY must contain at least 32 bytes"):
        application = create_app()
        async with application.router.lifespan_context(application):
            pass


async def test_settings_repr_redacts_key(api_key):
    assert api_key not in repr(Settings(api_key))
