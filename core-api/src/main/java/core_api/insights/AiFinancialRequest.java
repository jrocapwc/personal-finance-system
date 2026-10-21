package core_api.insights;

import com.fasterxml.jackson.annotation.JsonFormat;
import core_api.dashboard.DashboardResponse;
import java.math.BigDecimal;
import java.util.List;

public record AiFinancialRequest(int month, int year,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalIncome,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalExpenses,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal netSavings,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal savingsRate,
        List<ExpenseCategory> expensesByCategory, List<BudgetUsage> budgetUtilization) {
    public record ExpenseCategory(Long categoryId, String categoryName,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {}
    public record BudgetUsage(Long categoryId,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monthlyLimit,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal spentAmount, Integer alertThreshold) {}

    public static AiFinancialRequest from(DashboardResponse dashboard) {
        var summary = dashboard.summary();
        return new AiFinancialRequest(dashboard.month(), dashboard.year(), summary.totalIncome(), summary.totalExpenses(),
                summary.netSavings(), summary.savingsRate(), dashboard.expensesByCategory().stream()
                .map(category -> new ExpenseCategory(category.categoryId(), category.categoryName(), category.amount())).toList(),
                dashboard.budgetUtilization().stream().map(budget -> new BudgetUsage(budget.categoryId(),
                        budget.monthlyLimit(), budget.spentAmount(), budget.alertThreshold())).toList());
    }
}
