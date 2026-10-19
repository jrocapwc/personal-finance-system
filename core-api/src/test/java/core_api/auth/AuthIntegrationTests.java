package core_api.auth;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import core_api.JwtTestSupport;
import core_api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTests extends JwtTestSupport {
    private static final String PASSWORD = "Test-password-2026!";
    @LocalServerPort
    private int port;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JwtEncoder encoder;
    @Autowired
    private JwtDecoder decoder;

    @Test
    void registrationNormalizesEmailHashesPasswordAndReturnsSafeUser() throws Exception {
        String email = newEmail();
        var response = register("  " + email.toUpperCase(java.util.Locale.ROOT) + "  ");
        assertThat(response.statusCode()).isEqualTo(201);
        var user = users.findByEmail(email).orElseThrow();
        assertThat(user.getName()).isEqualTo("Test User");
        assertThat(user.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());
        assertThat(JsonPath.<String>read(response.body(), "$.id")).isEqualTo(user.getId().toString());
        assertThat(JsonPath.<String>read(response.body(), "$.email")).isEqualTo(email);
        assertSafe(response.body(), user.getPasswordHash());
    }

    @Test
    void duplicateNormalizedEmailReturnsConflict() throws Exception {
        String email = newEmail();
        assertThat(register(email).statusCode()).isEqualTo(201);
        var response = register(" " + email.toUpperCase(java.util.Locale.ROOT) + " ");
        assertError(response, 409, "DUPLICATE_EMAIL");
        assertThat(users.findAll().stream().filter(user -> user.getEmail().equals(email)).count()).isEqualTo(1);
    }

    @Test
    void loginIssuesSixtyMinuteTokenAndMeReturnsCurrentUserWithoutPassword() throws Exception {
        String email = newEmail();
        assertThat(register(email).statusCode()).isEqualTo(201);
        var response = login(email.toUpperCase(java.util.Locale.ROOT), PASSWORD);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.tokenType")).isEqualTo("Bearer");
        assertThat(JsonPath.<Integer>read(response.body(), "$.expiresIn")).isEqualTo(3600);
        String token = JsonPath.read(response.body(), "$.accessToken");
        var jwt = decoder.decode(token);
        var user = users.findByEmail(email).orElseThrow();
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(60));
        assertThat(jwt.getClaims()).doesNotContainKeys("password", "passwordHash", "email", "roles");
        var me = request("GET", "/api/v1/users/me", null, token);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(me.body(), "$.id")).isEqualTo(user.getId().toString());
        assertSafe(response.body(), user.getPasswordHash());
        assertSafe(me.body(), user.getPasswordHash());
        assertThat(response.headers().allValues("set-cookie")).isEmpty();
        assertThat(me.headers().allValues("set-cookie")).isEmpty();
        assertThat(me.headers().firstValue("cache-control").orElse("")).contains("no-store");
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnSameGenericUnauthorizedError() throws Exception {
        String email = newEmail();
        assertThat(register(email).statusCode()).isEqualTo(201);
        var wrongPassword = login(email, "Wrong-password-2026!");
        var unknownUser = login(newEmail(), PASSWORD);
        assertError(wrongPassword, 401, "UNAUTHORIZED");
        assertError(unknownUser, 401, "UNAUTHORIZED");
        assertThat(JsonPath.<String>read(wrongPassword.body(), "$.message"))
                .isEqualTo(JsonPath.read(unknownUser.body(), "$.message"));
        assertThat(wrongPassword.body()).doesNotContain(PASSWORD, "Wrong-password-2026!", "passwordHash");
    }

    @Test
    void missingJwtReturnsUnauthorized() throws Exception {
        var response = request("GET", "/api/v1/users/me", null, null);
        assertError(response, 401, "UNAUTHORIZED");
        assertThat(response.headers().firstValue("www-authenticate")).contains("Bearer");
    }

    @Test
    void malformedJwtReturnsUnauthorized() throws Exception {
        assertError(request("GET", "/api/v1/users/me", null, "not-a-jwt"), 401, "UNAUTHORIZED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "future", "signature", "issuer", "audience", "unknown-user",
            "invalid-subject", "missing-subject", "missing-expiration"})
    void untrustedTokensReturnUnauthorized(String scenario) throws Exception {
        String email = newEmail();
        assertThat(register(email).statusCode()).isEqualTo(201);
        String id = users.findByEmail(email).orElseThrow().getId().toString();
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(scenario.equals("issuer") ? "another-issuer" : "personal-finance-core-api")
                .audience(List.of(scenario.equals("audience") ? "another-api" : "personal-finance-core-api"))
                .issuedAt(now.minusSeconds(7200))
                .notBefore(scenario.equals("future") ? now.plusSeconds(300) : now.minusSeconds(7200));
        if (!scenario.equals("missing-expiration")) {
            claims.expiresAt(scenario.equals("expired") ? now.minusSeconds(1) : now.plusSeconds(3600));
        }
        if (!scenario.equals("missing-subject")) {
            claims.subject(switch (scenario) {
                case "unknown-user" -> UUID.randomUUID().toString();
                case "invalid-subject" -> "invalid-id";
                default -> id;
            });
        }
        JwtEncoder signer = encoder;
        if (scenario.equals("signature")) {
            signer = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                    Base64.getDecoder().decode(newSecret()), "HmacSHA256")));
        }
        String token = signer.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
        assertError(request("GET", "/api/v1/users/me", null, token), 401, "UNAUTHORIZED");
    }

    @Test
    void deletingAUserInvalidatesTheirToken() throws Exception {
        String email = newEmail();
        register(email);
        var login = login(email, PASSWORD);
        String token = JsonPath.read(login.body(), "$.accessToken");
        users.deleteById(users.findByEmail(email).orElseThrow().getId());
        assertError(request("GET", "/api/v1/users/me", null, token), 401, "UNAUTHORIZED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "email", "password", "unicode-password"})
    void invalidRegistrationReturnsFieldErrorsWithoutRejectedValues(String field) throws Exception {
        var payload = new HashMap<String, Object>(registration(newEmail()));
        switch (field) {
            case "name" -> payload.put("name", "   ");
            case "email" -> payload.put("email", "invalid-email");
            case "password" -> payload.put("password", "short");
            case "unicode-password" -> payload.put("password", "é".repeat(37));
            default -> throw new IllegalStateException();
        }
        var response = request("POST", "/api/v1/auth/register", payload, null);
        assertError(response, 400, "VALIDATION_FAILED");
        Map<String, Object> fields = JsonPath.read(response.body(), "$.fieldErrors");
        assertThat(fields).containsKey(field.equals("unicode-password") ? "password" : field);
        assertThat(response.body()).doesNotContain("short", "invalid-email", "é");
    }

    @ParameterizedTest
    @ValueSource(strings = {"roles", "id", "passwordHash", "createdAt", "enabled"})
    void privilegedRegistrationFieldsAreRejected(String field) throws Exception {
        var payload = new HashMap<String, Object>(registration(newEmail()));
        payload.put(field, "user-controlled");
        assertError(request("POST", "/api/v1/auth/register", payload, null), 400, "INVALID_REQUEST");
    }

    @Test
    void invalidLoginInputReturnsBadRequest() throws Exception {
        assertError(login("not-an-email", ""), 400, "VALIDATION_FAILED");
    }

    @Test
    void malformedJsonReturnsConsistentBadRequest() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/auth/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{"))
                    .build();
            assertError(client.send(request, HttpResponse.BodyHandlers.ofString()), 400, "INVALID_REQUEST");
        }
    }

    @Test
    void healthRemainsPublicAndOtherEndpointsRequireAuthentication() throws Exception {
        assertThat(request("GET", "/actuator/health", null, null).statusCode()).isEqualTo(200);
        assertThat(request("GET", "/actuator/health/liveness", null, null).statusCode()).isEqualTo(200);
        assertError(request("GET", "/api/v1/private", null, null), 401, "UNAUTHORIZED");
        var console = request("GET", "/h2-console/", null, null);
        assertError(console, 401, "UNAUTHORIZED");
        assertThat(console.headers().firstValue("x-frame-options")).contains("DENY");
    }

    private HttpResponse<String> register(String email) throws Exception {
        return request("POST", "/api/v1/auth/register", registration(email), null);
    }

    private Map<String, Object> registration(String email) {
        return Map.of("name", "  Test User  ", "email", email, "password", PASSWORD);
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        return request("POST", "/api/v1/auth/login", Map.of("email", email, "password", password), null);
    }

    private HttpResponse<String> request(String method, String path, Object body, String token) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl() + path)).timeout(Duration.ofSeconds(20));
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            if (body != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private String baseUrl() { return "http://localhost:" + port; }
    private String newEmail() { return "user-" + UUID.randomUUID() + "@example.com"; }

    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(status);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo(code);
        assertThat(JsonPath.<String>read(response.body(), "$.timestamp")).isNotBlank();
        assertThat(JsonPath.<String>read(response.body(), "$.path")).startsWith("/");
        assertThat(response.body()).doesNotContain("stackTrace", "passwordHash", "$2a$", "$2b$");
    }

    private void assertSafe(String body, String hash) {
        assertThat(body).doesNotContain("password", "passwordHash", "password_hash", PASSWORD, hash);
    }
}
