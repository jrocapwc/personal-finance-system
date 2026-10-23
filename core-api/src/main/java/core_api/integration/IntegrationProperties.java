package core_api.integration;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.integration")
public record IntegrationProperties(URI baseUrl, String internalApiKey, Duration connectTimeout, Duration readTimeout) {
    public IntegrationProperties {
        if (baseUrl == null || baseUrl.getHost() == null || !("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme())) ||
                baseUrl.getUserInfo() != null || baseUrl.getQuery() != null || baseUrl.getFragment() != null ||
                !(baseUrl.getPath().isEmpty() || baseUrl.getPath().equals("/"))) throw new IllegalArgumentException("Integration base URL must be an HTTP(S) origin");
        if (connectTimeout == null || readTimeout == null || connectTimeout.toMillis() < 1 || readTimeout.toMillis() < 1 ||
                connectTimeout.toMillis() > 60000 || readTimeout.toMillis() > 60000) throw new IllegalArgumentException("Integration timeouts must be 1ms..60s");
    }
    @Override public String toString() { return "IntegrationProperties[redacted]"; }
}
