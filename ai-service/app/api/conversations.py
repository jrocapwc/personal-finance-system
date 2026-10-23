from typing import Annotated
from uuid import UUID
from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request
from pymongo.errors import PyMongoError
from starlette.concurrency import run_in_threadpool
from app.api.insights import require_internal_key
from app.schemas.chat import ContextualChatRequest as ChatRequest
from app.schemas.conversations import ConversationCreate, ConversationTurn
from app.services.chat_provider import ProviderUnavailable
from app.services.chatbot_service import GENERAL_UNAVAILABLE, GENERAL_DISCLAIMER
from app.services.insight_engine import DISCLAIMER

router = APIRouter(prefix="/api/v1/chat/conversations", tags=["Conversations"],
                   dependencies=[Depends(require_internal_key)])


def trusted_owner(x_owner_id: Annotated[str, Header()]) -> str:
    try:
        return str(UUID(x_owner_id))
    except ValueError:
        raise HTTPException(400, detail={"code": "INVALID_REQUEST", "message": "Invalid owner identity"}) from None


Owner = Annotated[str, Depends(trusted_owner)]


async def database_call(function, *args):
    try:
        return await run_in_threadpool(function, *args)
    except PyMongoError:
        raise HTTPException(503, detail={"code": "PERSISTENCE_UNAVAILABLE", "message": "Conversation storage unavailable; persistence was not confirmed"}) from None


@router.post("")
async def create(body: ConversationCreate, request: Request, owner: Owner):
    return await database_call(request.app.state.conversations.create, owner, body)


@router.get("")
async def listing(request: Request, owner: Owner, page: int = Query(0, ge=0, le=100000), size: int = Query(20, ge=1, le=100)):
    return await database_call(request.app.state.conversations.listing, owner, page, size)


@router.get("/{conversation_id}")
async def read(conversation_id: str, request: Request, owner: Owner,
               page: int = Query(0, ge=0, le=100000), size: int = Query(100, ge=1, le=100)):
    result = await database_call(request.app.state.conversations.read, owner, conversation_id, page, size)
    return {**result, "disclaimer": GENERAL_DISCLAIMER if result["mode"] == "GENERAL" else DISCLAIMER}


@router.post("/{conversation_id}/messages")
async def send(conversation_id: str, body: ConversationTurn, request: Request, owner: Owner):
    repository = request.app.state.conversations
    document, history = await database_call(repository.context, owner, conversation_id)
    if document["mode"] == "GENERAL" and body.financialSummary is not None:
        raise HTTPException(400, detail={"code": "INVALID_REQUEST", "message": "GENERAL cannot include financial data"})
    if document["mode"] == "FINANCE" and (body.financialSummary is None or
            (body.financialSummary.month, body.financialSummary.year) != (document["month"], document["year"])):
        raise HTTPException(400, detail={"code": "INVALID_REQUEST", "message": "A current matching financial summary is required"})
    question = ChatRequest(question=body.question, mode=document["mode"], month=document.get("month"),
                           year=document.get("year"), financialSummary=body.financialSummary, history=history)
    try:
        response = await request.app.state.chatbot.respond(question)
    except ProviderUnavailable:
        raise HTTPException(503, detail={"code": "CHAT_UNAVAILABLE", "message": GENERAL_UNAVAILABLE}) from None
    await database_call(repository.save_turn, owner, conversation_id, body.question, response)
    return response
