package core_api.insights;

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
@RequestMapping("/api/v1/insights")
@Tag(name = "Insights", description = "Educational financial insights from the internal rules service")
@SecurityRequirement(name = "bearerAuth")
public class InsightsController {
    private final InsightsService insights;
    public InsightsController(InsightsService insights) { this.insights = insights; }

    @GetMapping
    @Operation(summary = "Analyze my monthly finances", description = "Requires month 1..12 and year 2000..2100. Sends only the authenticated user's aggregates to the internal service.")
    @ApiResponse(responseCode = "200", description = "Structured educational insights", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "Invalid or missing period or unsupported parameters", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Insights upstream request or response failed", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "504", description = "Insights upstream timeout", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public InsightsResponse get(Authentication authentication, @RequestParam int month, @RequestParam int year, HttpServletRequest request) {
        if (!Set.of("month", "year").containsAll(request.getParameterMap().keySet()) ||
                request.getParameterMap().values().stream().anyMatch(values -> values.length != 1)) {
            throw new InvalidInsightsRequestException();
        }
        return insights.get(UUID.fromString(authentication.getName()), month, year);
    }
}
