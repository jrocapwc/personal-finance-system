package core_api.user;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public UserService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public void changePassword(UUID userId, long tokenVersion, ChangePasswordRequest request) {
        var user = currentUserForUpdate(userId, tokenVersion);
        if (!passwords.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordChangeException();
        }
        if (passwords.matches(request.newPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordChangeException();
        }
        user.changePassword(passwords.encode(request.newPassword()));
        users.saveAndFlush(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, long tokenVersion, UpdateProfileRequest request) {
        var user = currentUserForUpdate(userId, tokenVersion);
        user.updateName(request.name());
        return UserResponse.from(users.saveAndFlush(user));
    }

    private User currentUserForUpdate(UUID userId, long tokenVersion) {
        var user = users.findByIdForAccountUpdate(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication"));
        // Recheck after acquiring the lock in case a password change revoked this request while it waited.
        if (user.getTokenVersion() != tokenVersion) {
            throw new BadCredentialsException("Invalid authentication");
        }
        return user;
    }
}
