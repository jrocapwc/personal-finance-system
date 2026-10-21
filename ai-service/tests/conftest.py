from decimal import Decimal, ROUND_HALF_UP
import secrets

import pytest
from httpx import ASGITransport, AsyncClient

from app.core.config import Settings
from app.main import create_app


@pytest.fixture
def api_key():
    return secrets.token_urlsafe(32)


@pytest.fixture
def anyio_backend():
    return "asyncio"


@pytest.fixture
async def client(api_key):
    application = create_app(Settings(api_key))
    async with application.router.lifespan_context(application):
        async with AsyncClient(transport=ASGITransport(app=application), base_url="http://testserver",
                               headers={"X-Internal-API-Key": api_key}) as client:
            yield client


@pytest.fixture
def payload_factory():
    def build(income="1000.00", expenses="200.00", categories=None, budgets=None):
        income_value = Decimal(income)
        expenses_value = Decimal(expenses)
        savings = income_value - expenses_value
        rate = None if income_value == 0 else str((savings * 100 / income_value).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))
        return {
            "month": 10, "year": 2026,
            "totalIncome": income, "totalExpenses": expenses,
            "netSavings": str(savings), "savingsRate": rate,
            "expensesByCategory": categories if categories is not None else [
                {"categoryId": 1, "categoryName": "Food", "amount": expenses}],
            "budgetUtilization": budgets if budgets is not None else []
        }
    return build


@pytest.fixture(autouse=True)
def block_live_openai(monkeypatch):
    from openai.resources.responses import AsyncResponses
    from unittest.mock import AsyncMock
    monkeypatch.setattr(AsyncResponses, "create", AsyncMock(side_effect=AssertionError("Live OpenAI calls are forbidden in tests")))
