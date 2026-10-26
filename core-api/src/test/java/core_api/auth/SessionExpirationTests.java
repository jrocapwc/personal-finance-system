package core_api.auth;

import core_api.JwtTestSupport;
import core_api.security.TokenService;
import core_api.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.*;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionExpirationTests extends JwtTestSupport {
    @LocalServerPort int port;
    @MockitoBean Clock clock;
    @Autowired TokenService tokens;
    @Autowired UserRepository users;

    @Test void originalExpirationIsEnforcedAfterDeterministicClockAdvance() throws Exception {
        Instant issued = Instant.parse("2030-01-01T00:00:00Z");
        when(clock.instant()).thenReturn(issued);
        var user = users.saveAndFlush(new User("Expiration Test", UUID.randomUUID()+"@example.test", "test-hash"));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/session"))
                .header("Cookie", "__Host-finance_session="+tokens.issue(user.getId())).GET().build();
        var bankRequest = HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/bank-connections"))
                .header("Cookie", "__Host-finance_session="+tokens.issue(user.getId())).GET().build();
        try (var client = HttpClient.newHttpClient()) {
            assertThat(client.send(bankRequest, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            when(clock.instant()).thenReturn(issued.plusSeconds(3601));
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            assertThat(client.send(bankRequest, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        }
    }
}
