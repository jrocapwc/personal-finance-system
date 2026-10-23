package core_api.chat;

import core_api.dashboard.DashboardService;
import core_api.insights.AiFinancialRequest;
import core_api.insights.UpstreamInsightsException;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class ConversationService {
    private final ConversationClient client;
    private final DashboardService dashboard;
    public ConversationService(ConversationClient client, DashboardService dashboard) {
        this.client = client; this.dashboard = dashboard;
    }
    public JsonNode create(UUID owner, ConversationRequest request) {
        return client.exchange(owner, HttpMethod.POST, "", request);
    }
    public JsonNode list(UUID owner, int page, int size) {
        pagination(page, size);
        return client.exchange(owner, HttpMethod.GET, "?page=" + page + "&size=" + size, null);
    }
    public JsonNode read(UUID owner, String id, int page, int size) {
        identifier(id); pagination(page, size);
        return client.exchange(owner, HttpMethod.GET, "/" + id + "?page=" + page + "&size=" + size, null);
    }
    public ChatResponse send(UUID owner, String id, ConversationMessageRequest request) {
        // Owned metadata is fetched before any financial data. No client period or owner is accepted.
        var conversation = read(owner, id, 0, 1);
        Object body;
        if ("GENERAL".equals(conversation.path("mode").asString())) {
            body = new ConversationUpstreamTurn(request.question(), null);
        } else if ("FINANCE".equals(conversation.path("mode").asString()) &&
                conversation.path("month").isIntegralNumber() && conversation.path("year").isIntegralNumber()) {
            int month = conversation.path("month").asInt();
            int year = conversation.path("year").asInt();
            if (month < 1 || month > 12 || year < 2000 || year > 2100) throw UpstreamInsightsException.failure();
            body = new ConversationUpstreamTurn(request.question(), AiFinancialRequest.from(dashboard.get(owner, month, year)));
        } else throw UpstreamInsightsException.failure();
        return client.answer(client.exchange(owner, HttpMethod.POST, "/" + id + "/messages", body));
    }
    private void identifier(String id) {
        if (!id.matches("[a-fA-F0-9]{24}")) throw new ConversationException(400, "INVALID_REQUEST", "Invalid conversation ID");
    }
    private void pagination(int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100)
            throw new ConversationException(400, "INVALID_REQUEST", "Invalid pagination");
    }
}
