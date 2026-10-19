package core_api.auth;

import core_api.security.TokenService;
import core_api.user.User;
import core_api.user.UserRepository;
import core_api.user.UserResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokens;

    public AuthService(UserRepository users, PasswordEncoder passwords,
                       AuthenticationManager authenticationManager, TokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new DuplicateEmailException();
        }
        try {
            var user = new User(request.name(), request.email(), passwords.encode(request.password()));
            return UserResponse.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            // The unique index also protects concurrent registrations after the pre-check.
            if (isEmailConflict(exception)) {
                throw new DuplicateEmailException();
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        var user = users.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        return new LoginResponse(tokens.issue(user.getId()), "Bearer", TokenService.LIFETIME.toSeconds(),
                UserResponse.from(user));
    }

    private boolean isEmailConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                var constraint = violation.getConstraintName();
                return constraint != null && constraint.toLowerCase(java.util.Locale.ROOT).contains("uk_users_email");
            }
        }
        return false;
    }
}
