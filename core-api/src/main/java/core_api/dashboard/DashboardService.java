package core_api.dashboard;

import core_api.budget.BudgetPeriod;
import core_api.budget.BudgetService;
import core_api.category.CategoryType;
import core_api.transaction.TransactionRepository;
import core_api.transaction.TransactionResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final Comparator<CategoryBreakdown> CATEGORY_ORDER = Comparator
            .comparing(CategoryBreakdown::amount, Comparator.reverseOrder())
            .thenComparing(CategoryBreakdown::categoryId);
    private final TransactionRepository transactions;
    private final BudgetService budgets;

    public DashboardService(TransactionRepository transactions, BudgetService budgets) {
        this.transactions = transactions;
        this.budgets = budgets;
    }

    public DashboardResponse get(UUID userId, int month, int year) {
        var period = new BudgetPeriod(month, year);
        var grouped = transactions.summarizeCategories(userId, period.start(), period.endExclusive());
        var expenses = breakdown(grouped, CategoryType.EXPENSE);
        var income = breakdown(grouped, CategoryType.INCOME);
        var summary = FinancialSummary.from(total(income), total(expenses));
        var daily = transactions.sumDailyExpenses(userId, period.start(), period.endExclusive()).stream()
                .collect(Collectors.toMap(TransactionRepository.DashboardDailyTotal::getTransactionDate,
                        TransactionRepository.DashboardDailyTotal::getTotalAmount));
        var recent = transactions.findRecentForDashboard(userId, period.start(), period.endExclusive(), PageRequest.of(0, 5))
                .stream().map(TransactionResponse::from).toList();
        return new DashboardResponse(month, year, summary, expenses, income, dailyTrend(period, daily), recent,
                budgets.list(userId, month, year), expenses.stream().limit(5).toList());
    }

    private List<CategoryBreakdown> breakdown(List<TransactionRepository.DashboardCategoryTotal> rows, CategoryType type) {
        return rows.stream().filter(row -> row.getType() == type)
                .map(row -> new CategoryBreakdown(row.getCategoryId(), row.getCategoryName(), row.getColor(),
                        row.getTotalAmount().setScale(2)))
                .sorted(CATEGORY_ORDER).toList();
    }

    private BigDecimal total(List<CategoryBreakdown> rows) {
        return rows.stream().map(CategoryBreakdown::amount).reduce(ZERO, BigDecimal::add);
    }

    private List<DailyTrend> dailyTrend(BudgetPeriod period, Map<LocalDate, BigDecimal> amounts) {
        return period.start().datesUntil(period.endExclusive())
                .map(date -> new DailyTrend(date, amounts.getOrDefault(date, ZERO).setScale(2))).toList();
    }
}
