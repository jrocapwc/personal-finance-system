package core_api.user;

import core_api.error.ApiError;
import core_api.security.BrowserCredentials;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Authenticated user profile")
public class UserController {
    private final UserRepository users;
    private final UserService service;
    private final BrowserCredentials cookies;
    private final CookieCsrfTokenRepository csrf;

    public UserController(UserRepository users, UserService service, BrowserCredentials cookies,
                          CookieCsrfTokenRepository csrf) {
        this.users = users;
        this.service = service;
        this.cookies = cookies;
        this.csrf = csrf;
    }

    @Operation(summary = "Get my profile", description = "Returns the current user resolved from a verified JWT.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user profile without password fields",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, expired token, or user no longer exists",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return users.findById(UUID.fromString(authentication.getName()))
                .map(UserResponse::from)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication"));
    }

    @Operation(summary = "Update my full name")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated user profile",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid name or unsupported fields", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Authentication missing or invalid", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "CSRF token missing or invalid", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/me")
    public UserResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return service.updateProfile(UUID.fromString(jwt.getSubject()), ((Number) jwt.getClaim("token_version")).longValue(), request);
    }

    @Operation(summary = "Change my password", description = "Revokes all existing tokens and clears the session cookie. Log in again after success.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed; log in again", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid request or password change rejected", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Authentication missing or invalid", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "CSRF token missing or invalid", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request,
                               HttpServletRequest servletRequest, HttpServletResponse response) {
        service.changePassword(UUID.fromString(jwt.getSubject()), ((Number) jwt.getClaim("token_version")).longValue(), request);
        cookies.clear(response);
        csrf.saveToken(csrf.generateToken(servletRequest), servletRequest, response);
        SecurityContextHolder.clearContext();
    }
}
