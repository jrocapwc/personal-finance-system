import hmac
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, Security
from fastapi.security import APIKeyHeader

from app.schemas.financial import FinancialSummary, InsightResponse
from app.services.insight_engine import analyze

router = APIRouter(prefix="/api/v1/insights", tags=["Insights"])
internal_key = APIKeyHeader(name="X-Internal-API-Key", auto_error=False)


def require_internal_key(request: Request, supplied: Annotated[str | None, Security(internal_key)]) -> None:
    expected = request.app.state.settings.internal_api_key
    if supplied is None or not hmac.compare_digest(supplied.encode("utf-8"), expected.encode("utf-8")):
        raise HTTPException(status_code=401, detail="Missing or invalid internal API key")


@router.post("/analyze", response_model=InsightResponse, dependencies=[Depends(require_internal_key)],
             responses={401: {"description": "Missing or invalid internal API key"}},
             summary="Analyze a monthly financial summary using deterministic rules")
def analyze_summary(financial: FinancialSummary) -> InsightResponse:
    return analyze(financial)
