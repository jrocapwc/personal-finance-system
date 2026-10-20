from decimal import Decimal

import pytest

from app.schemas.financial import FinancialSummary
from app.services.insight_engine import analyze


def result(payload):
    return analyze(FinancialSummary.model_validate(payload))


def test_money_is_decimal(payload_factory):
    model = FinancialSummary.model_validate(payload_factory())
    assert isinstance(model.totalIncome, Decimal)
    assert isinstance(model.expensesByCategory[0].amount, Decimal)


def test_positive_savings_and_highest_category(payload_factory):
    insights = result(payload_factory(categories=[
        {"categoryId": 2, "categoryName": "Food", "amount": "50"},
        {"categoryId": 1, "categoryName": "Housing", "amount": "150"},
    ]))
    assert "highest spending category was Housing at 150.00" in insights.summary
    assert any("retained 800.00" in item for item in insights.positiveObservations)
    assert any("15.00" in item for item in insights.suggestedActions)
    assert insights.concerns == []


def test_negative_savings_and_rate(payload_factory):
    insights = result(payload_factory(income="100", expenses="150"))
    assert any("exceeded income by 50.00" in item for item in insights.concerns)
    assert any("negative (-50.00%)" in item for item in insights.concerns)
    assert insights.positiveObservations == []
    assert insights.recommendations
    assert insights.suggestedActions


@pytest.mark.parametrize(("expenses", "low"), [("80.01", True), ("80.00", False), ("100", True)])
def test_savings_rate_threshold(payload_factory, expenses, low):
    insights = result(payload_factory(income="100", expenses=expenses))
    assert any("below the 20.00%" in item for item in insights.concerns) == low


def test_no_income(payload_factory):
    insights = result(payload_factory(income="0", expenses="20"))
    assert any("cannot be calculated" in item for item in insights.concerns)
    assert "None" not in insights.model_dump_json()
    assert "NaN" not in insights.model_dump_json()


def test_empty_lists_and_zero_activity(payload_factory):
    insights = result(payload_factory(income="0", expenses="0", categories=[], budgets=[]))
    assert "No income or expenses were reported" in insights.summary
    assert "highest spending" not in insights.summary
    assert insights.positiveObservations == []
    assert insights.suggestedActions


def test_missing_category_breakdown_is_safe(payload_factory):
    insights = result(payload_factory(categories=[]))
    assert any("Provide category totals" in item for item in insights.suggestedActions)


@pytest.mark.parametrize(("spent", "expected"), [("79.99", None), ("80", "WARNING"), ("99.99", "WARNING"), ("100", "EXCEEDED"), ("120", "EXCEEDED")])
def test_budget_thresholds(payload_factory, spent, expected):
    insights = result(payload_factory(budgets=[{"categoryId": 1, "monthlyLimit": "100", "spentAmount": spent, "alertThreshold": 80}]))
    concerns = [item for item in insights.concerns if item.startswith("Budget for")]
    assert len(concerns) == (0 if expected is None else 1)
    if expected:
        assert expected in concerns[0]


def test_budget_threshold_uses_exact_amounts_not_display_rounding(payload_factory):
    insights = result(payload_factory(budgets=[{"categoryId": 1, "monthlyLimit": "10000", "spentAmount": "7999.99", "alertThreshold": 80}], expenses="7999.99", income="10000"))
    assert not any("WARNING" in item for item in insights.concerns)


def test_deterministic_ties_and_budget_order(payload_factory):
    categories = [{"categoryId": 2, "categoryName": "B", "amount": "100"},
                  {"categoryId": 1, "categoryName": "A", "amount": "100"}]
    budgets = [{"categoryId": i, "monthlyLimit": "100", "spentAmount": "100", "alertThreshold": 80} for i in (2, 1)]
    forward = result(payload_factory(categories=categories, budgets=budgets))
    reverse = result(payload_factory(categories=categories[::-1], budgets=budgets[::-1]))
    assert forward == reverse
    assert "highest spending category was A" in forward.summary
    assert "category 1" in forward.concerns[0]


def test_decimal_rounding_without_binary_float_errors(payload_factory):
    insights = result(payload_factory(income="0.30", expenses="0.10"))
    assert "net savings were 0.20" in insights.summary
    assert any("66.67%" in item for item in insights.positiveObservations)
