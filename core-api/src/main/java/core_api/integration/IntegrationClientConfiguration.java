package core_api.integration;

import java.io.IOException;
import java.net.HttpURLConnection;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IntegrationProperties.class)
public class IntegrationClientConfiguration {
    @Bean
    RestClient integrationRestClient(IntegrationProperties properties) {
        var factory = new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().baseUrl(properties.baseUrl().toString()).requestFactory(factory).build();
    }
}
