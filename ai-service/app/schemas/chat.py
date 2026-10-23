from typing import Annotated, Literal, Self
from pydantic import Field, StrictInt, model_validator
from app.schemas.financial import ContractModel, FinancialSummary


class HistoryMessage(ContractModel):
    role: Literal["user", "assistant"]
    content: Annotated[str, Field(min_length=1, max_length=8000)]


class ChatRequest(ContractModel):
    question: Annotated[str, Field(min_length=1, max_length=1000)]
    mode: Literal["GENERAL", "FINANCE"] = "FINANCE"
    month: Annotated[StrictInt, Field(ge=1, le=12)] | None = None
    year: Annotated[StrictInt, Field(ge=2000, le=2100)] | None = None
    financialSummary: FinancialSummary | None = None

    @model_validator(mode="after")
    def consistent_context(self) -> Self:
        if self.mode == "GENERAL":
            if self.financialSummary is not None:
                raise ValueError("GENERAL mode must not include a financial summary")
            return self
        if self.month is None or self.year is None or self.financialSummary is None:
            raise ValueError("FINANCE mode requires month, year and financialSummary")
        if (self.month, self.year) != (self.financialSummary.month, self.financialSummary.year):
            raise ValueError("Question period must match the financial summary")
        if len(self.financialSummary.model_dump_json().encode("utf-8")) > 32768:
            raise ValueError("Financial context exceeds the 32 KiB chat limit")
        return self


class ContextualChatRequest(ChatRequest):
    history: Annotated[list[HistoryMessage], Field(max_length=12)] = Field(default_factory=list)


class ChatResponse(ContractModel):
    answer: Annotated[str, Field(min_length=1, max_length=8000)]
    provider: Literal["openai", "rule-based"]
    disclaimer: str
