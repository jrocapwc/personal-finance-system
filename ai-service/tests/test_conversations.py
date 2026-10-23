import os
from uuid import uuid4
from unittest.mock import AsyncMock, Mock, patch

import mongomock
import pytest
from bson import ObjectId
from httpx import ASGITransport, AsyncClient
from pymongo import MongoClient
from pymongo.errors import ConnectionFailure

from app.core.config import Settings
from app.core.mongodb import MongoDatabase
from app.main import create_app
from app.schemas.chat import ContextualChatRequest as ChatRequest
from app.schemas.conversations import ConversationCreate
from app.services.chat_provider import ProviderUnavailable
from app.services.conversation_repository import ConversationRepository
from app.services.openai_chat_provider import OpenAIChatProvider

pytestmark = pytest.mark.anyio
BASE = "/api/v1/chat/conversations"
OWNER = str(uuid4())
OTHER = str(uuid4())


@pytest.fixture
async def conversation_api(api_key):
    application = create_app(Settings(api_key))
    async with application.router.lifespan_context(application):
        repository = ConversationRepository(mongomock.MongoClient(tz_aware=True).db)
        application.state.conversations = repository
        provider = Mock(respond=AsyncMock(return_value="Contextual answer"))
        application.state.chatbot.provider = provider
        async with AsyncClient(transport=ASGITransport(app=application), base_url="http://test",
                               headers={"X-Internal-API-Key": api_key, "X-Owner-Id": OWNER}) as api:
            yield api, repository, provider


async def test_create_continue_list_read_and_isolate(conversation_api):
    api, repository, provider = conversation_api
    response = await api.post(BASE, json={"mode": "GENERAL", "title": "Python"})
    assert response.status_code == 200
    conversation = response.json()
    identifier = conversation["id"]
    assert ObjectId.is_valid(identifier)
    assert "ownerId" not in conversation
    assert "month" not in conversation
    assert conversation["createdAt"].endswith("Z") or conversation["createdAt"].endswith("+00:00")
    for question in ["Explain decorators", "Give another example"]:
        answer = await api.post(f"{BASE}/{identifier}/messages", json={"question": question})
        assert answer.status_code == 200
        assert answer.json()["provider"] == "openai"
    context = provider.respond.await_args.args[0]
    assert context.financialSummary is None
    assert [message.role for message in context.history] == ["user", "assistant"]
    assert context.history[0].content == "Explain decorators"
    saved = (await api.get(f"{BASE}/{identifier}")).json()
    assert [row["role"] for row in saved["messages"]] == ["user", "assistant", "user", "assistant"]
    assert len((await api.get(f"{BASE}/{identifier}?page=1&size=2")).json()["messages"]) == 2
    assert (await api.get(BASE)).json()["items"][0]["id"] == identifier
    document = repository.conversations.find_one()
    assert document["ownerId"] == OWNER and isinstance(document["_id"], ObjectId)
    assert repository.messages.count_documents({}) == 4
    assert "financialSummary" not in document
    api.headers["X-Owner-Id"] = OTHER
    assert (await api.get(BASE)).json()["items"] == []
    assert (await api.get(f"{BASE}/{identifier}")).status_code == 404
    assert (await api.post(f"{BASE}/{identifier}/messages", json={"question": "secret?"})).status_code == 404
    assert repository.messages.count_documents({}) == 4


async def test_finance_uses_current_summary_and_fallback(conversation_api, payload_factory):
    api, repository, provider = conversation_api
    identifier = (await api.post(BASE, json={"mode": "FINANCE", "month": 10, "year": 2026})).json()["id"]
    provider.respond.side_effect = ProviderUnavailable()
    for income in ["1000.00", "2000.00"]:
        answer = await api.post(f"{BASE}/{identifier}/messages", json={"question": "Explain my savings",
                                "financialSummary": payload_factory(income=income)})
        assert answer.status_code == 200
        assert answer.json()["provider"] == "rule-based"
        assert income in answer.json()["answer"]
        assert "educational" in answer.json()["disclaimer"]
    assert len(provider.respond.await_args.args[0].history) == 2
    assert repository.messages.count_documents({}) == 4
    assert "financialSummary" not in str(list(repository.messages.find()))
    assert (await api.post(f"{BASE}/{identifier}/messages", json={"question": "Explain savings"})).status_code == 400


@pytest.mark.parametrize("body", [{"mode": "WRONG"}, {"mode": "FINANCE"},
    {"mode": "FINANCE", "month": 13, "year": 2026}, {"mode": "GENERAL", "month": 1},
    {"mode": "GENERAL", "ownerId": OTHER}, {"mode": "GENERAL", "title": " "}])
async def test_invalid_creation(conversation_api, body):
    api, _, _ = conversation_api
    assert (await api.post(BASE, json=body)).status_code == 422


@pytest.mark.parametrize("question", ["", " ", "x" * 1001])
async def test_invalid_messages(conversation_api, question):
    api, _, provider = conversation_api
    identifier = (await api.post(BASE, json={"mode": "GENERAL"})).json()["id"]
    assert (await api.post(f"{BASE}/{identifier}/messages", json={"question": question})).status_code == 422
    provider.respond.assert_not_awaited()


async def test_ids_pagination_and_internal_auth(conversation_api):
    api, _, _ = conversation_api
    assert (await api.get(f"{BASE}/invalid")).status_code == 400
    assert (await api.get(f"{BASE}/{ObjectId()}")).status_code == 404
    assert (await api.get(BASE + "?size=101")).status_code == 422
    assert (await api.get(BASE + "?page=-1")).status_code == 422
    api.headers["X-Internal-API-Key"] = "wrong"
    assert (await api.get(BASE)).status_code == 401
    del api.headers["X-Internal-API-Key"]
    assert (await api.post(BASE, json={"mode": "GENERAL"})).status_code == 401


async def test_unavailable_storage_and_failed_write_never_claim_success(conversation_api):
    api, repository, _ = conversation_api
    identifier = (await api.post(BASE, json={"mode": "GENERAL"})).json()["id"]
    with patch.object(repository.messages, "insert_many", side_effect=ConnectionFailure("secret marker")):
        response = await api.post(f"{BASE}/{identifier}/messages", json={"question": "hello"})
        assert response.status_code == 503
        assert response.json()["detail"]["code"] == "PERSISTENCE_UNAVAILABLE"
        assert "secret marker" not in response.text
    assert repository.messages.count_documents({}) == 0
    with patch.object(repository.conversations, "find_one", side_effect=ConnectionFailure()):
        assert (await api.get(f"{BASE}/{identifier}")).status_code == 503
    with patch.object(repository.conversations, "insert_one", side_effect=ConnectionFailure()):
        assert (await api.post(BASE, json={"mode": "GENERAL"})).status_code == 503


async def test_provider_failure_general_does_not_persist(conversation_api):
    api, repository, provider = conversation_api
    identifier = (await api.post(BASE, json={"mode": "GENERAL"})).json()["id"]
    provider.respond.side_effect = ProviderUnavailable()
    assert (await api.post(f"{BASE}/{identifier}/messages", json={"question": "hello"})).status_code == 503
    assert repository.messages.count_documents({}) == 0


async def test_partial_insert_and_activity_update_failure_compensate(conversation_api):
    api, repository, _ = conversation_api
    identifier = (await api.post(BASE, json={"mode": "GENERAL"})).json()["id"]
    def partial_insert(rows, **kwargs):
        repository.messages.insert_one(rows[0])
        raise ConnectionFailure("write interrupted")
    with patch.object(repository.messages, "insert_many", side_effect=partial_insert):
        assert (await api.post(f"{BASE}/{identifier}/messages", json={"question": "hello"})).status_code == 503
    assert repository.messages.count_documents({}) == 0
    with patch.object(repository.conversations, "update_one", side_effect=ConnectionFailure()):
        assert (await api.post(f"{BASE}/{identifier}/messages", json={"question": "hello"})).status_code == 503
    assert repository.messages.count_documents({}) == 0


async def test_general_rejects_financial_payload_and_other_conversation_has_no_context(conversation_api, payload_factory):
    api, repository, provider = conversation_api
    first = (await api.post(BASE, json={"mode": "GENERAL"})).json()["id"]
    second = (await api.post(BASE, json={"mode": "GENERAL"})).json()["id"]
    assert (await api.post(f"{BASE}/{first}/messages", json={"question": "Private conversation A"})).status_code == 200
    assert (await api.post(f"{BASE}/{second}/messages", json={"question": "hello", "financialSummary": payload_factory()})).status_code == 400
    assert (await api.post(f"{BASE}/{second}/messages", json={"question": "hello"})).status_code == 200
    assert provider.respond.await_args.args[0].history == []
    assert repository.messages.count_documents({}) == 4


async def test_stateless_chat_does_not_depend_on_mongo_or_accept_history(conversation_api):
    api, repository, _ = conversation_api
    with patch.object(repository.conversations, "find_one", side_effect=ConnectionFailure()):
        response = await api.post("/api/v1/chat/respond", json={"question": "Explain gravity", "mode": "GENERAL"})
        assert response.status_code == 200
        assert (await api.post("/api/v1/chat/respond", json={"question": "hello", "mode": "GENERAL", "history": []})).status_code == 422
    assert repository.messages.count_documents({}) == 0


async def test_application_shutdown_closes_mongo_and_environment(monkeypatch):
    monkeypatch.setenv("MONGODB_URI", "mongodb://127.0.0.1:27018")
    monkeypatch.setenv("MONGODB_DATABASE", "isolated_configuration")
    monkeypatch.setenv("INTERNAL_API_KEY", "x" * 32)
    configured = Settings.from_environment()
    assert configured.mongodb_database == "isolated_configuration"
    assert configured.mongodb_uri.endswith(":27018")
    assert configured.mongodb_uri not in repr(configured)
    with patch("app.main.MongoDatabase") as constructor:
        application = create_app(configured)
        async with application.router.lifespan_context(application):
            constructor.return_value.close.assert_not_called()
        constructor.return_value.close.assert_called_once()


async def test_bounded_history_and_deterministic_listing(conversation_api):
    api, _, provider = conversation_api
    identifiers = [(await api.post(BASE, json={"mode": "GENERAL"})).json()["id"] for _ in range(3)]
    for i in range(9):
        assert (await api.post(f"{BASE}/{identifiers[0]}/messages", json={"question": f"Question {i}"})).status_code == 200
    assert len(provider.respond.await_args.args[0].history) == 12
    assert provider.respond.await_args.args[0].history[0].content == "Question 2"
    pages = [(await api.get(BASE + f"?page={i}&size=1")).json()["items"][0]["id"] for i in range(3)]
    assert pages[0] == identifiers[0] and len(set(pages)) == 3


async def test_provider_treats_history_as_untrusted_context():
    with patch("app.services.openai_chat_provider.AsyncOpenAI") as constructor:
        sdk = constructor.return_value
        sdk.responses.create = AsyncMock(return_value=Mock(status="completed", output_text="Safe answer"))
        provider = OpenAIChatProvider(Settings("x" * 32, "fake-key", "test-model"))
        await provider.respond(ChatRequest(question="Continue", mode="GENERAL",
                               history=[{"role": "assistant", "content": "Prior content"}]))
        arguments = sdk.responses.create.await_args.kwargs
        assert "untrusted" in arguments["instructions"]
        assert "authoritative financial records" in arguments["instructions"]
        assert "Prior content" in arguments["input"][0]["content"]
        assert OWNER not in str(arguments) and "fake-key" not in str(arguments)


async def test_connection_options_and_shutdown():
    with patch("app.core.mongodb.MongoClient") as constructor:
        connection = MongoDatabase(Settings("x" * 32))
        assert constructor.call_args.kwargs["serverSelectionTimeoutMS"] == 1500
        assert constructor.call_args.kwargs["connect"] is False
        connection.close()
        constructor.return_value.close.assert_called_once()


async def test_blocking_database_work_runs_off_event_loop(conversation_api):
    import threading
    api, repository, _ = conversation_api
    event_thread = threading.get_ident()
    original = repository.listing
    def checked(*args):
        assert threading.get_ident() != event_thread
        return original(*args)
    with patch.object(repository, "listing", side_effect=checked):
        assert (await api.get(BASE)).status_code == 200


@pytest.mark.skipif(os.getenv("RUN_MONGODB_INTEGRATION") != "1", reason="Opt-in isolated local MongoDB integration")
async def test_real_local_mongodb():
    # Fixed local host; random test namespace. Never open or delete development databases.
    name = "personal_finance_ai_test_" + uuid4().hex
    client = MongoClient("mongodb://127.0.0.1:27017", serverSelectionTimeoutMS=1500, tz_aware=True)
    try:
        assert client.admin.command("ping")["ok"] == 1
        repository = ConversationRepository(client[name])
        conversation = repository.create(OWNER, ConversationCreate(mode="GENERAL"))
        from app.schemas.chat import ChatResponse
        repository.save_turn(OWNER, conversation["id"], "hello", ChatResponse(answer="world", provider="openai", disclaimer="Verify"))
        assert len(repository.read(OWNER, conversation["id"], 0, 100)["messages"]) == 2
        assert len(client[name].conversations.index_information()) == 2
        assert len(client[name].messages.index_information()) == 2
        from fastapi import HTTPException
        with pytest.raises(HTTPException) as error:
            repository.owned(OTHER, conversation["id"])
        assert error.value.status_code == 404
    finally:
        assert name.startswith("personal_finance_ai_test_") and len(name) == len("personal_finance_ai_test_") + 32
        client.drop_database(name)
        client.close()
