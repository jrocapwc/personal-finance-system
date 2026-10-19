package core_api.transaction;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public final class TransactionSpecifications {
    private TransactionSpecifications() {}

    public static Specification<Transaction> ownedAndMatching(UUID userId, TransactionQuery filter) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            // Ownership is mandatory even when all optional filters are absent.
            predicates.add(builder.equal(root.get("user").get("id"), userId));
            if (filter.type() != null) { predicates.add(builder.equal(root.get("type"), filter.type())); }
            if (filter.categoryId() != null) { predicates.add(builder.equal(root.get("category").get("id"), filter.categoryId())); }
            if (filter.startDate() != null) { predicates.add(builder.greaterThanOrEqualTo(root.get("transactionDate"), filter.startDate())); }
            if (filter.endDate() != null) { predicates.add(builder.lessThanOrEqualTo(root.get("transactionDate"), filter.endDate())); }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                var literal = filter.keyword().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(builder.like(builder.lower(root.get("description")), "%" + literal + "%", '\\'));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
