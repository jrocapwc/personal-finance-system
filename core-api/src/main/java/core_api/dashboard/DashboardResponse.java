package core_api.dashboard;

import core_api.budget.BudgetResponse;
import core_api.transaction.TransactionResponse;

import java.util.List;

public record DashboardResponse(int month, int year, FinancialSummary summary,
                                List<CategoryBreakdown> expensesByCategory,
                                List<CategoryBreakdown> incomeByCategory,
                                List<DailyTrend> dailySpendingTrend,
                                List<TransactionResponse> recentTransactions,
                                List<BudgetResponse> budgetUtilization,
                                List<CategoryBreakdown> topSpendingCategories) {
}
