package core_api.dashboard;

import core_api.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Read-only monthly financial metrics for the authenticated user")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {
    private final DashboardService dashboard;
    public DashboardController(DashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping
    @Operation(summary = "Get my monthly financial dashboard", description = "Requires month 1..12 and year 2000..2100. "
            + "Uses [month start, next month start). Percentages use 2-decimal HALF_UP rounding; savingsRate is null "
            + "when income is zero. Daily spending contains every day, including zeros. Recent transactions and "
            + "top expense categories are limited to 5. Budget usage follows the existing budget API.")
    @ApiResponse(responseCode = "200", description = "Monthly dashboard", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "Missing/invalid period or unsupported query parameters",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public DashboardResponse get(Authentication authentication, @RequestParam int month, @RequestParam int year,
                                 HttpServletRequest request) {
        if (!Set.of("month", "year").containsAll(request.getParameterMap().keySet())) {
            throw new InvalidDashboardRequestException();
        }
        return dashboard.get(UUID.fromString(authentication.getName()), month, year);
    }
}
