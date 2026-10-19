package core_api.transaction;

import core_api.category.CategoryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @NotNull @Positive Long categoryId,
        @NotNull CategoryType type,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2)
        @Schema(example = "125.50", description = "Positive amount, at most 17 integer digits and 2 decimal places") BigDecimal amount,
        @Size(max = 255) String description,
        @NotNull @Schema(example = "2026-10-19") LocalDate transactionDate) {
}
