from typing import Protocol
from app.schemas.chat import ChatRequest


class ProviderUnavailable(Exception):
    """Safe provider failure without upstream exception text or request content."""


class ChatProvider(Protocol):
    async def respond(self, request: ChatRequest) -> str: ...
