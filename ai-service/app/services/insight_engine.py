"""Pure deterministic rules. No persistence, network calls, or payload logging."""

from decimal import Decimal, localcontext

from app.schemas.financial import FinancialSummary, InsightResponse, percentage

LOW_SAVINGS_RATE = Decimal("20")
REDUCTION_TARGET = Decimal("0.10")
DISCLAIMER = (
    "These insights are educational and are not professional financial, investment, tax, or legal advice. "
    "Consider your circumstances and consult a qualified professional when needed."
)


def analyze(financial: FinancialSummary) -> InsightResponse:
    # Explicit precision supports the aggregate contract's 24-digit amounts.
    with localcontext() as context:
        context.prec = 64
        return _analyze(financial)


def _analyze(financial: FinancialSummary) -> InsightResponse:
    positives: list[str] = []
    concerns: list[str] = []
    recommendations: list[str] = []
    actions: list[str] = []
    summary = (
        f"For {financial.year:04d}-{financial.month:02d}, income was {financial.totalIncome:.2f}, "
        f"expenses were {financial.totalExpenses:.2f}, and net savings were {financial.netSavings:.2f}."
    )

    if financial.totalIncome == 0 and financial.totalExpenses == 0:
        summary += " No income or expenses were reported."
        actions.append("Record income and expenses for this month before assessing spending patterns.")
    elif financial.netSavings > 0:
        positives.append(f"You retained {financial.netSavings:.2f} after expenses.")
    elif financial.netSavings < 0:
        concerns.append(f"Expenses exceeded income by {-financial.netSavings:.2f}.")
        recommendations.append("Review essential costs first, then reduce discretionary expenses to close the savings gap.")
        actions.append("Review recurring subscriptions and pause one nonessential expense.")

    if financial.totalIncome == 0:
        concerns.append("No income was reported; a savings rate cannot be calculated.")
        recommendations.append("Confirm that all income is recorded before assessing the month's savings rate.")
    elif financial.savingsRate < 0:
        concerns.append(f"Your savings rate is negative ({financial.savingsRate:.2f}%).")
    elif financial.savingsRate < LOW_SAVINGS_RATE:
        concerns.append(f"Your savings rate of {financial.savingsRate:.2f}% is below the 20.00% rule threshold.")
        recommendations.append("Consider gradually increasing savings toward 20% of income if your circumstances allow.")
    else:
        positives.append(f"Your savings rate of {financial.savingsRate:.2f}% meets the 20.00% rule threshold.")

    spending = sorted((entry for entry in financial.expensesByCategory if entry.amount > 0),
                      key=lambda entry: (-entry.amount, entry.categoryId))
    if spending:
        highest = spending[0]
        summary += f" The highest spending category was {highest.categoryName} at {highest.amount:.2f}."
        recommendations.append(f"Review {highest.categoryName} for discretionary costs that could be reduced.")
        target = (highest.amount * REDUCTION_TARGET).quantize(Decimal("0.01"), rounding="ROUND_HALF_UP")
        actions.append(f"Explore a 10% reduction in {highest.categoryName}, approximately {target:.2f}, if feasible.")
    elif financial.totalExpenses > 0:
        actions.append("Provide category totals to identify where expense reductions may be possible.")

    for budget in sorted(financial.budgetUtilization, key=lambda entry: entry.categoryId):
        label = f"category {budget.categoryId}"
        used = percentage(budget.spentAmount, budget.monthlyLimit)
        # Compare exact amounts, rather than the rounded percentage shown in text.
        if budget.spentAmount >= budget.monthlyLimit:
            concerns.append(f"Budget for {label} is EXCEEDED at {used:.2f}% of its limit.")
            actions.append(f"Review remaining essential costs for {label} before committing to further spending.")
        elif budget.spentAmount * Decimal("100") >= budget.monthlyLimit * budget.alertThreshold:
            concerns.append(f"Budget for {label} is WARNING at {used:.2f}% (threshold {budget.alertThreshold}%).")
            actions.append(f"Track {label} against the remaining budget of {budget.monthlyLimit - budget.spentAmount:.2f}.")

    return InsightResponse(summary=summary, positiveObservations=positives, concerns=concerns,
                           recommendations=recommendations, suggestedActions=actions, disclaimer=DISCLAIMER)
