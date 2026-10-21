package core_api.chat;

import core_api.insights.AiFinancialRequest;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatUpstreamRequest(String question, Integer month, Integer year, AiFinancialRequest financialSummary, ChatMode mode) {
    public ChatUpstreamRequest(String question, int month, int year, AiFinancialRequest financialSummary) {
        this(question, month, year, financialSummary, ChatMode.FINANCE);
    }
    @Override public String toString() { return "ChatUpstreamRequest[redacted]"; }
}
