package core_api.budget;

import java.math.BigDecimal;
import java.time.Instant;

public record BudgetResponse(Long id, Long categoryId, Integer month, Integer year,
                             BigDecimal monthlyLimit, Integer alertThreshold, BigDecimal spentAmount,
                             BigDecimal remainingAmount, BigDecimal percentageUsed, BudgetStatus budgetStatus,
                             Instant createdAt, Instant updatedAt) {
    public static BudgetResponse from(Budget budget, BigDecimal spent) {
        var usage = BudgetUtilization.calculate(budget.getMonthlyLimit(), spent, budget.getAlertThreshold());
        return new BudgetResponse(budget.getId(), budget.getCategoryId(), budget.getMonth(), budget.getYear(),
                budget.getMonthlyLimit(), budget.getAlertThreshold(), usage.spentAmount(), usage.remainingAmount(),
                usage.percentageUsed(), usage.budgetStatus(), budget.getCreatedAt(), budget.getUpdatedAt());
    }
}
