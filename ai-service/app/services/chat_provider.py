from typing import Protocol
import logging
from app.schemas.chat import ChatRequest


class ProviderUnavailable(Exception):
    """Safe provider failure without upstream exception text or request content."""


def log_provider_failure(reason: str, exception: Exception | None = None, status: int | None = None) -> None:
    # Never log exception messages, bodies, headers, prompts, or arbitrary upstream error codes.
    code = getattr(exception, "code", None)
    safe_code = code if isinstance(code, str) and code in {
        "insufficient_quota", "rate_limit_exceeded", "model_not_found", "invalid_api_key",
        "unsupported_parameter", "invalid_value", "billing_hard_limit_reached",
    } else "unknown"
    logging.getLogger(__name__).warning(
        "OpenAI chat unavailable reason=%s exception_class=%s upstream_status=%s error_code=%s",
        reason, type(exception).__name__ if exception is not None else "none",
        status if type(status) is int else "none", safe_code,
    )


class ChatProvider(Protocol):
    async def respond(self, request: ChatRequest) -> str: ...
