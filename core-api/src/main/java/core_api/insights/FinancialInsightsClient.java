package core_api.insights;

import org.springframework.beans.factory.annotation.Qualifier;
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
        var key = properties.internalApiKey();
        if (key == null || key.isBlank() || key.getBytes(StandardCharsets.UTF_8).length < 32 ||
                key.indexOf('\r') >= 0 || key.indexOf('\n') >= 0) {
            throw UpstreamInsightsException.failure();
        }
        try {
            return client.post().uri("/api/v1/insights/analyze").contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON).header("X-Internal-API-Key", key).body(financial)
                    .exchange((request, response) -> {
                        var type = response.getHeaders().getContentType();
                        if (response.getStatusCode().value() != 200 || type == null ||
                                !MediaType.APPLICATION_JSON.isCompatibleWith(type)) {
                            throw UpstreamInsightsException.failure();
                        }
                        byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (body.length > MAX_RESPONSE_BYTES) { throw UpstreamInsightsException.failure(); }
                        return InsightsResponse.decode(mapper.readTree(body), mapper);
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
