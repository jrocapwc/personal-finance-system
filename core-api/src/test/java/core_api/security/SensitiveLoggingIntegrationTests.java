package core_api.security;

import core_api.JwtTestSupport;
import core_api.category.*;
import core_api.user.*;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.level.org.springframework=DEBUG")
@ExtendWith(OutputCaptureExtension.class)
class SensitiveLoggingIntegrationTests extends JwtTestSupport {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired TokenService tokens;

    @Test void verboseParentLoggingDoesNotExposeFinancialBodies(CapturedOutput output) throws Exception {
        String marker = "QA-PRIVATE";
        var user = users.saveAndFlush(new User("Fictional QA", UUID.randomUUID() + "@example.test", "test-hash"));
        var category = categories.saveAndFlush(new Category(user, "QA Food", CategoryType.EXPENSE, null));
        String token = tokens.issue(user.getId());
        try (var http = HttpClient.newHttpClient()) {
            var create = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/transactions"))
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"categoryId\":" + category.getId() +
                            ",\"type\":\"EXPENSE\",\"amount\":12.34,\"description\":\"" + marker + "\",\"transactionDate\":\"2026-10-01\"}"))
                    .build();
            assertThat(http.send(create, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(201);
            var dashboard = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/dashboard?month=10&year=2026"))
                    .header("Authorization", "Bearer " + token).GET().build();
            assertThat(http.send(dashboard, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        }
        assertThat(output.getAll()).doesNotContain(marker, token, "totalExpenses=12.34");
    }

    @Test void concurrentRegistrationConflictDoesNotLogIdentityOrPassword(CapturedOutput output) throws Exception {
        String email = "fictional-race-" + UUID.randomUUID() + "@example.test";
        String password = "Fictional-test-password-123!";
        String body = "{\"name\":\"Fictional QA\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        try (var http = HttpClient.newHttpClient(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/register"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var first = executor.submit(() -> http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
            var second = executor.submit(() -> http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(output.getAll()).doesNotContain(email, password);
    }
}
