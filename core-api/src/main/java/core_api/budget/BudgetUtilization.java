package core_api.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record BudgetUtilization(BigDecimal spentAmount, BigDecimal remainingAmount,
                                BigDecimal percentageUsed, BudgetStatus budgetStatus) {
    public static BudgetUtilization calculate(BigDecimal limit, BigDecimal spent, int threshold) {
        var amount = (spent == null ? BigDecimal.ZERO : spent).setScale(2, RoundingMode.UNNECESSARY);
        var percentNumerator = amount.multiply(BigDecimal.valueOf(100));
        var percentage = percentNumerator.divide(limit, 2, RoundingMode.HALF_UP);
        // Exact cross multiplication avoids false alerts due to rounded display percentages.
        var status = amount.compareTo(limit) >= 0 ? BudgetStatus.EXCEEDED
                : percentNumerator.compareTo(limit.multiply(BigDecimal.valueOf(threshold))) >= 0
                ? BudgetStatus.WARNING : BudgetStatus.SAFE;
        return new BudgetUtilization(amount, limit.subtract(amount).setScale(2), percentage, status);
    }
}
