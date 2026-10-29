import asyncio
import re
from decimal import localcontext

from app.schemas.chat import ChatRequest, ChatResponse
from app.services.chat_provider import ChatProvider, ProviderUnavailable, log_provider_failure
from app.services.insight_engine import DISCLAIMER, analyze
from app.schemas.financial import FinancialSummary, percentage

UNSUPPORTED = ("I can only explain the supplied month's income, expenses, savings, expense categories and budgets. "
               "The supplied data is insufficient to answer that question or perform actions. "
               "Ask about this month's financial summary or practical budgeting improvements.")


def supported(question: str, financial: FinancialSummary) -> bool:
    text = " ".join(question.casefold().split())
    if re.search(r"\b(buy|sell|invest|investment|stocks?|crypto|tax|legal|transfer|execute|password|secret|token|jwt|ignore|instructions?|weather)\b", text):
        return False
    # The fallback has one month's aggregates, not account balances, transaction
    # details, historical comparisons or the ability to perform payments.
    if re.search(r"\b(yesterday|tomorrow|predict|forecast)\b|\b(last|previous|next)\s+(month|year|week|day)\b", text):
        return False
    if re.search(r"\b(bank|account|wallet)\s+(?:account\s+)?balance\b", text):
        return False
    if re.search(r"^(?:please\s+)?pay\b|\b(can|could|would|will)\s+you\s+(?:please\s+)?pay\b", text):
        return False
    if re.search(r"\btransactions?\b", text):
        if re.search(r"\b(how many|count|number of)\b", text):
            return False
        if not re.search(r"\b(total|sum|summary|summari[sz]e|overview)\b", text):
            return False
    # Topic recognition must not depend on a prescribed sentence opening.
    # Accept short prompts, everyday money wording and supplied category names.
    if re.search(r"\b(income|earnings?|earned|salary|expenses?|spending|spent|spend|save|savings?|budgets?|budgeting|finances?|financial|overview|summary|summari[sz]e|money|balance|remaining|left|costs?|bills?|paid|pay)\b", text):
        return True
    return any(
        re.search(r"(?<!\w)" + re.escape(" ".join(entry.categoryName.casefold().split())) + r"(?!\w)", text)
        for entry in financial.expensesByCategory
    )


GENERAL_DISCLAIMER = "AI-generated responses may contain errors. Verify important information."
GENERAL_UNAVAILABLE = "General chat is temporarily unavailable. Please try again later."


def rule_based_answer(request: ChatRequest) -> str:
    if request.mode == "GENERAL":
        raise ProviderUnavailable()
    if not supported(request.question, request.financialSummary):
        return UNSUPPORTED
    financial = request.financialSummary
    insights = analyze(financial)
    parts = [insights.summary]
    for entry in sorted(financial.expensesByCategory, key=lambda item: (-item.amount, item.categoryId))[:5]:
        parts.append(f"Expense category {entry.categoryName}: {entry.amount:.2f}.")
    with localcontext() as context:
        context.prec = 64
        for budget in sorted(financial.budgetUtilization, key=lambda item: item.categoryId)[:5]:
            parts.append(f"Budget category {budget.categoryId}: limit {budget.monthlyLimit:.2f}, spent {budget.spentAmount:.2f}, "
                         f"remaining {budget.monthlyLimit - budget.spentAmount:.2f}, used {percentage(budget.spentAmount, budget.monthlyLimit):.2f}%.")
    if len(financial.expensesByCategory) > 5 or len(financial.budgetUtilization) > 5:
        parts.append("Category and budget details above are limited to five entries each.")
    parts.extend(insights.concerns[:3])
    parts.extend(insights.recommendations[:2])
    parts.extend(insights.suggestedActions[:2])
    return " ".join(parts)[:8000]


class ChatbotService:
    def __init__(self, provider: ChatProvider | None = None, timeout_seconds: float = 3.0):
        self.provider = provider
        self.timeout_seconds = timeout_seconds

    async def respond(self, request: ChatRequest) -> ChatResponse:
        disclaimer = GENERAL_DISCLAIMER if request.mode == "GENERAL" else DISCLAIMER
        if self.provider is not None:
            try:
                answer = await asyncio.wait_for(self.provider.respond(request), timeout=self.timeout_seconds)
                if isinstance(answer, str) and 0 < len(answer.strip()) <= 8000:
                    return ChatResponse(answer=answer.strip(), provider="openai", disclaimer=disclaimer)
                log_provider_failure("invalid_provider_output")
            except TimeoutError as exception:
                log_provider_failure("orchestrator_timeout", exception)
            except ProviderUnavailable:
                pass
        if request.mode == "GENERAL":
            raise ProviderUnavailable()
        return ChatResponse(answer=rule_based_answer(request), provider="rule-based", disclaimer=disclaimer)
