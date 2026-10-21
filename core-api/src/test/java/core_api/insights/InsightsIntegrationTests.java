package core_api.insights;

import com.jayway.jsonpath.JsonPath;
import core_api.JwtTestSupport;
import core_api.budget.BudgetRequest;
import core_api.budget.BudgetService;
import core_api.category.Category;
import core_api.category.CategoryRepository;
import core_api.category.CategoryType;
import core_api.security.TokenService;
import core_api.transaction.Transaction;
import core_api.transaction.TransactionRepository;
import core_api.transaction.TransactionRequest;
import core_api.user.User;
import core_api.user.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InsightsIntegrationTests extends JwtTestSupport {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired TransactionRepository transactions;
    @Autowired BudgetService budgets;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder passwords;
    @MockitoBean FinancialInsightsClient upstream;
    private String hash;
    private static final InsightsResponse RESPONSE = new InsightsResponse("Owned insights", List.of("Savings"), List.of(),
            List.of("Review spending"), List.of(), "Educational; not professional financial, investment, tax or legal advice.");

    @BeforeAll void prepareHash() { hash = passwords.encode("insights-test-only-password"); }
    @BeforeEach void prepareResponse() { when(upstream.analyze(any())).thenReturn(RESPONSE); }

    @Test void authenticatedRequestUsesOnlyOwnersRealDashboardData() throws Exception {
        var a = account();
        var b = account();
        var income = category(a, "A Income", CategoryType.INCOME);
        var food = category(a, "A Food", CategoryType.EXPENSE);
        var housing = category(a, "A Housing", CategoryType.EXPENSE);
        transaction(a, income, "1000.00", "2026-10-01");
        transaction(a, food, "400.00", "2026-10-15");
        transaction(a, housing, "450.00", "2026-10-16");
        transaction(a, food, "999.00", "2026-11-01");
        var otherCategory = category(b, "B private", CategoryType.EXPENSE);
        transaction(b, otherCategory, "99999.00", "2026-10-15");
        budgets.create(a.getId(), new BudgetRequest(food.getId(), 10, 2026, new BigDecimal("500.00"), 80));
        budgets.create(b.getId(), new BudgetRequest(otherCategory.getId(), 10, 2026, new BigDecimal("99999.00"), 80));
        var response = get("?month=10&year=2026", tokens.issue(a.getId()));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.summary")).isEqualTo(RESPONSE.summary());
        assertThat(JsonPath.<List<String>>read(response.body(), "$.positiveObservations")).containsExactly("Savings");
        assertThat(JsonPath.<String>read(response.body(), "$.disclaimer")).contains("Educational");
        assertThat(response.body()).doesNotContain(InsightsFixtures.KEY, a.getEmail(), a.getPasswordHash(), a.getId().toString(), "B private");
        var captured = org.mockito.ArgumentCaptor.forClass(AiFinancialRequest.class);
        verify(upstream).analyze(captured.capture());
        var data = captured.getValue();
        assertThat(data.month()).isEqualTo(10);
        assertThat(data.year()).isEqualTo(2026);
        assertThat(data.totalIncome()).isEqualByComparingTo("1000.00");
        assertThat(data.totalExpenses()).isEqualByComparingTo("850.00");
        assertThat(data.netSavings()).isEqualByComparingTo("150.00");
        assertThat(data.savingsRate()).isEqualByComparingTo("15.00");
        assertThat(data.expensesByCategory()).extracting(AiFinancialRequest.ExpenseCategory::categoryId)
                .containsExactly(housing.getId(), food.getId());
        assertThat(data.budgetUtilization()).hasSize(1);
        assertThat(data.budgetUtilization().getFirst().categoryId()).isEqualTo(food.getId());
        assertThat(data.budgetUtilization().getFirst().spentAmount()).isEqualByComparingTo("400.00");
    }

    @Test void emptyMonthPreservesNullSavingsRateAndEmptyLists() throws Exception {
        var account = account();
        assertThat(get("?month=1&year=2026", tokens.issue(account.getId())).statusCode()).isEqualTo(200);
        var captured = org.mockito.ArgumentCaptor.forClass(AiFinancialRequest.class);
        verify(upstream).analyze(captured.capture());
        var data = captured.getValue();
        assertThat(data.totalIncome()).isZero();
        assertThat(data.totalExpenses()).isZero();
        assertThat(data.netSavings()).isZero();
        assertThat(data.savingsRate()).isNull();
        assertThat(data.expensesByCategory()).isEmpty();
        assertThat(data.budgetUtilization()).isEmpty();
    }

    @Test void zeroIncomeWithExpensesIsPassedWithoutDivisionOrFabrication() throws Exception {
        var account = account();
        transaction(account, category(account, "Expense", CategoryType.EXPENSE), "20.00", "2026-10-01");
        assertThat(get("?month=10&year=2026", tokens.issue(account.getId())).statusCode()).isEqualTo(200);
        var captured = org.mockito.ArgumentCaptor.forClass(AiFinancialRequest.class);
        verify(upstream).analyze(captured.capture());
        assertThat(captured.getValue().savingsRate()).isNull();
        assertThat(captured.getValue().netSavings()).isEqualByComparingTo("-20.00");
    }

    @ParameterizedTest @ValueSource(strings = {"", "?month=10", "?year=2026", "?month=0&year=2026", "?month=13&year=2026",
            "?month=10&year=1999", "?month=10&year=2101", "?month=x&year=2026", "?month=10&year=x",
            "?month=10&year=2026&userId=123", "?month=10&year=2026&month=11"})
    void invalidPeriodsAndOwnershipParametersNeverReachUpstream(String query) throws Exception {
        var response = get(query, tokens.issue(account().getId()));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(upstream);
    }

    @Test void missingAndInvalidJwtNeverReachUpstream() throws Exception {
        assertThat(get("?month=10&year=2026", null).statusCode()).isEqualTo(401);
        assertThat(get("?month=10&year=2026", "invalid-token").statusCode()).isEqualTo(401);
        verifyNoInteractions(upstream);
    }

    @Test void upstreamFailureUsesExistingErrorEnvelopeAnd502() throws Exception {
        when(upstream.analyze(any())).thenThrow(UpstreamInsightsException.failure());
        var response = get("?month=10&year=2026", tokens.issue(account().getId()));
        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("INSIGHTS_UNAVAILABLE");
        assertThat(JsonPath.<String>read(response.body(), "$.path")).isEqualTo("/api/v1/insights");
        assertThat(response.body()).doesNotContain(InsightsFixtures.KEY, "stackTrace", "accessToken");
    }

    @Test void upstreamTimeoutReturns504WithoutSuccessfulInsightFields() throws Exception {
        when(upstream.analyze(any())).thenThrow(UpstreamInsightsException.timeout());
        var response = get("?month=10&year=2026", tokens.issue(account().getId()));
        assertThat(response.statusCode()).isEqualTo(504);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("INSIGHTS_TIMEOUT");
        assertThat(response.body()).doesNotContain("positiveObservations", "recommendations");
    }

    private User account() { return users.saveAndFlush(new User("Insights User", UUID.randomUUID() + "@example.com", hash)); }
    private Category category(User user, String name, CategoryType type) {
        return categories.saveAndFlush(new Category(user, name, type, null));
    }
    private void transaction(User user, Category category, String amount, String date) {
        transactions.saveAndFlush(new Transaction(user, category, new TransactionRequest(category.getId(), category.getType(),
                new BigDecimal(amount), "private description must never be forwarded", LocalDate.parse(date))));
    }
    private HttpResponse<String> get(String query, String token) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/insights" + query))
                    .timeout(Duration.ofSeconds(20));
            if (token != null) { request.header("Authorization", "Bearer " + token); }
            return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
