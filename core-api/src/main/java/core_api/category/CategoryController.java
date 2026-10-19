package core_api.category;

import core_api.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Categories owned by the authenticated user")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Authentication required",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "400", description = "Invalid request body, type filter, or ID",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class CategoryController {
    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    @Operation(summary = "List my categories", description = "Optional INCOME or EXPENSE filter. "
            + "Initializes ten defaults once per user; subsequent lists preserve edits and deletions.")
    public List<CategoryResponse> list(Authentication authentication, @RequestParam(required = false) CategoryType type) {
        return categories.list(userId(authentication), type);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a category", description = "Name uniqueness ignores case and repeated whitespace per user/type.")
    @ApiResponse(responseCode = "201", description = "Category created", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "409", description = "Duplicate name within the same user and type",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CategoryResponse create(Authentication authentication, @Valid @RequestBody CategoryRequest request) {
        return categories.create(userId(authentication), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update my category", description = "Full replacement of name/type/color; omitted or null color clears it.")
    @ApiResponse(responseCode = "404", description = "Category missing or owned by another user",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Duplicate name, or referenced category type cannot change",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CategoryResponse update(Authentication authentication, @PathVariable Long id,
                                   @Valid @RequestBody CategoryRequest request) {
        return categories.update(userId(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete my category", description = "Default categories may be deleted and will not be reseeded. Referenced categories cannot be deleted.")
    @ApiResponse(responseCode = "204", description = "Category deleted", content = @Content)
    @ApiResponse(responseCode = "409", description = "Category is referenced by transactions",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Category missing or owned by another user",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void delete(Authentication authentication, @PathVariable Long id) {
        categories.delete(userId(authentication), id);
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
