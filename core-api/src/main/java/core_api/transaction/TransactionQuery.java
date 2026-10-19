package core_api.transaction;

import core_api.category.CategoryType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

public record TransactionQuery(CategoryType type, Long categoryId, LocalDate startDate, LocalDate endDate,
                               String keyword, int page, int size, String sort, String direction) {
    private static final Set<String> SORT_FIELDS = Set.of("id", "amount", "transactionDate", "createdAt", "updatedAt", "description", "type");

    public TransactionQuery {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new InvalidTransactionRequestException("Use page >= 0 and size between 1 and 100 within the supported offset range");
        }
        if (categoryId != null && categoryId <= 0) {
            throw new InvalidTransactionRequestException("categoryId must be positive");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidTransactionRequestException("startDate must not be after endDate");
        }
        if (keyword != null && keyword.length() > 255) {
            throw new InvalidTransactionRequestException("keyword must not exceed 255 characters");
        }
        keyword = keyword == null ? null : keyword.strip();
        if (sort == null || !SORT_FIELDS.contains(sort)) {
            throw new InvalidTransactionRequestException("Unsupported sort field");
        }
        direction = direction == null ? "" : direction.toUpperCase(Locale.ROOT);
        if (!Set.of("ASC", "DESC").contains(direction)) {
            throw new InvalidTransactionRequestException("direction must be ASC or DESC");
        }
    }

    public PageRequest pageable() {
        var order = Sort.by(Sort.Direction.fromString(direction), sort);
        if (!sort.equals("id")) { order = order.and(Sort.by(Sort.Direction.fromString(direction), "id")); }
        return PageRequest.of(page, size, order);
    }
}
