package core_api.auth;

import core_api.security.TokenService;
import core_api.user.User;
import core_api.user.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {
    @Mock private UserRepository users;
    @Mock private PasswordEncoder passwords;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private TokenService tokens;
    @InjectMocks private AuthService auth;

    @Test
    void existingEmailIsRejectedBeforeHashingOrSaving() {
        var request = new RegisterRequest("Test User", "test@example.com", "Test-password-2026!");
        when(users.existsByEmail(request.email())).thenReturn(true);
        assertThatThrownBy(() -> auth.register(request)).isInstanceOf(DuplicateEmailException.class);
        verify(passwords, never()).encode(any());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void uniqueConstraintRaceIsMappedToDuplicateEmail() {
        var request = new RegisterRequest("Test User", "test@example.com", "Test-password-2026!");
        when(passwords.encode(request.password())).thenReturn("encoded");
        var violation = new ConstraintViolationException("Unique email", new SQLException("unique", "23505"),
                "PUBLIC.UK_USERS_EMAIL_INDEX_4");
        when(users.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("constraint", violation));
        assertThatThrownBy(() -> auth.register(request)).isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void unrelatedDatabaseFailureIsNotMisreportedAsDuplicateEmail() {
        var request = new RegisterRequest("Test User", "test@example.com", "Test-password-2026!");
        when(passwords.encode(request.password())).thenReturn("encoded");
        var failure = new DataIntegrityViolationException("unrelated failure");
        when(users.saveAndFlush(any(User.class))).thenThrow(failure);
        assertThatThrownBy(() -> auth.register(request)).isSameAs(failure);
    }
}
