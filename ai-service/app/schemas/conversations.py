from typing import Annotated, Literal, Self
from pydantic import Field, StrictInt, model_validator
from app.schemas.financial import ContractModel, FinancialSummary


class ConversationCreate(ContractModel):
    mode: Literal["GENERAL", "FINANCE"]
    title: Annotated[str, Field(min_length=1, max_length=100)] = "New conversation"
    month: Annotated[StrictInt, Field(ge=1, le=12)] | None = None
    year: Annotated[StrictInt, Field(ge=2000, le=2100)] | None = None

    @model_validator(mode="after")
    def period(self) -> Self:
        if self.mode == "FINANCE" and (self.month is None or self.year is None):
            raise ValueError("FINANCE requires month and year")
        if self.mode == "GENERAL" and (self.month is not None or self.year is not None):
            raise ValueError("GENERAL must not include a financial period")
        return self


class ConversationTurn(ContractModel):
    question: Annotated[str, Field(min_length=1, max_length=1000)]
    financialSummary: FinancialSummary | None = None

    @model_validator(mode="after")
    def context_size(self) -> Self:
        if self.financialSummary and len(self.financialSummary.model_dump_json().encode("utf-8")) > 32768:
            raise ValueError("Financial context exceeds the 32 KiB chat limit")
        return self
