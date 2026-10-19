package core_api.transaction;

import core_api.category.CategoryType;
import core_api.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Manual income and expense transactions owned by the authenticated user")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", description = "Invalid input, filters, or category type mismatch",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "401", description = "Authentication required",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "404", description = "Transaction or category missing or unavailable to this user",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class TransactionController {
    private final TransactionService transactions;

    public TransactionController(TransactionService transactions) { this.transactions = transactions; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a manual transaction")
    @ApiResponse(responseCode = "201", description = "Transaction created", useReturnTypeSchema = true)
    public TransactionResponse create(Authentication authentication, @Valid @RequestBody TransactionRequest request) {
        return transactions.create(userId(authentication), request);
    }

    @GetMapping
    @Operation(summary = "List my transactions", description = "Inclusive date filters; keyword matches description as "
            + "literal case-insensitive text. page starts at 0; size is 1..100, default 20. "
            + "sort: id, amount, transactionDate, createdAt, updatedAt, description, type; direction: ASC or DESC. "
            + "Defaults to transactionDate DESC with an id tie-breaker.")
    public TransactionPageResponse list(Authentication authentication,
            @RequestParam(required = false) CategoryType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "transactionDate") String sort,
            @RequestParam(defaultValue = "DESC") String direction) {
        return transactions.list(userId(authentication), new TransactionQuery(
                type, categoryId, startDate, endDate, keyword, page, size, sort, direction));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get my transaction")
    public TransactionResponse get(Authentication authentication, @PathVariable Long id) {
        return transactions.get(userId(authentication), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace my transaction", description = "Provide all required fields; omitted description is cleared. Owner and source cannot change.")
    public TransactionResponse update(Authentication authentication, @PathVariable Long id,
                                      @Valid @RequestBody TransactionRequest request) {
        return transactions.update(userId(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete my transaction")
    @ApiResponse(responseCode = "204", description = "Transaction deleted", content = @Content)
    public void delete(Authentication authentication, @PathVariable Long id) {
        transactions.delete(userId(authentication), id);
    }

    private UUID userId(Authentication authentication) { return UUID.fromString(authentication.getName()); }
}
