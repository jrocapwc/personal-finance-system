from fastapi import APIRouter, Depends, Request
from app.api.insights import require_internal_key
from app.schemas.chat import ChatRequest, ChatResponse

router = APIRouter(prefix="/api/v1/chat", tags=["Chat"])


@router.post("/respond", response_model=ChatResponse, dependencies=[Depends(require_internal_key)],
             responses={401: {"description": "Missing or invalid internal API key"}},
             summary="Answer a question using the supplied monthly financial summary")
async def respond_to_question(financial_question: ChatRequest, request: Request) -> ChatResponse:
    return await request.app.state.chatbot.respond(financial_question)
