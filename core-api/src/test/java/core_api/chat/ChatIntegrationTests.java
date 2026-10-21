package core_api.chat;

import core_api.insights.FinancialInsightsClient;
import core_api.insights.UpstreamInsightsException;

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
class ChatIntegrationTests extends JwtTestSupport {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired TransactionRepository transactions;
    @Autowired BudgetService budgets;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder passwords;
    @MockitoBean FinancialInsightsClient upstream;
    private String hash;
    private static final ChatResponse RESPONSE = new ChatResponse("Owned answer", "rule-based", "Educational; not professional financial, investment, tax or legal advice.");

    @BeforeAll void prepareHash() { hash = passwords.encode("insights-test-only-password"); }
    @BeforeEach void prepareResponse() { when(upstream.chat(any())).thenReturn(RESPONSE); }

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
        assertThat(JsonPath.<String>read(response.body(), "$.answer")).isEqualTo(RESPONSE.answer());
        assertThat(JsonPath.<String>read(response.body(), "$.provider")).isEqualTo("rule-based");
        assertThat(JsonPath.<String>read(response.body(), "$.disclaimer")).contains("Educational");
        assertThat(response.body()).doesNotContain("test-only-internal-key-not-for-real-use", a.getEmail(), a.getPasswordHash(), a.getId().toString(), "B private");
        var captured = org.mockito.ArgumentCaptor.forClass(ChatUpstreamRequest.class);
        verify(upstream).chat(captured.capture());
        var data = captured.getValue().financialSummary();
        assertThat(data.month()).isEqualTo(10);
        assertThat(data.year()).isEqualTo(2026);
        assertThat(data.totalIncome()).isEqualByComparingTo("1000.00");
        assertThat(data.totalExpenses()).isEqualByComparingTo("850.00");
        assertThat(data.netSavings()).isEqualByComparingTo("150.00");
        assertThat(data.savingsRate()).isEqualByComparingTo("15.00");
        assertThat(data.expensesByCategory()).extracting(core_api.insights.AiFinancialRequest.ExpenseCategory::categoryId)
                .containsExactly(housing.getId(), food.getId());
        assertThat(data.budgetUtilization()).hasSize(1);
        assertThat(data.budgetUtilization().getFirst().categoryId()).isEqualTo(food.getId());
        assertThat(data.budgetUtilization().getFirst().spentAmount()).isEqualByComparingTo("400.00");
    }

    @Test void emptyMonthPreservesNullSavingsRateAndEmptyLists() throws Exception {
        var account = account();
        assertThat(get("?month=1&year=2026", tokens.issue(account.getId())).statusCode()).isEqualTo(200);
        var captured = org.mockito.ArgumentCaptor.forClass(ChatUpstreamRequest.class);
        verify(upstream).chat(captured.capture());
        var data = captured.getValue().financialSummary();
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
        var captured = org.mockito.ArgumentCaptor.forClass(ChatUpstreamRequest.class);
        verify(upstream).chat(captured.capture());
        assertThat(captured.getValue().financialSummary().savingsRate()).isNull();
        assertThat(captured.getValue().financialSummary().netSavings()).isEqualByComparingTo("-20.00");
    }

    @ParameterizedTest @ValueSource(strings = {"", "?month=10", "?year=2026", "?month=0&year=2026", "?month=13&year=2026",
            "?month=10&year=1999", "?month=10&year=2101", "?month=x&year=2026", "?month=10&year=x",
            "?month=10&year=2026&userId=123"})
    void invalidPeriodsAndOwnershipParametersNeverReachUpstream(String query) throws Exception {
        var response = get(query, tokens.issue(account().getId()));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isIn("INVALID_REQUEST", "VALIDATION_FAILED");
        verifyNoInteractions(upstream);
    }

    @Test void missingAndInvalidJwtNeverReachUpstream() throws Exception {
        assertThat(get("?month=10&year=2026", null).statusCode()).isEqualTo(401);
        assertThat(get("?month=10&year=2026", "invalid-token").statusCode()).isEqualTo(401);
        verifyNoInteractions(upstream);
    }

    @Test void upstreamFailureUsesExistingErrorEnvelopeAnd502() throws Exception {
        when(upstream.chat(any())).thenThrow(UpstreamInsightsException.failure());
        var response = get("?month=10&year=2026", tokens.issue(account().getId()));
        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("INSIGHTS_UNAVAILABLE");
        assertThat(JsonPath.<String>read(response.body(), "$.path")).isEqualTo("/api/v1/chat/messages");
        assertThat(response.body()).doesNotContain("test-only-internal-key-not-for-real-use", "stackTrace", "accessToken");
    }

    @Test void upstreamTimeoutReturns504WithoutSuccessfulInsightFields() throws Exception {
        when(upstream.chat(any())).thenThrow(UpstreamInsightsException.timeout());
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
    @ParameterizedTest @ValueSource(strings = {
            "{\"question\":\" \",\"month\":10,\"year\":2026}",
            "{\"month\":10,\"year\":2026}",
            "{\"question\":\"Explain my income\",\"month\":10,\"year\":2026,\"userId\":\"other\"}",
            "{\"question\":\"Explain my income\",\"month\":10,\"year\":2026,\"financialSummary\":{}}"
    })
    void invalidQuestionsAndClientFinancialContextAreRejected(String body) throws Exception {
        assertThat(post(body, tokens.issue(account().getId())).statusCode()).isEqualTo(400);
        verifyNoInteractions(upstream);
    }

    @Test void oversizedQuestionIsRejected() throws Exception {
        var body = new tools.jackson.databind.json.JsonMapper().writeValueAsString(java.util.Map.of(
                "question", "x".repeat(1001), "month", 10, "year", 2026));
        assertThat(post(body, tokens.issue(account().getId())).statusCode()).isEqualTo(400);
        verifyNoInteractions(upstream);
    }

    private HttpResponse<String> get(String query, String token) throws Exception {
        var fields = new java.util.HashMap<String, Object>();
        fields.put("question", "Explain my income, expenses, savings and budgets.");
        for (var pair : query.replace("?", "").split("&")) {
            if (pair.isEmpty()) { continue; }
            var parts = pair.split("=", 2);
            try { fields.put(parts[0], Integer.valueOf(parts[1])); }
            catch (NumberFormatException exception) { fields.put(parts[0], parts[1]); }
        }
        return post(new tools.jackson.databind.json.JsonMapper().writeValueAsString(fields), token);
    }
    private HttpResponse<String> post(String body, String token) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/chat/messages"))
                    .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (token != null) { request.header("Authorization", "Bearer " + token); }
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
