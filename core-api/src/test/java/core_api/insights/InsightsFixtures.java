package core_api.insights;

import core_api.budget.BudgetResponse;
import core_api.budget.BudgetStatus;
import core_api.dashboard.CategoryBreakdown;
import core_api.dashboard.DashboardResponse;
import core_api.dashboard.FinancialSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

final class InsightsFixtures {
    static final String KEY = "test-only-internal-key-not-for-real-use";
    static final String RESPONSE = """
            {"summary":"Monthly overview","positiveObservations":["Positive savings"],"concerns":[],
             "recommendations":["Review spending"],"suggestedActions":[],"disclaimer":"Educational; not professional financial, investment, tax or legal advice."}
            """;
    static final String REQUEST = """
            {"month":10,"year":2026,"totalIncome":"1000.00","totalExpenses":"850.00","netSavings":"150.00","savingsRate":"15.00",
             "expensesByCategory":[{"categoryId":1,"categoryName":"Food","amount":"400.00"},{"categoryId":2,"categoryName":"Housing","amount":"450.00"}],
             "budgetUtilization":[{"categoryId":1,"monthlyLimit":"500.00","spentAmount":"400.00","alertThreshold":80}]}
            """;
    static AiServiceProperties properties(String key) {
        return new AiServiceProperties(java.net.URI.create("http://127.0.0.1:8000"), key,
                java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(5));
    }
    static DashboardResponse dashboard() {
        return new DashboardResponse(10, 2026, FinancialSummary.from(new BigDecimal("1000.00"), new BigDecimal("850.00")),
                List.of(new CategoryBreakdown(1L, "Food", "#ABC", new BigDecimal("400.00")),
                        new CategoryBreakdown(2L, "Housing", null, new BigDecimal("450.00"))),
                List.of(), List.of(), List.of(), List.of(new BudgetResponse(99L, 1L, 10, 2026, new BigDecimal("500.00"), 80,
                        new BigDecimal("400.00"), new BigDecimal("100.00"), new BigDecimal("80.00"), BudgetStatus.WARNING,
                        Instant.EPOCH, Instant.EPOCH)), List.of());
    }
}
