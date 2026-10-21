package core_api.insights;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("app.ai")
public record AiServiceProperties(URI baseUrl, String internalApiKey, Duration connectTimeout, Duration readTimeout) {
    public AiServiceProperties {
        if (baseUrl == null || baseUrl.getHost() == null ||
                !("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme())) ||
                baseUrl.getUserInfo() != null || baseUrl.getQuery() != null || baseUrl.getFragment() != null ||
                !(baseUrl.getPath().isEmpty() || baseUrl.getPath().equals("/"))) {
            throw new IllegalArgumentException("AI_SERVICE_BASE_URL must be an HTTP(S) origin without credentials, path, query or fragment");
        }
        if (connectTimeout == null || connectTimeout.toMillis() < 1 || connectTimeout.toMillis() > 60000 ||
                readTimeout == null || readTimeout.toMillis() < 1 || readTimeout.toMillis() > 60000) {
            throw new IllegalArgumentException("AI service timeouts must be between 1ms and 60s");
        }
    }
    @Override public String toString() { return "AiServiceProperties[redacted]"; }
}
