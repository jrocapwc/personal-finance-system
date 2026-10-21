package core_api.insights;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Set;

public record InsightsResponse(String summary, List<String> positiveObservations, List<String> concerns,
        List<String> recommendations, List<String> suggestedActions, String disclaimer) {
    private static final Set<String> FIELDS = Set.of("summary", "positiveObservations", "concerns",
            "recommendations", "suggestedActions", "disclaimer");

    static InsightsResponse decode(JsonNode root, JsonMapper mapper) {
        if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(FIELDS)) {
            throw UpstreamInsightsException.failure();
        }
        for (var field : FIELDS) {
            var value = root.get(field);
            if (field.equals("summary") || field.equals("disclaimer")) {
                if (!value.isString() || value.asString().isBlank()) { throw UpstreamInsightsException.failure(); }
            } else {
                if (!value.isArray()) { throw UpstreamInsightsException.failure(); }
                for (var item : value) {
                    if (!item.isString()) { throw UpstreamInsightsException.failure(); }
                }
            }
        }
        return mapper.treeToValue(root, InsightsResponse.class);
    }
}
