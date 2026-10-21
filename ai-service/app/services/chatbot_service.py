import asyncio
import re
from decimal import localcontext

from app.schemas.chat import ChatRequest, ChatResponse
from app.services.chat_provider import ChatProvider, ProviderUnavailable
from app.services.insight_engine import DISCLAIMER, analyze
from app.schemas.financial import percentage

UNSUPPORTED = ("I can only explain the supplied month's income, expenses, savings, expense categories and budgets. "
               "The supplied data is insufficient to answer that question or perform actions. "
               "Ask about this month's financial summary or practical budgeting improvements.")


def supported(question: str) -> bool:
    text = question.casefold()
    if re.search(r"\b(buy|sell|invest|investment|stocks?|crypto|tax|legal|transfer|execute|pay|password|secret|token|jwt|ignore|instructions?|transactions?|yesterday|tomorrow|predict|forecast|previous|last|next|weather)\b", text):
        return False
    return bool(re.search(r"\b(income|expenses?|spending|spent|spend|save|savings?|budget|budgets|finances|financial|overview)\b", text)
                and re.match(r"^(what|how|why|can|could|please|summari[sz]e|explain|show|tell|give|am|do|did|is|are)\b", text))


def rule_based_answer(request: ChatRequest) -> str:
    if not supported(request.question):
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
        if self.provider is not None:
            try:
                answer = await asyncio.wait_for(self.provider.respond(request), timeout=self.timeout_seconds)
                if isinstance(answer, str) and 0 < len(answer.strip()) <= 8000:
                    return ChatResponse(answer=answer.strip(), provider="openai", disclaimer=DISCLAIMER)
            except (ProviderUnavailable, TimeoutError):
                pass
        return ChatResponse(answer=rule_based_answer(request), provider="rule-based", disclaimer=DISCLAIMER)
