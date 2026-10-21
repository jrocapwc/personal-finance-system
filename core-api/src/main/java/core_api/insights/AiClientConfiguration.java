package core_api.insights;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import java.net.HttpURLConnection;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiServiceProperties.class)
public class AiClientConfiguration {
    @Bean
    RestClient insightsRestClient(AiServiceProperties properties) {
        var factory = new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                // Do not forward the internal credential to redirect destinations.
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        // Dedicated client: no inbound security-context propagation or request logging interceptors.
        return RestClient.builder().baseUrl(properties.baseUrl().toString()).requestFactory(factory).build();
    }
}
