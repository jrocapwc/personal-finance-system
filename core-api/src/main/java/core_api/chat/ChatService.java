package core_api.chat;

import core_api.dashboard.DashboardService;
import core_api.insights.AiFinancialRequest;
import core_api.insights.FinancialInsightsClient;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ChatService {
    private final DashboardService dashboard;
    private final FinancialInsightsClient client;
    public ChatService(DashboardService dashboard, FinancialInsightsClient client) {
        this.dashboard = dashboard; this.client = client;
    }
    public ChatResponse respond(UUID userId, ChatMessageRequest question) {
        var financial = AiFinancialRequest.from(dashboard.get(userId, question.month(), question.year()));
        return client.chat(new ChatUpstreamRequest(question.question(), question.month(), question.year(), financial));
    }
}
