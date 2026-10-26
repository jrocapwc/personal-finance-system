package core_api.auth;

import core_api.CsrfTestSupport;
import core_api.JwtTestSupport;
import core_api.security.TokenService;
import core_api.user.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CookieAuthenticationTests extends JwtTestSupport {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    String base() { return "http://localhost:" + port; }
    User user() { return users.saveAndFlush(new User("Cookie Test", UUID.randomUUID()+"@example.test", passwords.encode("Cookie-password-123!"))); }
    HttpResponse<String> send(String method, String path, String body, String cookie, String csrf, String bearer) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create(base() + path)).header("Content-Type", "application/json");
            if (cookie != null) request.header("Cookie", cookie);
            if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
            if (bearer != null) request.header("Authorization", "Bearer " + bearer);
            return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    String bootstrap() throws Exception { return CsrfTestSupport.cookie(send("GET", "/api/v1/auth/csrf", null, null, null, null), "XSRF-TOKEN"); }
    String value(String cookie) { return cookie.split("=",2)[1]; }

    @Test void browserLifecycleRotatesCsrfRestoresProfileAndClearsCookie() throws Exception {
        var user = user();
        String csrf = bootstrap();
        var login = send("POST", "/api/v1/auth/login", "{\"email\":\""+user.getEmail()+"\",\"password\":\"Cookie-password-123!\"}", csrf, value(csrf), null);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains(user.getId().toString(), "expiresIn").doesNotContain("accessToken", "password", "Bearer");
        String auth = CsrfTestSupport.cookie(login, "__Host-finance_session");
        String rotated = CsrfTestSupport.cookie(login, "XSRF-TOKEN");
        assertThat(rotated).isNotEqualTo(csrf);
        assertThat(login.headers().allValues("set-cookie")).anySatisfy(c -> assertThat(c)
                .contains("__Host-finance_session=", "Secure", "HttpOnly", "Path=/", "SameSite=Lax", "Max-Age=3600").doesNotContain("Domain="));
        var session = send("GET", "/api/v1/auth/session", null, auth, null, null);
        assertThat(session.statusCode()).isEqualTo(200);
        assertThat(session.body()).contains(user.getEmail()).doesNotContain(value(auth), "password");
        String restoredCsrf = CsrfTestSupport.cookie(session, "XSRF-TOKEN");
        assertThat(send("POST", "/api/v1/categories", "{\"name\":\"After browser restart\",\"type\":\"EXPENSE\"}",
                auth+"; "+restoredCsrf, value(restoredCsrf), null).statusCode()).isEqualTo(201);
        assertThat(session.headers().firstValue("cache-control").orElse("")).contains("no-store");
        assertThat(send("GET", "/api/v1/categories", null, auth, null, null).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/v1/auth/logout", "{}", auth, null, null).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/v1/categories", "{}", auth, null, null).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/v1/categories", "{}", auth+"; "+rotated, "wrong", null).statusCode()).isEqualTo(403);
        var create = send("POST", "/api/v1/categories", "{\"name\":\"Cookie category\",\"type\":\"EXPENSE\"}", auth+"; "+rotated, value(rotated), null);
        assertThat(create.statusCode()).isEqualTo(201);
        var logout = send("POST", "/api/v1/auth/logout", "{}", auth+"; "+rotated, value(rotated), null);
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.headers().allValues("set-cookie")).anySatisfy(c -> assertThat(c).contains("__Host-finance_session=;", "Max-Age=0", "Path=/", "Secure", "HttpOnly"));
        assertThat(CsrfTestSupport.cookie(logout, "XSRF-TOKEN")).isNotEqualTo(rotated);
        assertThat(send("GET", "/api/v1/auth/session", null, null, null, null).statusCode()).isEqualTo(401);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/logout", "/api/v1/chat/messages", "/api/v1/transactions"})
    void browserMutationsRequireCsrf(String path) throws Exception {
        assertThat(send("POST", path, "{}", null, null, null).statusCode()).isEqualTo(403);
        // Even an added Bearer header cannot exempt lifecycle endpoints or cookie requests.
        String jwt = tokens.issue(user().getId());
        assertThat(send("POST", path, "{}", "__Host-finance_session="+jwt, null, jwt).statusCode()).isEqualTo(403);
    }

    @Test void ambiguousCredentialsNeverAuthenticateAndCookieNeverFallsBackToHeader() throws Exception {
        String jwt = tokens.issue(user().getId());
        assertThat(send("GET", "/api/v1/auth/session", null, "__Host-finance_session="+jwt, null, jwt).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/categories", null, "__Host-finance_session=tampered", null, jwt).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/categories", null, "__Host-finance_session=tampered", null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/categories", null, null, null, jwt).statusCode()).isEqualTo(200);
    }

    @Test void deletedUserIsRejectedAndExpiredCookieCanLogout() throws Exception {
        var user = user();
        String jwt = tokens.issue(user.getId());
        users.deleteById(user.getId());
        assertThat(send("GET", "/api/v1/auth/session", null, "__Host-finance_session="+jwt, null, null).statusCode()).isEqualTo(401);
        String csrf = bootstrap();
        assertThat(send("POST", "/api/v1/auth/logout", "{}", "__Host-finance_session=expired; "+csrf, value(csrf), null).statusCode()).isEqualTo(204);
    }
}
