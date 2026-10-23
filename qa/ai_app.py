"""Test-only FastAPI factory: actual adapter/routers, fake OpenAI SDK and in-memory MongoDB."""
from contextlib import asynccontextmanager
import json
import os
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

import mongomock
from app.core.config import Settings
from app.main import create_app
from app.services.chat_provider import ProviderUnavailable


class FakeResponses:
    async def create(self, **arguments):
        assert arguments["model"] == "qa-mock-model"
        assert arguments["store"] is False and arguments["tools"] == []
        inputs = arguments["input"]
        content = inputs[-1]["content"]
        try:
            financial = json.loads(content)
        except json.JSONDecodeError:
            financial = None
        question = financial["user_question"] if isinstance(financial, dict) else content
        if "simulate-provider-failure" in question:
            raise ProviderUnavailable()
        if isinstance(financial, dict):
            summary = financial["supplied_financial_data"]
            answer = f"Mock finance: income={summary['totalIncome']}; expenses={summary['totalExpenses']}"
        else:
            previous = inputs[0]["content"] if len(inputs) > 1 else "No previous context"
            answer = "Mock general: " + question + "; " + previous
        return SimpleNamespace(status="completed", output_text=answer[:8000])


def create_test_app():
    # Required QA-specific variables prevent accidental use of developer .env files/keys.
    configured = Settings(internal_api_key=os.environ["QA_INTERNAL_API_KEY"],
                          openai_api_key="fictional-qa-key", openai_model="qa-mock-model",
                          mongodb_database="qa_never_connected")
    application = create_app(configured)
    lifespan = application.router.lifespan_context
    fake_client = SimpleNamespace(responses=FakeResponses(), close=AsyncMock())

    @asynccontextmanager
    async def isolated_lifespan(app):
        # Both constructor replacement and a live SDK tripwire forbid paid requests.
        memory = mongomock.MongoClient(tz_aware=True)
        database = SimpleNamespace(database=memory["qa"], close=memory.close)
        with patch("app.services.openai_chat_provider.AsyncOpenAI", return_value=fake_client), \
             patch("app.main.MongoDatabase", return_value=database), \
             patch("openai.resources.responses.AsyncResponses.create", side_effect=AssertionError("Live OpenAI is forbidden")):
            async with lifespan(app):
                yield

    application.router.lifespan_context = isolated_lifespan
    return application
