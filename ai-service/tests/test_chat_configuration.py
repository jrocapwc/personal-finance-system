from contextlib import nullcontext
import logging
import os
from types import SimpleNamespace
from unittest.mock import AsyncMock, Mock, patch
from pathlib import Path
import tempfile

import httpx2
import pytest
from httpx import ASGITransport, AsyncClient
from openai import APIConnectionError, APIStatusError, APITimeoutError

from app.core.config import Settings
from app.main import create_app
from app.services.insight_engine import DISCLAIMER

pytestmark = pytest.mark.anyio
PATH = "/api/v1/chat/respond"


@pytest.fixture
def local_env_file():
    with tempfile.NamedTemporaryFile(dir=Path(__file__).parent, prefix="chat-config-", suffix=".log", delete=False) as stream:
        path = Path(stream.name)
    try:
        yield path
    finally:
        path.unlink(missing_ok=True)


def configure_local_file(monkeypatch, path, api_key, model="test-model"):
    path.write_text(f"INTERNAL_API_KEY={api_key}\nOPENAI_API_KEY=test-provider-secret\nOPENAI_MODEL={model}\n", encoding="utf-8")
    monkeypatch.setattr("app.core.config.LOCAL_ENV_FILE", path)
    monkeypatch.delenv("INTERNAL_API_KEY", raising=False)
    return path


async def test_dotenv_startup_general_calls_openai(monkeypatch, local_env_file, api_key, caplog):
    configure_local_file(monkeypatch, local_env_file, api_key)
    sdk = Mock()
    sdk.responses.create = AsyncMock(return_value=SimpleNamespace(status="completed", output_text="Moonlight rests upon the sea."))
    sdk.close = AsyncMock()
    app = create_app()
    with caplog.at_level(logging.INFO), patch("app.services.openai_chat_provider.AsyncOpenAI", return_value=sdk) as constructor:
        async with app.router.lifespan_context(app):
            async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test",
                                   headers={"X-Internal-API-Key": api_key}) as client:
                response = await client.post(PATH, json={"mode": "GENERAL", "question": "Write a poem about the moon."})
    assert response.status_code == 200
    assert response.json()["provider"] == "openai"
    assert constructor.call_args.kwargs["api_key"] == "test-provider-secret"
    assert sdk.responses.create.await_args.kwargs["model"] == "test-model"
    assert sdk.responses.create.await_args.kwargs["input"] == [{"role": "user", "content": "Write a poem about the moon."}]
    sdk.responses.create.assert_awaited_once()
    assert "key_present=True model_present=True" in caplog.text
    assert "test-provider-secret" not in caplog.text
    assert api_key not in caplog.text
    assert "poem about the moon" not in caplog.text


async def test_process_environment_overrides_dotenv_without_mutation(monkeypatch, local_env_file, api_key):
    configure_local_file(monkeypatch, local_env_file, api_key)
    monkeypatch.setenv("OPENAI_API_KEY", "process-only-secret")
    monkeypatch.setenv("OPENAI_MODEL", "process-model")
    before = dict(os.environ)
    settings = Settings.from_environment()
    assert settings.openai_api_key == "process-only-secret"
    assert settings.openai_model == "process-model"
    assert settings.internal_api_key == api_key
    assert dict(os.environ) == before
    monkeypatch.setenv("OPENAI_API_KEY", "")
    assert Settings.from_environment().openai_api_key == ""


@pytest.mark.parametrize("kind", ["missing", "display_name", "setup"])
async def test_unconfigured_general_returns_503_but_finance_and_insights_work(api_key, payload_factory, kind, caplog):
    if kind == "missing":
        settings = Settings(api_key)
    else:
        settings = Settings(api_key, "private-key-marker", "GPT 4.1 Mini" if kind == "display_name" else "test-model")
    app = create_app(settings)
    constructor = patch("app.main.OpenAIChatProvider", side_effect=ValueError("private-setup-marker")) if kind == "setup" else nullcontext()
    with constructor, caplog.at_level(logging.WARNING):
        async with app.router.lifespan_context(app):
            async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test",
                                   headers={"X-Internal-API-Key": api_key}) as client:
                general = await client.post(PATH, json={"mode": "GENERAL", "question": "private-question-marker"})
                assert general.status_code == 503
                assert general.json()["detail"]["code"] == "CHAT_UNAVAILABLE"
                assert "provider" not in general.json()
                finance = await client.post(PATH, json={"question": "Explain my savings", "month": 10, "year": 2026,
                                                       "financialSummary": payload_factory()})
                assert finance.status_code == 200
                assert finance.json()["provider"] == "rule-based"
                assert finance.json()["disclaimer"] == DISCLAIMER
                assert (await client.post("/api/v1/insights/analyze", json=payload_factory())).status_code == 200
    expected_reason = "reason=missing_configuration" if kind == "missing" else "reason=provider_setup exception_class=ValueError"
    assert expected_reason in caplog.text
    assert all(marker not in caplog.text + general.text for marker in (
        "private-key-marker", "private-question-marker", "private-setup-marker", api_key))


@pytest.mark.parametrize("kind", [400, 401, 403, 404, 429, 500, "connection", "timeout", "incomplete", "blank"])
async def test_sdk_failures_are_diagnosable_and_general_is_503(api_key, kind, caplog):
    sdk = Mock()
    sdk.responses.create = AsyncMock()
    sdk.close = AsyncMock()
    request = httpx2.Request("POST", "https://api.openai.com/v1/responses")
    if isinstance(kind, int):
        sdk.responses.create.side_effect = APIStatusError("private-upstream-message", response=httpx2.Response(kind, request=request),
                                                         body={"code": "insufficient_quota" if kind == 429 else "model_not_found",
                                                               "message": "private-body-marker"})
    elif kind == "connection":
        sdk.responses.create.side_effect = APIConnectionError(message="private-network-marker", request=request)
    elif kind == "timeout":
        sdk.responses.create.side_effect = APITimeoutError(request=request)
    else:
        sdk.responses.create.return_value = SimpleNamespace(status="incomplete" if kind == "incomplete" else "completed", output_text="")
    app = create_app(Settings(api_key, "private-key-marker", "test-model"))
    with patch("app.services.openai_chat_provider.AsyncOpenAI", return_value=sdk), caplog.at_level(logging.WARNING):
        async with app.router.lifespan_context(app):
            async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test",
                                   headers={"X-Internal-API-Key": api_key}) as client:
                response = await client.post(PATH, json={"mode": "GENERAL", "question": "private-prompt-marker"})
    assert response.status_code == 503
    assert response.json()["detail"]["code"] == "CHAT_UNAVAILABLE"
    assert "answer" not in response.json()
    sdk.responses.create.assert_awaited_once()
    if isinstance(kind, int):
        assert f"exception_class=APIStatusError upstream_status={kind}" in caplog.text
        if kind == 429:
            assert "error_code=insufficient_quota" in caplog.text
    elif kind in ("connection", "timeout"):
        assert f"exception_class={'APIConnectionError' if kind == 'connection' else 'APITimeoutError'}" in caplog.text
    else:
        assert "reason=incomplete_or_empty_output" in caplog.text
    assert all(marker not in caplog.text + response.text for marker in (
        "private-upstream-message", "private-body-marker", "private-network-marker", "private-key-marker", "private-prompt-marker", api_key))
