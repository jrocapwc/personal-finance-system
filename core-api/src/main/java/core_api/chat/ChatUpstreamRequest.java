package core_api.chat;

import core_api.insights.AiFinancialRequest;

public record ChatUpstreamRequest(String question, int month, int year, AiFinancialRequest financialSummary) {
    @Override public String toString() { return "ChatUpstreamRequest[redacted]"; }
}
