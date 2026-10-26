package core_api.user;

import core_api.CsrfTestSupport;
import core_api.JwtTestSupport;
import core_api.security.TokenService;
import core_api.chat.ConversationClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class UserAccountIntegrationTests extends JwtTestSupport {
    private static final String CURRENT = "CurrentPassword123";
    private static final String NEXT = "NewSecurePassword456";
    private static final String PASSWORD_PATH = "/api/v1/users/me/password";
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired ObjectMapper json;
    @MockitoBean ConversationClient conversations;

    @Test
    void passwordChangeHashesRevokesAllSessionsClearsCookieAndRequiresNewLogin(CapturedOutput output) throws Exception {
        var user = account();
        var login = login(user, CURRENT);
        assertThat(login.statusCode()).isEqualTo(200);
        String session = CsrfTestSupport.cookie(login, "__Host-finance_session");
        String csrf = CsrfTestSupport.cookie(login, "XSRF-TOKEN");
        String secondToken = tokens.issue(user.getId());
        var response = send("PUT", PASSWORD_PATH, passwordBody(CURRENT, NEXT), session + "; " + csrf, value(csrf), null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers().allValues("set-cookie")).anySatisfy(cookie -> assertThat(cookie)
                .contains("__Host-finance_session=;", "Max-Age=0", "HttpOnly", "Secure", "Path=/", "SameSite=Lax"));
        assertThat(CsrfTestSupport.cookie(response, "XSRF-TOKEN")).isNotEqualTo(csrf);
        var saved = users.findById(user.getId()).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo(user.getPasswordHash()).isNotEqualTo(NEXT);
        assertThat(passwords.matches(NEXT, saved.getPasswordHash())).isTrue();
        assertThat(passwords.matches(CURRENT, saved.getPasswordHash())).isFalse();
        assertThat(saved.getTokenVersion()).isEqualTo(1);
        assertThat(saved.getEmail()).isEqualTo(user.getEmail());
        assertThat(saved.getName()).isEqualTo(user.getName());
        assertThat(saved.getCreatedAt()).isEqualTo(user.getCreatedAt());
        for (String oldToken : List.of(value(session), secondToken)) {
            for (String path : List.of("/api/v1/users/me", "/api/v1/auth/session", "/api/v1/categories", "/api/v1/chat/conversations")) {
                assertError(send("GET", path, null, "__Host-finance_session=" + oldToken, null, null), 401, "UNAUTHORIZED");
                assertError(send("GET", path, null, null, null, oldToken), 401, "UNAUTHORIZED");
            }
            assertError(send("PUT", PASSWORD_PATH, passwordBody(NEXT, CURRENT), null, null, oldToken), 401, "UNAUTHORIZED");
        }
        assertError(login(user, CURRENT), 401, "UNAUTHORIZED");
        verifyNoInteractions(conversations);
        when(conversations.exchange(eq(user.getId()), eq(HttpMethod.GET), eq("?page=0&size=20"), isNull()))
                .thenReturn(json.readTree("{\"items\":[]}"));
        var freshLogin = login(user, NEXT);
        assertThat(freshLogin.statusCode()).isEqualTo(200);
        String freshSession = CsrfTestSupport.cookie(freshLogin, "__Host-finance_session");
        assertThat(((Number) decoder.decode(value(freshSession)).getClaim("token_version")).longValue()).isEqualTo(1);
        for (String path : List.of("/api/v1/users/me", "/api/v1/auth/session", "/api/v1/categories", "/api/v1/chat/conversations")) {
            assertThat(send("GET", path, null, freshSession, null, null).statusCode()).isEqualTo(200);
        }
        verify(conversations).exchange(user.getId(), HttpMethod.GET, "?page=0&size=20", null);
        assertThat(output.getAll()).doesNotContain(CURRENT, NEXT, saved.getPasswordHash(), user.getPasswordHash());
    }

    @Test
    void incorrectCurrentAndReusedPasswordsAreSafeErrorsAndDoNotRevokeSession(CapturedOutput output) throws Exception {
        var user = account();
        String token = tokens.issue(user.getId());
        for (var body : List.of(passwordBody("WrongPassword123", NEXT), passwordBody(CURRENT, CURRENT))) {
            var response = send("PUT", PASSWORD_PATH, body, null, null, token);
            assertError(response, 400, "INVALID_PASSWORD_CHANGE");
            assertThat(response.body()).doesNotContain(CURRENT, NEXT, "WrongPassword123", user.getPasswordHash());
            assertThat(response.headers().allValues("set-cookie")).noneMatch(c -> c.startsWith("__Host-finance_session="));
        }
        assertThat(users.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(user.getPasswordHash());
        assertThat(send("GET", "/api/v1/users/me", null, null, null, token).statusCode()).isEqualTo(200);
        assertThat(output.getAll()).doesNotContain(CURRENT, NEXT, "WrongPassword123", user.getPasswordHash());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"currentPassword\":null,\"newPassword\":null}",
            "{\"currentPassword\":\"\",\"newPassword\":\"NewSecurePassword456\"}",
            "{\"currentPassword\":\"CurrentPassword123\",\"newPassword\":\"\"}",
            "{\"currentPassword\":\"CurrentPassword123\",\"newPassword\":\"Short123\"}",
            "{\"currentPassword\":\"CurrentPassword123\"}", "{\"newPassword\":\"NewSecurePassword456\"}"})
    void invalidPasswordRequestsAreRejected(String body) throws Exception {
        var user = account();
        assertError(send("PUT", PASSWORD_PATH, body, null, null, tokens.issue(user.getId())), 400, "VALIDATION_FAILED");
        assertThat(users.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(user.getPasswordHash());
    }

    @Test
    void passwordLimitsUseUtf8BytesAndAcceptTwelveCharactersAndExactlySeventyTwoBytes() throws Exception {
        for (String candidate : List.of("x".repeat(73), "界".repeat(25), "é".repeat(37))) {
            var user = account();
            assertError(send("PUT", PASSWORD_PATH, passwordBody(CURRENT, candidate), null, null, tokens.issue(user.getId())),
                    400, "VALIDATION_FAILED");
            assertThat(users.findById(user.getId()).orElseThrow().getTokenVersion()).isZero();
        }
        for (String candidate : List.of("TwelveChars!", "界".repeat(24))) {
            var user = account();
            assertThat(send("PUT", PASSWORD_PATH, passwordBody(CURRENT, candidate), null, null, tokens.issue(user.getId())).statusCode()).isEqualTo(204);
            assertThat(passwords.matches(candidate, users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
        }
    }

    @Test
    void cookieMutationsRequireValidCsrfAndAuthentication() throws Exception {
        var user = account();
        String auth = "__Host-finance_session=" + tokens.issue(user.getId());
        String csrf = bootstrap();
        for (String path : List.of(PASSWORD_PATH, "/api/v1/users/me")) {
            String body = path.equals(PASSWORD_PATH) ? passwordBody(CURRENT, NEXT) : "{\"name\":\"Updated Name\"}";
            assertError(send("PUT", path, body, csrf, value(csrf), null), 401, "UNAUTHORIZED");
            assertError(send("PUT", path, body, auth, null, null), 403, "FORBIDDEN");
            assertError(send("PUT", path, body, auth + "; " + csrf, "wrong", null), 403, "FORBIDDEN");
        }
        assertThat(users.findById(user.getId()).orElseThrow().getTokenVersion()).isZero();
        assertThat(send("PUT", PASSWORD_PATH, passwordBody(CURRENT, NEXT), auth + "; " + csrf, value(csrf), null).statusCode()).isEqualTo(204);
    }

    @Test
    void profileUpdateTrimsPersistsAndPreservesOtherFieldsAndSessions() throws Exception {
        var user = account();
        String token = tokens.issue(user.getId());
        String csrf = bootstrap();
        var response = send("PUT", "/api/v1/users/me", "{\"name\":\"  Updated Full Name  \"}",
                "__Host-finance_session=" + token + "; " + csrf, value(csrf), null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(json.readTree(response.body()).path("name").asString()).isEqualTo("Updated Full Name");
        assertThat(response.body()).doesNotContain("password", "token_version", "tokenVersion", user.getPasswordHash());
        var saved = users.findById(user.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Updated Full Name");
        assertThat(saved.getId()).isEqualTo(user.getId());
        assertThat(saved.getEmail()).isEqualTo(user.getEmail());
        assertThat(saved.getPasswordHash()).isEqualTo(user.getPasswordHash());
        assertThat(saved.getCreatedAt()).isEqualTo(user.getCreatedAt());
        assertThat(saved.getTokenVersion()).isZero();
        assertThat(send("GET", "/api/v1/users/me", null, null, null, token).body()).contains("Updated Full Name");
        assertThat(send("PUT", "/api/v1/users/me", json.writeValueAsString(Map.of("name", "x".repeat(100))), null, null, token).statusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"   \"}"})
    void profileRejectsMissingOrBlankNames(String body) throws Exception {
        var user = account();
        assertError(send("PUT", "/api/v1/users/me", body, null, null, tokens.issue(user.getId())), 400, "VALIDATION_FAILED");
        assertThat(users.findById(user.getId()).orElseThrow().getName()).isEqualTo(user.getName());
    }

    @Test
    void profileRejectsNamesOverOneHundredCharacters() throws Exception {
        var user = account();
        assertError(send("PUT", "/api/v1/users/me", json.writeValueAsString(Map.of("name", "x".repeat(101))),
                null, null, tokens.issue(user.getId())), 400, "VALIDATION_FAILED");
    }

    @Test
    void accountUpdatesCannotTargetAnotherUserOrChangeProtectedFields() throws Exception {
        var a = account();
        var b = account();
        String token = tokens.issue(a.getId());
        for (String field : List.of("userId", "id", "email", "passwordHash", "tokenVersion", "createdAt")) {
            var body = new java.util.HashMap<String, String>();
            body.put("name", "Stolen Name");
            body.put(field, b.getId().toString());
            assertError(send("PUT", "/api/v1/users/me", json.writeValueAsString(body), null, null, token), 400, "INVALID_REQUEST");
        }
        assertError(send("PUT", PASSWORD_PATH, json.writeValueAsString(Map.of("currentPassword", CURRENT,
                "newPassword", NEXT, "userId", b.getId().toString())), null, null, token), 400, "INVALID_REQUEST");
        assertThat(send("PUT", "/api/v1/users/me", "{\"name\":\"User A Updated\"}", null, null, token).statusCode()).isEqualTo(200);
        assertThat(send("PUT", PASSWORD_PATH, passwordBody(CURRENT, NEXT), null, null, token).statusCode()).isEqualTo(204);
        var other = users.findById(b.getId()).orElseThrow();
        assertThat(other.getName()).isEqualTo(b.getName());
        assertThat(other.getPasswordHash()).isEqualTo(b.getPasswordHash());
        assertThat(other.getTokenVersion()).isZero();
        assertThat(login(b, CURRENT).statusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "negative", "future", "fraction", "string", "expired"})
    void invalidVersionClaimsAndExpiredTokensAreRejected(String scenario) throws Exception {
        var user = account();
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("personal-finance-core-api").audience(List.of("personal-finance-core-api"))
                .subject(user.getId().toString()).issuedAt(now.minusSeconds(10)).notBefore(now.minusSeconds(10))
                .expiresAt(scenario.equals("expired") ? now.minusSeconds(1) : now.plusSeconds(3600));
        if (!scenario.equals("missing")) claims.claim("token_version", switch (scenario) {
            case "negative" -> -1L;
            case "future" -> 1L;
            case "fraction" -> 0.5;
            case "string" -> "0";
            default -> 0L;
        });
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
        assertError(send("GET", "/api/v1/auth/session", null, "__Host-finance_session=" + token, null, null), 401, "UNAUTHORIZED");
        assertError(send("PUT", PASSWORD_PATH, passwordBody(CURRENT, NEXT), null, null, token), 401, "UNAUTHORIZED");
    }

    @Test
    void concurrentPasswordChangesCannotBothSucceed() throws Exception {
        var user = account();
        String token = tokens.issue(user.getId());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> send("PUT", PASSWORD_PATH, passwordBody(CURRENT, NEXT), null, null, token).statusCode());
            var second = executor.submit(() -> send("PUT", PASSWORD_PATH, passwordBody(CURRENT, "AnotherSecurePassword789"), null, null, token).statusCode());
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(204, 401);
        }
        assertThat(users.findById(user.getId()).orElseThrow().getTokenVersion()).isEqualTo(1);
    }

    @Test
    void concurrentLoginWithOldPasswordCannotKeepAValidSessionAfterPasswordChange() throws Exception {
        var user = account();
        String token = tokens.issue(user.getId());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var oldLogin = executor.submit(() -> login(user, CURRENT));
            var change = executor.submit(() -> send("PUT", PASSWORD_PATH, passwordBody(CURRENT, NEXT), null, null, token));
            assertThat(change.get().statusCode()).isEqualTo(204);
            var response = oldLogin.get();
            assertThat(response.statusCode()).isIn(200, 401);
            if (response.statusCode() == 200) {
                String session = CsrfTestSupport.cookie(response, "__Host-finance_session");
                assertError(send("GET", "/api/v1/auth/session", null, session, null, null), 401, "UNAUTHORIZED");
            }
        }
        assertError(login(user, CURRENT), 401, "UNAUTHORIZED");
        assertThat(login(user, NEXT).statusCode()).isEqualTo(200);
    }

    private User account() {
        var user = users.saveAndFlush(new User("Account Test", UUID.randomUUID() + "@example.test", passwords.encode(CURRENT)));
        return users.findById(user.getId()).orElseThrow();
    }

    private String passwordBody(String current, String next) {
        return json.writeValueAsString(Map.of("currentPassword", current, "newPassword", next));
    }

    private HttpResponse<String> login(User user, String password) throws Exception {
        String csrf = bootstrap();
        return send("POST", "/api/v1/auth/login", json.writeValueAsString(Map.of("email", user.getEmail(), "password", password)), csrf, value(csrf), null);
    }

    private String bootstrap() throws Exception {
        return CsrfTestSupport.cookie(send("GET", "/api/v1/auth/csrf", null, null, null, null), "XSRF-TOKEN");
    }

    private String value(String cookie) { return cookie.split("=", 2)[1]; }

    private HttpResponse<String> send(String method, String path, String body, String cookie, String csrf, String bearer) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
            if (cookie != null) request.header("Cookie", cookie);
            if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
            if (bearer != null) request.header("Authorization", "Bearer " + bearer);
            return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        var body = json.readTree(response.body());
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("code").asString()).isEqualTo(code);
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.has("path")).isTrue();
        assertThat(body.has("fieldErrors")).isTrue();
    }
}
