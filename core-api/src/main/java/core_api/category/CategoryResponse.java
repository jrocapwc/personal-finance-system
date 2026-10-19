package core_api.category;

import java.time.Instant;

public record CategoryResponse(Long id, String name, CategoryType type, String color,
                               Instant createdAt, Instant updatedAt) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType(), category.getColor(),
                category.getCreatedAt(), category.getUpdatedAt());
    }
}
