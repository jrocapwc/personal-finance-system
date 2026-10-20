import json
from pathlib import Path

from app.schemas.financial import FinancialSummary
from app.services.insight_engine import analyze


def test_documented_request_and_response_remain_consistent():
    examples = Path(__file__).resolve().parents[1] / "examples"
    payload = json.loads((examples / "analyze-request.json").read_text(encoding="utf-8"))
    expected = json.loads((examples / "analyze-response.json").read_text(encoding="utf-8"))
    assert analyze(FinancialSummary.model_validate(payload)).model_dump() == expected
