"""Minimal aggregate contract; no user identity, credentials, or raw transactions."""

import re
from decimal import Decimal, ROUND_HALF_UP, localcontext
from typing import Annotated, Self

from pydantic import BaseModel, BeforeValidator, ConfigDict, Field, StrictInt, model_validator

CENT = Decimal("0.01")
HUNDRED = Decimal("100")


def decimal_string(value: object) -> Decimal:
    # Requiring strings avoids JSON parsers first converting money to binary floats.
    if isinstance(value, Decimal) and value.is_finite():
        return value
    if not isinstance(value, str) or len(value) > 64 or not re.fullmatch(r"-?\d+(?:\.\d{1,2})?", value):
        raise ValueError("Use a decimal string with at most two decimal places")
    return Decimal(value)


Money = Annotated[Decimal, BeforeValidator(decimal_string, json_schema_input_type=str),
                  Field(max_digits=24, decimal_places=2)]
NonnegativeMoney = Annotated[Money, Field(ge=Decimal("0"))]
PositiveMoney = Annotated[Money, Field(gt=Decimal("0"))]
Rate = Annotated[Decimal, BeforeValidator(decimal_string, json_schema_input_type=str),
                 Field(max_digits=28, decimal_places=2)]
CategoryId = Annotated[StrictInt, Field(ge=1, le=9223372036854775807)]
CategoryName = Annotated[str, Field(min_length=1, max_length=100)]


def percentage(numerator: Decimal, denominator: Decimal) -> Decimal:
    with localcontext() as context:
        context.prec = 64
        return (numerator * HUNDRED / denominator).quantize(CENT, rounding=ROUND_HALF_UP)


class ContractModel(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True, str_strip_whitespace=True,
                              hide_input_in_errors=True)


class ExpenseCategory(ContractModel):
    categoryId: CategoryId
    categoryName: CategoryName
    amount: NonnegativeMoney


class BudgetUtilization(ContractModel):
    categoryId: CategoryId
    monthlyLimit: PositiveMoney
    spentAmount: NonnegativeMoney
    alertThreshold: Annotated[StrictInt, Field(ge=1, le=99)]


class FinancialSummary(ContractModel):
    month: Annotated[StrictInt, Field(ge=1, le=12)]
    year: Annotated[StrictInt, Field(ge=2000, le=2100)]
    totalIncome: NonnegativeMoney
    totalExpenses: NonnegativeMoney
    netSavings: Money
    savingsRate: Rate | None
    expensesByCategory: Annotated[list[ExpenseCategory], Field(max_length=1000)]
    budgetUtilization: Annotated[list[BudgetUtilization], Field(max_length=1000)]

    @model_validator(mode="after")
    def check_consistency(self) -> Self:
        with localcontext() as context:
            context.prec = 64
            if self.netSavings != self.totalIncome - self.totalExpenses:
                raise ValueError("netSavings must equal totalIncome minus totalExpenses")
            expected_rate = None if self.totalIncome == 0 else percentage(self.netSavings, self.totalIncome)
            if self.savingsRate != expected_rate:
                raise ValueError("savingsRate must match the two-decimal HALF_UP calculation; use null for zero income")
            for entries in (self.expensesByCategory, self.budgetUtilization):
                if len({entry.categoryId for entry in entries}) != len(entries):
                    raise ValueError("Each categoryId must occur at most once in each list")
            if sum((entry.amount for entry in self.expensesByCategory), Decimal("0")) > self.totalExpenses:
                raise ValueError("Category spending cannot exceed totalExpenses")
            if sum((entry.spentAmount for entry in self.budgetUtilization), Decimal("0")) > self.totalExpenses:
                raise ValueError("Budget spending cannot exceed totalExpenses")
        return self


class InsightResponse(ContractModel):
    summary: str
    positiveObservations: list[str]
    concerns: list[str]
    recommendations: list[str]
    suggestedActions: list[str]
    disclaimer: str
