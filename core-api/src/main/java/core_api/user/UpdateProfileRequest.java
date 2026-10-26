package core_api.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(@NotBlank @Size(max = 100) String name) {
    public UpdateProfileRequest {
        name = name == null ? null : name.strip();
    }
}
