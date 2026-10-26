package core_api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoreApiApplicationTests extends JwtTestSupport {

	@LocalServerPort
	private int port;

	@Test
	void contextLoads() {
	}

	@Test
	void healthEndpointIsUp() throws IOException, InterruptedException {
		var response = get("/actuator/health");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<String>read(response.body(), "$.status")).isEqualTo("UP");
	}

	@Test
	void h2ConsoleIsDisabledInTestProfile() throws IOException, InterruptedException {
		var response = get("/h2-console/");

		assertThat(response.statusCode()).isEqualTo(401);
	}

    @Test
    void swaggerUiAndItsConfigurationArePublic() throws IOException, InterruptedException {
        var entry = get("/swagger-ui.html");
        assertThat(entry.statusCode()).isBetween(300, 399);
        assertThat(entry.headers().firstValue("location").orElse(""))
                .contains("/swagger-ui/index.html");
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
        assertThat(get("/swagger-ui/swagger-ui.css").statusCode()).isEqualTo(200);
        assertThat(get("/v3/api-docs/swagger-config").statusCode()).isEqualTo(200);
    }

    @Test
    void openApiDocumentsPublicAuthenticationAndProtectedProfile() throws IOException, InterruptedException {
        var response = get("/v3/api-docs");
        assertThat(response.statusCode()).isEqualTo(200);
        java.util.Map<String, Object> paths = JsonPath.read(response.body(), "$.paths");
        assertThat(paths).containsOnlyKeys("/api/v1/auth/csrf", "/api/v1/auth/session", "/api/v1/auth/logout", "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/users/me",
                "/api/v1/categories", "/api/v1/categories/{id}",
                "/api/v1/transactions", "/api/v1/transactions/{id}",
                "/api/v1/budgets", "/api/v1/budgets/{id}", "/api/v1/dashboard", "/api/v1/insights", "/api/v1/chat/messages",
                "/api/v1/chat/conversations", "/api/v1/chat/conversations/{id}", "/api/v1/chat/conversations/{id}/messages",
                "/api/v1/transactions/import/mock", "/api/v1/bank-connections", "/api/v1/bank-connections/demo",
                "/api/v1/bank-connections/demo/sync", "/api/v1/bank-connections/statements/columns", "/api/v1/bank-connections/statements/preview",
                "/api/v1/bank-connections/statements/{id}/confirm", "/api/v1/bank-connections/statements/{id}");
        assertThat(JsonPath.<String>read(response.body(), "$.components.securitySchemes.bearerAuth.scheme"))
                .isEqualTo("bearer");
        assertThat(JsonPath.<String>read(response.body(), "$.components.securitySchemes.bearerAuth.bearerFormat"))
                .isEqualTo("JWT");
        java.util.Map<String, Object> register = JsonPath.read(response.body(), "$.paths['/api/v1/auth/register'].post");
        java.util.Map<String, Object> login = JsonPath.read(response.body(), "$.paths['/api/v1/auth/login'].post");
        assertThat(register).doesNotContainKey("security");
        assertThat(login).doesNotContainKey("security");
        java.util.List<java.util.Map<String, Object>> security =
                JsonPath.read(response.body(), "$.paths['/api/v1/users/me'].get.security");
        assertThat(security).anySatisfy(requirement -> assertThat(requirement).containsKey("bearerAuth"));
        java.util.Map<String, Object> registerResponses = JsonPath.read(response.body(), "$.paths['/api/v1/auth/register'].post.responses");
        assertThat(registerResponses).containsKeys("201", "400", "409");
        java.util.Map<String, Object> meResponses = JsonPath.read(response.body(), "$.paths['/api/v1/users/me'].get.responses");
        assertThat(meResponses).containsKeys("200", "401");
        java.util.Map<String, Object> userProperties = JsonPath.read(response.body(), "$.components.schemas.UserResponse.properties");
        assertThat(userProperties).doesNotContainKeys("password", "passwordHash", "password_hash");
        assertThat(JsonPath.<Boolean>read(response.body(), "$.components.schemas.RegisterRequest.properties.password.writeOnly"))
                .isTrue();
    }
	private HttpResponse<String> get(String path) throws IOException, InterruptedException {
		try (var client = HttpClient.newHttpClient()) {
			var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
					.timeout(Duration.ofSeconds(10))
					.GET()
					.build();
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

}
