package core_api.integration;

import core_api.category.CategoryType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Strict boundary contract: no binary floating point or provider-supplied owner/category IDs. */
public record MockTransaction(String externalTransactionId, CategoryType type, BigDecimal amount,
                              String description, LocalDate transactionDate, String categoryHint) {
    private static final Set<String> FIELDS = Set.of("externalTransactionId", "type", "amount", "description", "transactionDate", "categoryHint", "source");
    public MockTransaction {
        if (externalTransactionId == null || !externalTransactionId.matches("[A-Za-z0-9_-]{1,100}") || type == null ||
                amount == null || amount.signum() <= 0 || amount.scale() < 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 17 ||
                description == null || description.isBlank() || description.length() > 255 || transactionDate == null ||
                transactionDate.getYear() < 1 || transactionDate.getYear() > 9999 || categoryHint == null || !categoryHint.matches("[A-Z][A-Z_]{0,99}")) {
            throw new InvalidMockTransactionException();
        }
    }
    public static MockTransaction decode(JsonNode node) {
        if (node == null || !node.isObject() || !Set.copyOf(node.propertyNames()).equals(FIELDS)) throw new InvalidMockTransactionException();
        for (var field : FIELDS) if (!node.get(field).isString()) throw new InvalidMockTransactionException();
        if (!"MOCK_PROVIDER".equals(node.get("source").asString()) ||
                !node.get("amount").asString().matches("(0|[1-9][0-9]{0,16})\\.[0-9]{2}") ||
                !node.get("transactionDate").asString().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new InvalidMockTransactionException();
        try {
            return new MockTransaction(node.get("externalTransactionId").asString(), CategoryType.valueOf(node.get("type").asString()),
                    new BigDecimal(node.get("amount").asString()), node.get("description").asString(),
                    LocalDate.parse(node.get("transactionDate").asString()), node.get("categoryHint").asString());
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new InvalidMockTransactionException();
        }
    }
    @Override public String toString() { return "MockTransaction[redacted]"; }
}
