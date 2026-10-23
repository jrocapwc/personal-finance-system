package core_api.integration;

import core_api.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions/import/mock")
@SecurityRequirement(name = "bearerAuth")
public class MockImportController {
    private final MockImportService imports;
    public MockImportController(MockImportService imports) { this.imports = imports; }
    @PostMapping
    @Operation(summary = "Import fictional mock transactions", description = "Uses the verified JWT owner; accepts no body or query parameters. Duplicate provider IDs are skipped per owner.")
    public MockImportService.ImportResult importMock(Authentication authentication, HttpServletRequest request) throws java.io.IOException {
        if (!request.getParameterMap().isEmpty() || request.getInputStream().read() != -1)
            throw new IntegrationException(400, "INVALID_REQUEST", "Mock import accepts no body or query parameters");
        return imports.importFor(UUID.fromString(authentication.getName()));
    }
    @ExceptionHandler(IntegrationException.class)
    ResponseEntity<ApiError> error(IntegrationException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(ApiError.of(exception.status(), exception.code(), exception.getMessage(), request.getRequestURI()));
    }
}
