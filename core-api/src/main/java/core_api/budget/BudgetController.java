package core_api.budget;

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
@RequestMapping("/api/v1/budgets")
@Tag(name = "Budgets", description = "Monthly expense budgets with utilization calculated from transactions")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", description = "Invalid budget, period, or non-expense category",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "401", description = "Authentication required",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "404", description = "Budget/category missing or unavailable to this user",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class BudgetController {
    private final BudgetService budgets;
    public BudgetController(BudgetService budgets) { this.budgets = budgets; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a monthly expense budget")
    @ApiResponse(responseCode = "201", description = "Budget created with current spending", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "409", description = "Budget already exists for this category and period",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public BudgetResponse create(Authentication authentication, @Valid @RequestBody BudgetRequest request) {
        return budgets.create(userId(authentication), request);
    }

    @GetMapping
    @Operation(summary = "List my monthly budgets", description = "month (1..12) and year (2000..2100) are both required. "
            + "Spending includes only owned category EXPENSE transactions in [month start, next month start). "
            + "percentageUsed uses 2-decimal HALF_UP rounding; status uses exact comparisons.")
    public List<BudgetResponse> list(Authentication authentication, @RequestParam int month, @RequestParam int year) {
        return budgets.list(userId(authentication), month, year);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get my budget with live utilization")
    public BudgetResponse get(Authentication authentication, @PathVariable Long id) { return budgets.get(userId(authentication), id); }

    @PutMapping("/{id}")
    @Operation(summary = "Replace my budget", description = "Provide categoryId, month, year, monthlyLimit, and alertThreshold.")
    @ApiResponse(responseCode = "409", description = "Another budget already exists for this category and period",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public BudgetResponse update(Authentication authentication, @PathVariable Long id, @Valid @RequestBody BudgetRequest request) {
        return budgets.update(userId(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete my budget", description = "Does not delete transactions or categories.")
    @ApiResponse(responseCode = "204", description = "Budget deleted", content = @Content)
    public void delete(Authentication authentication, @PathVariable Long id) { budgets.delete(userId(authentication), id); }

    private UUID userId(Authentication authentication) { return UUID.fromString(authentication.getName()); }
}
