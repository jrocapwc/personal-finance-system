package core_api.chat;

import core_api.insights.AiServiceProperties;
import core_api.insights.UpstreamInsightsException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ConversationClient {
    private final RestClient client;
    private final AiServiceProperties properties;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public ConversationClient(@Qualifier("insightsRestClient") RestClient client, AiServiceProperties properties) {
        this.client = client; this.properties = properties;
    }
    public JsonNode exchange(UUID owner, HttpMethod method, String path, Object body) {
        var key = properties.internalApiKey();
        if (key == null || key.getBytes(StandardCharsets.UTF_8).length < 32 || key.contains("\r") || key.contains("\n"))
            throw UpstreamInsightsException.failure();
        try {
            var request = client.method(method).uri("/api/v1/chat/conversations" + path)
                    .accept(MediaType.APPLICATION_JSON).header("X-Internal-API-Key", key)
                    .header("X-Owner-Id", owner.toString());
            if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body);
            return request.exchange((sent, response) -> {
                int status = response.getStatusCode().value();
                if (status == 404) throw new ConversationException(404, "CONVERSATION_NOT_FOUND", "Conversation not found");
                if (status == 400 || status == 422) throw new ConversationException(400, "INVALID_REQUEST", "Invalid conversation request");
                byte[] bytes = response.getBody().readNBytes(4 * 1024 * 1024 + 1);
                if (bytes.length > 4 * 1024 * 1024) throw UpstreamInsightsException.failure();
                if (status == 503) {
                    // Only allowlisted codes influence public errors. Never forward arbitrary upstream text.
                    String code = mapper.readTree(bytes).path("detail").path("code").asString();
                    if ("CHAT_UNAVAILABLE".equals(code)) throw new ConversationException(503, code, "General chat is temporarily unavailable");
                    throw new ConversationException(503, "PERSISTENCE_UNAVAILABLE", "Conversation storage unavailable; persistence was not confirmed");
                }
                var type = response.getHeaders().getContentType();
                if (status != 200 || type == null || !MediaType.APPLICATION_JSON.isCompatibleWith(type))
                    throw UpstreamInsightsException.failure();
                JsonNode root = mapper.readTree(bytes);
                if (root == null || !root.isObject() || root.has("ownerId")) throw UpstreamInsightsException.failure();
                return root;
            });
        } catch (RestClientException | JacksonException | IllegalArgumentException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException)
                    throw new ConversationException(504, "CHAT_TIMEOUT", "Conversation service timed out; persistence was not confirmed");
            }
            throw UpstreamInsightsException.failure();
        }
    }
    public ChatResponse answer(JsonNode root) { return ChatResponse.decode(root, mapper); }
}
