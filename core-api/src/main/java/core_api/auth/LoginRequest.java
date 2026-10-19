package core_api.auth;

import core_api.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 72) @BcryptPassword @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                description = "Password, at most 72 UTF-8 bytes; registration requires at least 12 characters") String password) {
    public LoginRequest {
        email = User.normalizeEmail(email);
    }

    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
