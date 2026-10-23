package core_api.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import core_api.insights.AiFinancialRequest;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversationUpstreamTurn(String question, AiFinancialRequest financialSummary) {
    @Override public String toString() { return "ConversationUpstreamTurn[redacted]"; }
}
