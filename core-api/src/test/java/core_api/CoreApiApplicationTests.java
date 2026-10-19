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
class CoreApiApplicationTests {

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

		assertThat(response.statusCode()).isEqualTo(404);
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
