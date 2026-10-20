package core_api.dashboard;

import java.math.BigDecimal;

public record CategoryBreakdown(Long categoryId, String categoryName, String color, BigDecimal amount) {
}
