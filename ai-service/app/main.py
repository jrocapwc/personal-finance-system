from contextlib import asynccontextmanager
import logging
from openai import OpenAIError

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.insights import router
from app.core.config import Settings
from app.api.chat import router as chat_router
from app.services.chatbot_service import ChatbotService
from app.services.openai_chat_provider import OpenAIChatProvider
from app.services.chat_provider import log_provider_failure
from app.core.mongodb import MongoDatabase
from app.services.conversation_repository import ConversationRepository
from app.api.conversations import router as conversations_router
from starlette.concurrency import run_in_threadpool


def create_app(settings: Settings | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(application: FastAPI):
        application.state.settings = settings if settings is not None else Settings.from_environment()
        configured = application.state.settings
        mongo = MongoDatabase(configured)
        application.state.conversations = ConversationRepository(mongo.database)
        logging.getLogger(__name__).info(
            "OpenAI chat configuration key_present=%s model_present=%s timeout_seconds=%s max_output_tokens=%s",
            bool(configured.openai_api_key), bool(configured.openai_model),
            configured.openai_timeout_seconds, configured.openai_max_output_tokens,
        )
        try:
            provider = OpenAIChatProvider(configured) if configured.openai_api_key and configured.openai_model else None
            if provider is None:
                log_provider_failure("missing_configuration")
        except (OpenAIError, ValueError) as exception:
            log_provider_failure("provider_setup", exception, getattr(exception, "status_code", None))
            # Optional provider setup must not disable health or the existing insights API.
            provider = None
        application.state.chatbot = ChatbotService(provider, configured.openai_timeout_seconds)
        try:
            yield
        finally:
            await run_in_threadpool(mongo.close)
            if provider is not None:
                await provider.close()

    application = FastAPI(title="Financial Insights Service", version="0.1.0", lifespan=lifespan,
                          description="Standalone educational rules engine. Financial values must be decimal strings.")
    application.include_router(router)
    application.include_router(chat_router)
    application.include_router(conversations_router)

    @application.get("/health", tags=["Health"])
    def health() -> dict[str, str]:
        return {"status": "healthy"}

    @application.exception_handler(RequestValidationError)
    async def validation_error(_request: Request, exception: RequestValidationError) -> JSONResponse:
        # FastAPI's default includes rejected input. Never echo a financial body or an accidental secret.
        details = [{"loc": error["loc"], "msg": error["msg"], "type": error["type"]}
                   for error in exception.errors()]
        return JSONResponse(status_code=422, content={"detail": details})

    return application


app = create_app()
