package core_api.transaction;

import core_api.category.CategoryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionResponse(Long id, Long categoryId, CategoryType type, BigDecimal amount,
                                  String description, LocalDate transactionDate, TransactionSource source,
                                  Instant createdAt, Instant updatedAt) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getCategoryId(), transaction.getType(),
                transaction.getAmount(), transaction.getDescription(), transaction.getTransactionDate(),
                transaction.getSource(), transaction.getCreatedAt(), transaction.getUpdatedAt());
    }
}
