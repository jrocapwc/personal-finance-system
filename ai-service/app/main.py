from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.insights import router
from app.core.config import Settings


def create_app(settings: Settings | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(application: FastAPI):
        application.state.settings = settings if settings is not None else Settings.from_environment()
        yield

    application = FastAPI(title="Financial Insights Service", version="0.1.0", lifespan=lifespan,
                          description="Standalone educational rules engine. Financial values must be decimal strings.")
    application.include_router(router)

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
