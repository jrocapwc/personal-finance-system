package core_api.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 100) @Schema(example = "Education") String name,
        @NotNull CategoryType type,
        @Pattern(regexp = "#(?:[0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})",
                message = "must be a hex color in #RGB or #RRGGBB format")
        @Schema(example = "#4F46E5", description = "Optional #RGB or #RRGGBB hex color; null clears it") String color) {
    public CategoryRequest {
        name = Category.cleanName(name);
    }
}
