package core_api.chat;

import core_api.insights.UpstreamInsightsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.Set;

public record ChatResponse(String answer, String provider, String disclaimer) {
    public static ChatResponse decode(JsonNode root, JsonMapper mapper) {
        if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(Set.of("answer", "provider", "disclaimer"))) {
            throw UpstreamInsightsException.failure();
        }
        for (var field : Set.of("answer", "provider", "disclaimer")) {
            if (!root.get(field).isString() || root.get(field).asString().isBlank()) { throw UpstreamInsightsException.failure(); }
        }
        if (root.get("answer").asString().length() > 8000 ||
                !Set.of("openai", "rule-based").contains(root.get("provider").asString())) { throw UpstreamInsightsException.failure(); }
        return mapper.treeToValue(root, ChatResponse.class);
    }
    @Override public String toString() { return "ChatResponse[redacted]"; }
}
