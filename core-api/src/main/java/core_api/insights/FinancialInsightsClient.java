package core_api.insights;

import org.springframework.beans.factory.annotation.Qualifier;
import core_api.chat.ChatUpstreamRequest;
import core_api.chat.ChatResponse;
import core_api.chat.ChatMode;
import core_api.chat.ChatUnavailableException;
import tools.jackson.databind.JsonNode;
import java.util.function.Function;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;

@Component
public class FinancialInsightsClient {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private final RestClient client;
    private final AiServiceProperties properties;
    private final JsonMapper mapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public FinancialInsightsClient(@Qualifier("insightsRestClient") RestClient client, AiServiceProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public InsightsResponse analyze(AiFinancialRequest financial) {
        return post("/api/v1/insights/analyze", financial, root -> InsightsResponse.decode(root, mapper));
    }

    public ChatResponse chat(ChatUpstreamRequest question) {
        return post("/api/v1/chat/respond", question, root -> ChatResponse.decode(root, mapper));
    }

    private <T> T post(String path, Object body, Function<JsonNode, T> decode) {
        var key = properties.internalApiKey();
        if (key == null || key.isBlank() || key.getBytes(StandardCharsets.UTF_8).length < 32 ||
                key.indexOf('\r') >= 0 || key.indexOf('\n') >= 0) {
            throw UpstreamInsightsException.failure();
        }
        try {
            return client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON).header("X-Internal-API-Key", key).body(body)
                    .exchange((request, response) -> {
                        if (body instanceof ChatUpstreamRequest chat && chat.mode() == ChatMode.GENERAL &&
                                response.getStatusCode().value() == 503) {
                            throw new ChatUnavailableException();
                        }
                        var type = response.getHeaders().getContentType();
                        if (response.getStatusCode().value() != 200 || type == null ||
                                !MediaType.APPLICATION_JSON.isCompatibleWith(type)) {
                            throw UpstreamInsightsException.failure();
                        }
                        byte[] responseBody = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (responseBody.length > MAX_RESPONSE_BYTES) { throw UpstreamInsightsException.failure(); }
                        return decode.apply(mapper.readTree(responseBody));
                    });
        } catch (RestClientException | JacksonException | IllegalArgumentException exception) {
            // Do not propagate upstream bodies, request headers, URLs, or nested exception text.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                    throw UpstreamInsightsException.timeout();
                }
            }
            throw UpstreamInsightsException.failure();
        }
    }
}
