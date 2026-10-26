package core_api.user;

import core_api.auth.BcryptPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) @BcryptPassword
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String currentPassword,
        @NotBlank @Size(min = 12, max = 72) @BcryptPassword
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String newPassword) {
    @Override
    public String toString() { return "ChangePasswordRequest[redacted]"; }
}
