from fastapi import APIRouter, Depends, Request, HTTPException
from app.api.insights import require_internal_key
from app.schemas.chat import ChatRequest, ChatResponse
from app.services.chat_provider import ProviderUnavailable
from app.services.chatbot_service import GENERAL_UNAVAILABLE

router = APIRouter(prefix="/api/v1/chat", tags=["Chat"])


@router.post("/respond", response_model=ChatResponse, dependencies=[Depends(require_internal_key)],
             responses={401: {"description": "Missing or invalid internal API key"},
                        503: {"description": "General chat provider unavailable"}},
             summary="Answer a general question or explain a supplied monthly financial summary")
async def respond_to_question(financial_question: ChatRequest, request: Request) -> ChatResponse:
    try:
        return await request.app.state.chatbot.respond(financial_question)
    except ProviderUnavailable:
        raise HTTPException(status_code=503, detail={"code": "CHAT_UNAVAILABLE", "message": GENERAL_UNAVAILABLE}) from None
