package core_api.dashboard;

import com.jayway.jsonpath.JsonPath;
import core_api.JwtTestSupport;
import core_api.budget.BudgetRequest;
import core_api.budget.BudgetService;
import core_api.category.Category;
import core_api.category.CategoryDefaultInitializationRepository;
import core_api.category.CategoryRepository;
import core_api.category.CategoryType;
import core_api.security.TokenService;
import core_api.transaction.Transaction;
import core_api.transaction.TransactionRepository;
import core_api.transaction.TransactionRequest;
import core_api.transaction.TransactionService;
import core_api.user.User;
import core_api.user.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardIntegrationTests extends JwtTestSupport {
    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private CategoryRepository categories;
    @Autowired private CategoryDefaultInitializationRepository initializations;
    @Autowired private TransactionRepository transactions;
    @Autowired private TransactionService transactionService;
    @Autowired private BudgetService budgets;
    @Autowired private TokenService tokens;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JdbcTemplate jdbc;
    private String passwordHash;

    @BeforeAll
    void preparePasswordHash() { passwordHash = passwords.encode("Dashboard-test-password!"); }

    @Test
    void aggregatesIncomeExpensesSavingsAndCategoryBreakdownsWithDailyZeros() throws Exception {
        var account = account();
        var salary = category(account, "Salary", CategoryType.INCOME);
        var freelance = category(account, "Freelance", CategoryType.INCOME);
        var food = category(account, "Food", CategoryType.EXPENSE);
        var housing = category(account, "Housing", CategoryType.EXPENSE);
        transaction(account, salary, "1000.10", "2026-10-01");
        transaction(account, freelance, "499.90", "2026-10-02");
        transaction(account, food, "100.25", "2026-10-01");
        transaction(account, food, "49.75", "2026-10-02");
        transaction(account, housing, "300.00", "2026-10-02");
        var response = dashboard(account, 10, 2026);
        assertThat(response.statusCode()).isEqualTo(200);
        assertMoney(response, "$.summary.totalIncome", "1500.00");
        assertMoney(response, "$.summary.totalExpenses", "450.00");
        assertMoney(response, "$.summary.netSavings", "1050.00");
        assertMoney(response, "$.summary.savingsRate", "70.00");
        assertThat(JsonPath.<List<String>>read(response.body(), "$.expensesByCategory[*].categoryName"))
                .containsExactly("Housing", "Food");
        assertThat(JsonPath.<List<String>>read(response.body(), "$.incomeByCategory[*].categoryName"))
                .containsExactly("Salary", "Freelance");
        assertMoney(response, "$.expensesByCategory[0].amount", "300.00");
        assertMoney(response, "$.expensesByCategory[1].amount", "150.00");
        assertThat(JsonPath.<String>read(response.body(), "$.expensesByCategory[0].color")).isEqualTo("#ABC");
        assertThat(JsonPath.<List<Object>>read(response.body(), "$.dailySpendingTrend")).hasSize(31);
        assertThat(JsonPath.<String>read(response.body(), "$.dailySpendingTrend[0].date")).isEqualTo("2026-10-01");
        assertMoney(response, "$.dailySpendingTrend[0].amount", "100.25");
        assertMoney(response, "$.dailySpendingTrend[1].amount", "349.75");
        assertMoney(response, "$.dailySpendingTrend[2].amount", "0.00");
        assertThat(JsonPath.<String>read(response.body(), "$.dailySpendingTrend[30].date")).isEqualTo("2026-10-31");
        assertThat(response.body()).doesNotContain("userId", "password", account.id().toString(), "\"user\"");
    }

    @Test
    void incomeZeroProducesNullRateEvenWithExpenses() throws Exception {
        var account = account();
        transaction(account, category(account, "Expense", CategoryType.EXPENSE), "30.00", "2026-10-19");
        var response = dashboard(account, 10, 2026);
        assertMoney(response, "$.summary.totalIncome", "0.00");
        assertMoney(response, "$.summary.netSavings", "-30.00");
        assertThat(JsonPath.<Object>read(response.body(), "$.summary.savingsRate")).isNull();
        assertThat(JsonPath.<List<Object>>read(response.body(), "$.incomeByCategory")).isEmpty();
    }

    @Test
    void emptyMonthIsZeroFilledWithoutCreatingCategoriesOrBudgets() throws Exception {
        var account = account();
        assertThat(initializations.existsById(account.id())).isFalse();
        var response = dashboard(account, 2, 2024);
        assertThat(response.statusCode()).isEqualTo(200);
        assertMoney(response, "$.summary.totalIncome", "0");
        assertMoney(response, "$.summary.totalExpenses", "0");
        assertMoney(response, "$.summary.netSavings", "0");
        assertThat(JsonPath.<Object>read(response.body(), "$.summary.savingsRate")).isNull();
        for (var name : List.of("expensesByCategory", "incomeByCategory", "recentTransactions", "budgetUtilization", "topSpendingCategories")) {
            assertThat(JsonPath.<List<Object>>read(response.body(), "$." + name)).isEmpty();
        }
        List<Map<String, Object>> daily = JsonPath.read(response.body(), "$.dailySpendingTrend");
        assertThat(daily).hasSize(29).allSatisfy(day -> assertThat(new BigDecimal(day.get("amount").toString())).isZero());
        assertThat(daily.getLast().get("date")).isEqualTo("2024-02-29");
        assertThat(categories.findAllByUser_IdOrderByTypeAscNormalizedNameAscIdAsc(account.id())).isEmpty();
        assertThat(initializations.existsById(account.id())).isFalse();
    }

    @Test
    void recentTransactionsAreLimitedToFiveAndUseDeterministicDateCreatedAtIdOrder() throws Exception {
        var account = account();
        var expense = category(account, "Expense", CategoryType.EXPENSE);
        var income = category(account, "Income", CategoryType.INCOME);
        var expected = new ArrayList<Long>();
        for (int i = 0; i < 7; i++) {
            expected.add(transaction(account, i % 2 == 0 ? income : expense, "10.00", "2026-10-19").getId());
        }
        transaction(account, expense, "999", "2026-09-30");
        transaction(account, expense, "999", "2026-11-01");
        transaction(account, expense, "999", "2026-10-01");
        jdbc.update("UPDATE transactions SET created_at = TIMESTAMP WITH TIME ZONE '2026-10-20 00:00:00+00' WHERE user_id = ?", account.id());
        var response = dashboard(account, 10, 2026);
        assertThat(ids(response, "$.recentTransactions[*].id")).isEqualTo(expected.reversed().subList(0, 5));
        assertThat(ids(dashboard(account, 10, 2026), "$.recentTransactions[*].id"))
                .isEqualTo(expected.reversed().subList(0, 5));
        assertThat(JsonPath.<List<String>>read(response.body(), "$.recentTransactions[*].transactionDate")).containsOnly("2026-10-19");
    }

    @Test
    void topFiveExpenseCategoriesSortByAmountThenIdAndIncludeOnlyExpenses() throws Exception {
        var account = account();
        var categoryIds = new ArrayList<Long>();
        String[] amounts = {"100", "200", "300", "400", "500", "500", "50"};
        for (int i = 0; i < amounts.length; i++) {
            var category = category(account, "Expense " + i, CategoryType.EXPENSE);
            categoryIds.add(category.getId());
            transaction(account, category, amounts[i], "2026-10-19");
        }
        transaction(account, category(account, "Income", CategoryType.INCOME), "9999", "2026-10-19");
        var response = dashboard(account, 10, 2026);
        assertThat(ids(response, "$.topSpendingCategories[*].categoryId"))
                .containsExactly(categoryIds.get(4), categoryIds.get(5), categoryIds.get(3), categoryIds.get(2), categoryIds.get(1));
        assertThat(JsonPath.<List<Object>>read(response.body(), "$.expensesByCategory")).hasSize(7);
        assertMoney(response, "$.summary.totalExpenses", "2050.00");
    }

    @ParameterizedTest
    @CsvSource({"1,2026", "12,2026"})
    void januaryAndDecemberUseInclusiveStartExclusiveNextMonth(int month, int year) throws Exception {
        var account = account();
        var expense = category(account, "Expense", CategoryType.EXPENSE);
        var income = category(account, "Income", CategoryType.INCOME);
        var period = YearMonth.of(year, month);
        transaction(account, expense, "999", period.atDay(1).minusDays(1).toString());
        transaction(account, expense, "1.10", period.atDay(1).toString());
        transaction(account, expense, "2.20", period.atEndOfMonth().toString());
        transaction(account, expense, "999", period.plusMonths(1).atDay(1).toString());
        transaction(account, income, "9.90", period.atDay(1).toString());
        var response = dashboard(account, month, year);
        assertMoney(response, "$.summary.totalExpenses", "3.30");
        assertMoney(response, "$.summary.totalIncome", "9.90");
        assertMoney(response, "$.summary.netSavings", "6.60");
        assertMoney(response, "$.summary.savingsRate", "66.67");
        assertMoney(response, "$.dailySpendingTrend[0].amount", "1.10");
        assertMoney(response, "$.dailySpendingTrend[30].amount", "2.20");
        assertThat(JsonPath.<List<Object>>read(response.body(), "$.recentTransactions")).hasSize(3);
    }

    @Test
    void budgetUtilizationReusesBudgetApiAndRespondsToTransactionChanges() throws Exception {
        var account = account();
        var expense = category(account, "Expense", CategoryType.EXPENSE);
        var budget = budgets.create(account.id(), new BudgetRequest(expense.getId(), 10, 2026, new BigDecimal("100.00"), 80));
        var tx = transaction(account, expense, "80.00", "2026-10-19");
        var response = dashboard(account, 10, 2026);
        assertThat(ids(response, "$.budgetUtilization[*].id")).containsExactly(budget.id());
        assertMoney(response, "$.budgetUtilization[0].spentAmount", "80.00");
        assertThat(JsonPath.<String>read(response.body(), "$.budgetUtilization[0].budgetStatus")).isEqualTo("WARNING");
        var direct = budgets.get(account.id(), budget.id());
        assertThat(new BigDecimal(JsonPath.read(response.body(), "$.budgetUtilization[0].percentageUsed").toString()))
                .isEqualByComparingTo(direct.percentageUsed());
        transactionService.update(account.id(), tx.getId(), new TransactionRequest(expense.getId(), CategoryType.EXPENSE,
                new BigDecimal("120.00"), null, LocalDate.of(2026, 10, 19)));
        var exceeded = dashboard(account, 10, 2026);
        assertMoney(exceeded, "$.summary.totalExpenses", "120");
        assertThat(JsonPath.<String>read(exceeded.body(), "$.budgetUtilization[0].budgetStatus")).isEqualTo("EXCEEDED");
        transactionService.delete(account.id(), tx.getId());
        var empty = dashboard(account, 10, 2026);
        assertMoney(empty, "$.summary.totalExpenses", "0");
        assertMoney(empty, "$.budgetUtilization[0].spentAmount", "0");
        assertThat(JsonPath.<String>read(empty.body(), "$.budgetUtilization[0].budgetStatus")).isEqualTo("SAFE");
    }

    @Test
    void otherUsersTransactionsCategoriesAndBudgetsAreExcluded() throws Exception {
        var a = account();
        var b = account();
        var aExpense = category(a, "A Expense", CategoryType.EXPENSE);
        var aIncome = category(a, "A Income", CategoryType.INCOME);
        var bExpense = category(b, "B Private Expense", CategoryType.EXPENSE);
        var bIncome = category(b, "B Private Income", CategoryType.INCOME);
        var ownedIncome = transaction(a, aIncome, "100", "2026-10-19");
        var ownedTx = transaction(a, aExpense, "20", "2026-10-19");
        transaction(b, bIncome, "99999", "2026-10-19");
        transaction(b, bExpense, "99999", "2026-10-19");
        var ownBudget = budgets.create(a.id(), new BudgetRequest(aExpense.getId(), 10, 2026, new BigDecimal("50"), 80));
        budgets.create(b.id(), new BudgetRequest(bExpense.getId(), 10, 2026, new BigDecimal("99999"), 80));
        var response = dashboard(a, 10, 2026);
        assertMoney(response, "$.summary.totalIncome", "100");
        assertMoney(response, "$.summary.totalExpenses", "20");
        assertMoney(response, "$.summary.savingsRate", "80");
        assertThat(ids(response, "$.budgetUtilization[*].id")).containsExactly(ownBudget.id());
        assertThat(ids(response, "$.recentTransactions[*].id")).containsExactly(ownedTx.getId(), ownedIncome.getId());
        assertThat(ids(response, "$.expensesByCategory[*].categoryId")).containsExactly(aExpense.getId());
        assertThat(response.body()).doesNotContain("B Private", b.id().toString(), "99999");
        assertMoney(response, "$.dailySpendingTrend[18].amount", "20");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?month=10", "?year=2026", "?month=0&year=2026", "?month=13&year=2026",
            "?month=10&year=1999", "?month=10&year=2101", "?month=bad&year=2026", "?month=10&year=bad",
            "?month=10&year=2026&userId=123", "?month=10&year=2026&sort=amount"})
    void invalidPeriodsOrOwnershipParametersReturn400(String query) throws Exception {
        var response = request(query, account().token());
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void missingAndInvalidJwtReturn401() throws Exception {
        assertThat(request("?month=10&year=2026", null).statusCode()).isEqualTo(401);
        assertThat(request("?month=10&year=2026", "invalid-token").statusCode()).isEqualTo(401);
    }

    private record Account(UUID id, String token) {}
    private Account account() {
        var user = users.saveAndFlush(new User("Dashboard User", UUID.randomUUID() + "@example.com", passwordHash));
        return new Account(user.getId(), tokens.issue(user.getId()));
    }
    private Category category(Account account, String name, CategoryType type) {
        return categories.saveAndFlush(new Category(users.findById(account.id()).orElseThrow(), name, type, "#ABC"));
    }
    private Transaction transaction(Account account, Category category, String amount, String date) {
        return transactions.saveAndFlush(new Transaction(users.findById(account.id()).orElseThrow(), category,
                new TransactionRequest(category.getId(), category.getType(), new BigDecimal(amount), "Dashboard fixture", LocalDate.parse(date))));
    }
    private HttpResponse<String> dashboard(Account account, int month, int year) throws Exception { return request("?month=" + month + "&year=" + year, account.token()); }
    private HttpResponse<String> request(String query, String token) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/dashboard" + query)).timeout(Duration.ofSeconds(20));
            if (token != null) { builder.header("Authorization", "Bearer " + token); }
            return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    private List<Long> ids(HttpResponse<String> response, String path) { List<Number> values = JsonPath.read(response.body(), path); return values.stream().map(Number::longValue).toList(); }
    private void assertMoney(HttpResponse<String> response, String path, String expected) {
        assertThat(new BigDecimal(JsonPath.read(response.body(), path).toString())).isEqualByComparingTo(expected);
    }
}
