package core_api.integration;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MockTransactionClient {
    private final RestClient client;
    private final IntegrationProperties properties;
    private final JsonMapper json = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public MockTransactionClient(@Qualifier("integrationRestClient") RestClient client, IntegrationProperties properties) {
        this.client = client; this.properties = properties;
    }
    public List<JsonNode> fetch() {
        String key = properties.internalApiKey();
        if (key == null || key.getBytes(StandardCharsets.UTF_8).length < 32 || key.contains("\r") || key.contains("\n"))
            throw IntegrationException.unavailable();
        try {
            return client.get().uri("/internal/mock-transactions").accept(MediaType.APPLICATION_JSON)
                    .header("X-Internal-API-Key", key).exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 401 || status == 403) throw new IntegrationException(502, "INTEGRATION_AUTH_FAILED", "Mock integration service authentication failed");
                        if (status >= 500) throw IntegrationException.unavailable();
                        if (status != 200 || response.getHeaders().getContentType() == null ||
                                !MediaType.APPLICATION_JSON.isCompatibleWith(response.getHeaders().getContentType())) throw IntegrationException.invalidResponse();
                        byte[] bytes = response.getBody().readNBytes(256 * 1024 + 1);
                        if (bytes.length > 256 * 1024) throw IntegrationException.invalidResponse();
                        var root = json.readTree(bytes);
                        if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(Set.of("transactions")) ||
                                !root.get("transactions").isArray() || root.get("transactions").size() > 100) throw IntegrationException.invalidResponse();
                        var records = new ArrayList<JsonNode>();
                        root.get("transactions").forEach(records::add);
                        return List.copyOf(records);
                    });
        } catch (RestClientException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException)
                    throw new IntegrationException(504, "INTEGRATION_TIMEOUT", "Mock integration service timed out");
            }
            throw IntegrationException.unavailable();
        } catch (JacksonException | IllegalArgumentException exception) { throw IntegrationException.invalidResponse(); }
    }
}
