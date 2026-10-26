package core_api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.Arrays;

/** One source for cookie issuance, deletion and authentication-mechanism policy. */
@Component
public class BrowserCredentials {
    private final String name;
    private final boolean secure;
    private final DefaultBearerTokenResolver headers = new DefaultBearerTokenResolver();

    public BrowserCredentials(@Value("${app.auth.cookie.name:__Host-finance_session}") String name,
                              @Value("${app.auth.cookie.secure:true}") boolean secure, Environment environment) {
        boolean local = Arrays.asList(environment.getActiveProfiles()).contains("local");
        if ((!secure && !local) || (name.startsWith("__Host-") && !secure)
                || !name.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalStateException("Invalid authentication cookie configuration; HTTP requires the local profile");
        }
        this.name = name;
        this.secure = secure;
    }

    public boolean hasCookie(HttpServletRequest request) {
        return request.getCookies() != null && Arrays.stream(request.getCookies())
                .anyMatch(cookie -> name.equals(cookie.getName()));
    }

    public String resolve(HttpServletRequest request) {
        if (hasCookie(request) && request.getHeader(HttpHeaders.AUTHORIZATION) != null) throw invalid();
        // Public credential lifecycle endpoints must work with stale browser cookies.
        if (java.util.Set.of("/api/v1/auth/logout", "/api/v1/auth/login", "/api/v1/auth/register",
                "/api/v1/auth/csrf").contains(request.getRequestURI())) return null;
        if (!hasCookie(request)) return headers.resolve(request);
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) throw invalid();
        var cookies = Arrays.stream(request.getCookies()).filter(cookie -> name.equals(cookie.getName())).toList();
        if (cookies.size() != 1 || cookies.getFirst().getValue().isBlank()) throw invalid();
        return cookies.getFirst().getValue();
    }

    public boolean headerOnly(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/auth/") && !hasCookie(request)
                && request.getHeader(HttpHeaders.AUTHORIZATION) != null
                && request.getHeader(HttpHeaders.AUTHORIZATION).startsWith("Bearer ");
    }

    public void issue(HttpServletResponse response, String token) {
        write(response, token, TokenService.LIFETIME);
    }

    public void clear(HttpServletResponse response) { write(response, "", Duration.ZERO); }

    private void write(HttpServletResponse response, String value, Duration age) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value).httpOnly(true)
                .secure(secure).sameSite("Lax").path("/").maxAge(age).build().toString());
    }

    public CookieCsrfTokenRepository csrfRepository() {
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(secure).sameSite("Lax").path("/"));
        return repository;
    }

    private OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException(new BearerTokenError("invalid_request", HttpStatus.UNAUTHORIZED,
                "Invalid authentication", null));
    }
}
