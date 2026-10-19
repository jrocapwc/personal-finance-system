package core_api.auth;

import core_api.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) @BcryptPassword @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                description = "Password, at most 72 UTF-8 bytes; registration requires at least 12 characters") String password) {
    public RegisterRequest {
        name = name == null ? null : name.strip();
        email = User.normalizeEmail(email);
    }

    @Override
    public String toString() {
        return "RegisterRequest[redacted]";
    }
}
