from typing import Annotated, Literal, Self
from pydantic import Field, StrictInt, model_validator
from app.schemas.financial import ContractModel, FinancialSummary


class ChatRequest(ContractModel):
    question: Annotated[str, Field(min_length=1, max_length=1000)]
    month: Annotated[StrictInt, Field(ge=1, le=12)]
    year: Annotated[StrictInt, Field(ge=2000, le=2100)]
    financialSummary: FinancialSummary

    @model_validator(mode="after")
    def consistent_context(self) -> Self:
        if (self.month, self.year) != (self.financialSummary.month, self.financialSummary.year):
            raise ValueError("Question period must match the financial summary")
        if len(self.financialSummary.model_dump_json().encode("utf-8")) > 32768:
            raise ValueError("Financial context exceeds the 32 KiB chat limit")
        return self


class ChatResponse(ContractModel):
    answer: Annotated[str, Field(min_length=1, max_length=8000)]
    provider: Literal["openai", "rule-based"]
    disclaimer: str
