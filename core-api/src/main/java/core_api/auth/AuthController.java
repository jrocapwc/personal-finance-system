package core_api.auth;

import core_api.error.ApiError;
import core_api.user.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import core_api.security.BrowserCredentials;
import core_api.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.BadCredentialsException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Public registration and login endpoints")
public class AuthController {
    private final AuthService auth;
    private final BrowserCredentials cookies;
    private final CookieCsrfTokenRepository csrf;
    private final UserRepository users;
    private final Clock clock;

    public AuthController(AuthService auth, BrowserCredentials cookies, CookieCsrfTokenRepository csrf,
                          UserRepository users, Clock clock) {
        this.auth = auth;
        this.cookies = cookies;
        this.csrf = csrf;
        this.users = users;
        this.clock = clock;
    }

    @Operation(summary = "Register a user", description = "Email is trimmed and lowercased. "
            + "Passwords require at least 12 characters and at most 72 UTF-8 bytes. "
            + "Accepts only name, email, and password; returns no password or hash.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input, malformed JSON, or unsupported fields",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Normalized email already exists",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @Operation(summary = "Log in", description = "Set an HttpOnly session cookie valid for 3,600 seconds. Requires CSRF.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Safe user profile and lifetime",
                    content = @Content(schema = @Schema(implementation = BrowserSession.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input or malformed JSON",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/login")
    public BrowserSession login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
                                HttpServletResponse response) {
        var result = auth.login(request);
        cookies.issue(response, result.accessToken());
        rotateCsrf(servletRequest, response);
        return new BrowserSession(result.user(), result.expiresIn());
    }

    @GetMapping("/csrf")
    public void csrf(CsrfToken token) { token.getToken(); }

    @GetMapping("/session")
    public BrowserSession session(@AuthenticationPrincipal Jwt jwt, CsrfToken csrfToken) {
        // The JWT cookie can outlive a browser process while the CSRF cookie is
        // session-scoped. Restore the readable token before any private mutation.
        csrfToken.getToken();
        var user = users.findById(UUID.fromString(jwt.getSubject())).map(UserResponse::from)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication"));
        return new BrowserSession(user, Math.max(0, Duration.between(clock.instant(), jwt.getExpiresAt()).toSeconds()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        cookies.clear(response);
        rotateCsrf(request, response);
    }

    private void rotateCsrf(HttpServletRequest request, HttpServletResponse response) {
        csrf.saveToken(csrf.generateToken(request), request, response);
    }

    public record BrowserSession(UserResponse user, long expiresIn) {}
}
