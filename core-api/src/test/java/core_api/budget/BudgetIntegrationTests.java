package core_api.budget;

import com.jayway.jsonpath.JsonPath;
import core_api.JwtTestSupport;
import core_api.category.Category;
import core_api.category.CategoryRepository;
import core_api.category.CategoryType;
import core_api.security.TokenService;
import core_api.user.User;
import core_api.user.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BudgetIntegrationTests extends JwtTestSupport {
    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private CategoryRepository categories;
    @Autowired private BudgetRepository budgets;
    @Autowired private TokenService tokens;
    @Autowired private PasswordEncoder passwords;
    @Autowired private ObjectMapper mapper;
    private String passwordHash;

    @BeforeAll
    void preparePasswordHash() { passwordHash = passwords.encode("Budget-test-password!"); }

    @Test
    void createsListsAndRetrievesBudgetWithSafeInitialUtilization() throws Exception {
        var account = account();
        var created = create(account, body(account.expense(), 10, 2026, "1000.00", 80));
        assertThat(created.statusCode()).isEqualTo(201);
        long id = id(created);
        assertUsage(created, "0.00", "1000.00", "0.00", "SAFE");
        var get = get(account, id);
        assertThat(get.statusCode()).isEqualTo(200);
        assertThat(id(get)).isEqualTo(id);
        assertThat(get.body()).doesNotContain("userId", "password", "categoryType", account.id().toString(), "\"user\"");
        var listed = list(account, 10, 2026);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(ids(listed)).containsExactly(id);
        assertThat(ids(list(account, 11, 2026))).isEmpty();
        assertThat(ids(list(account, 10, 2027))).isEmpty();
        assertThat(ids(list(account(), 10, 2026))).isEmpty();
        assertThat(JsonPath.<String>read(get.body(), "$.createdAt")).isNotBlank();
    }

    @Test
    void updatesCategoryPeriodAndLimitAndPreservesCreationTime() throws Exception {
        var account = account();
        long id = id(create(account, body(account.expense(), 10, 2026, "100", 80)));
        var before = budgets.findById(id).orElseThrow();
        expense(account, account.otherExpense(), "60", "2026-11-01");
        var updated = request("PUT", "/api/v1/budgets/" + id, body(account.otherExpense(), 11, 2026, "200.00", 25), account.token());
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(id(updated)).isEqualTo(id);
        assertUsage(updated, "60.00", "140.00", "30.00", "WARNING");
        var after = budgets.findById(id).orElseThrow();
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThat(after.getUpdatedAt()).isAfter(before.getUpdatedAt());
        assertThat(after.getCategoryId()).isEqualTo(account.otherExpense());
        assertThat(ids(list(account, 10, 2026))).isEmpty();
        assertThat(ids(list(account, 11, 2026))).containsExactly(id);
        assertThat(request("PUT", "/api/v1/budgets/" + id, body(account.otherExpense(), 11, 2026, "200", 25), account.token()).statusCode()).isEqualTo(200);
    }

    @Test
    void deletesBudgetAndLeavesTransactionsAndCategoryUntouched() throws Exception {
        var account = account();
        long id = id(create(account, body(account.expense(), 10, 2026, "100", 80)));
        long tx = id(expense(account, account.expense(), "20", "2026-10-19"));
        var deleted = request("DELETE", "/api/v1/budgets/" + id, null, account.token());
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(deleted.body()).isEmpty();
        assertError(get(account, id), 404, "BUDGET_NOT_FOUND");
        assertError(request("PUT", "/api/v1/budgets/" + id, body(account.expense(), 10, 2026, "100", 80), account.token()), 404, "BUDGET_NOT_FOUND");
        assertError(request("DELETE", "/api/v1/budgets/" + id, null, account.token()), 404, "BUDGET_NOT_FOUND");
        assertThat(request("GET", "/api/v1/transactions/" + tx, null, account.token()).statusCode()).isEqualTo(200);
        assertThat(categories.findById(account.expense())).isPresent();
    }

    @Test
    void duplicateCreateAndUpdateReturn409AndOtherPeriodsRemainAllowed() throws Exception {
        var account = account();
        var payload = body(account.expense(), 10, 2026, "100", 80);
        assertThat(create(account, payload).statusCode()).isEqualTo(201);
        assertError(create(account, payload), 409, "DUPLICATE_BUDGET");
        long other = id(create(account, body(account.otherExpense(), 10, 2026, "100", 80)));
        assertError(request("PUT", "/api/v1/budgets/" + other, payload, account.token()), 409, "DUPLICATE_BUDGET");
        assertThat(budgets.findById(other).orElseThrow().getCategoryId()).isEqualTo(account.otherExpense());
        assertThat(create(account, body(account.expense(), 11, 2026, "100", 80)).statusCode()).isEqualTo(201);
        assertThat(create(account, body(account.expense(), 10, 2027, "100", 80)).statusCode()).isEqualTo(201);
    }

    @Test
    void concurrentDuplicateCreateReturnsOne201AndOne409() throws Exception {
        var account = account();
        var payload = body(account.expense(), 10, 2026, "100", 80);
        var a = CompletableFuture.supplyAsync(() -> uncheckedCreate(account, payload));
        var b = CompletableFuture.supplyAsync(() -> uncheckedCreate(account, payload));
        assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        assertThat(ids(list(account, 10, 2026))).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.001", "100000000000000000.00"})
    void invalidAmountsAreRejected(String amount) throws Exception {
        var account = account();
        assertError(create(account, body(account.expense(), 10, 2026, amount, 80)), 400, "VALIDATION_FAILED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"month-low", "month-high", "year-low", "year-high", "threshold-low", "threshold-high",
            "missing-limit", "missing-month", "missing-year", "missing-threshold", "missing-category", "userId", "spentAmount", "id"})
    void invalidFieldsAndOwnershipOrSpendingInjectionReturn400(String scenario) throws Exception {
        var account = account();
        var payload = body(account.expense(), 10, 2026, "100", 80);
        switch (scenario) {
            case "month-low" -> payload.put("month", 0);
            case "month-high" -> payload.put("month", 13);
            case "year-low" -> payload.put("year", 1999);
            case "year-high" -> payload.put("year", 2101);
            case "threshold-low" -> payload.put("alertThreshold", 0);
            case "threshold-high" -> payload.put("alertThreshold", 100);
            case "missing-limit" -> payload.remove("monthlyLimit");
            case "missing-month" -> payload.remove("month");
            case "missing-year" -> payload.remove("year");
            case "missing-threshold" -> payload.remove("alertThreshold");
            case "missing-category" -> payload.remove("categoryId");
            case "userId" -> payload.put("userId", UUID.randomUUID().toString());
            case "spentAmount" -> payload.put("spentAmount", 1);
            case "id" -> payload.put("id", 99);
            default -> throw new IllegalStateException();
        }
        assertThat(create(account, payload).statusCode()).isEqualTo(400);
        assertThat(ids(list(account, 10, 2026))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?month=10", "?year=2026", "?month=0&year=2026", "?month=13&year=2026",
            "?month=10&year=1999", "?month=10&year=2101", "?month=abc&year=2026"})
    void invalidOrMissingMonthlyFiltersReturn400(String query) throws Exception {
        var account = account();
        assertError(request("GET", "/api/v1/budgets" + query, null, account.token()), 400, "INVALID_REQUEST");
    }

    @Test
    void rejectsIncomeAndForeignCategoriesOnCreateAndUpdate() throws Exception {
        var account = account();
        var other = account();
        assertError(create(account, body(account.income(), 10, 2026, "100", 80)), 400, "INVALID_REQUEST");
        assertError(create(account, body(other.expense(), 10, 2026, "100", 80)), 404, "CATEGORY_NOT_FOUND");
        assertError(create(account, body(Long.MAX_VALUE, 10, 2026, "100", 80)), 404, "CATEGORY_NOT_FOUND");
        long id = id(create(account, body(account.expense(), 10, 2026, "100", 80)));
        assertError(request("PUT", "/api/v1/budgets/" + id, body(account.income(), 10, 2026, "100", 80), account.token()), 400, "INVALID_REQUEST");
        assertError(request("PUT", "/api/v1/budgets/" + id, body(other.expense(), 10, 2026, "100", 80), account.token()), 404, "CATEGORY_NOT_FOUND");
        assertThat(budgets.findById(id).orElseThrow().getCategoryId()).isEqualTo(account.expense());
    }

    @Test
    void foreignUserCannotReadUpdateOrDeleteBudget() throws Exception {
        var a = account();
        var b = account();
        long id = id(create(b, body(b.expense(), 10, 2026, "100", 80)));
        assertError(get(a, id), 404, "BUDGET_NOT_FOUND");
        assertError(request("PUT", "/api/v1/budgets/" + id, body(a.expense(), 10, 2026, "100", 80), a.token()), 404, "BUDGET_NOT_FOUND");
        assertError(request("DELETE", "/api/v1/budgets/" + id, null, a.token()), 404, "BUDGET_NOT_FOUND");
        assertThat(ids(list(a, 10, 2026))).isEmpty();
        assertThat(budgets.findById(id)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "get", "create", "update", "delete"})
    void missingAndInvalidJwtReturn401ForAllBudgetMethods(String operation) throws Exception {
        String method = switch (operation) { case "create" -> "POST"; case "update" -> "PUT"; case "delete" -> "DELETE"; default -> "GET"; };
        String path = "/api/v1/budgets" + (List.of("get", "update", "delete").contains(operation) ? "/1" : "?month=10&year=2026");
        assertError(request(method, path, null, null), 401, "UNAUTHORIZED");
        assertError(request(method, path, null, "not-a-jwt"), 401, "UNAUTHORIZED");
    }

    @Test
    void spendingChangesImmediatelyAfterTransactionCreateEditAndDelete() throws Exception {
        var account = account();
        long id = id(create(account, body(account.expense(), 10, 2026, "100.00", 80)));
        assertUsage(get(account, id), "0", "100", "0", "SAFE");
        long tx = id(expense(account, account.expense(), "79.99", "2026-10-19"));
        assertUsage(get(account, id), "79.99", "20.01", "79.99", "SAFE");
        assertThat(request("PUT", "/api/v1/transactions/" + tx,
                transactionBody(account.expense(), "EXPENSE", "80.00", "2026-10-19"), account.token()).statusCode()).isEqualTo(200);
        assertUsage(get(account, id), "80", "20", "80", "WARNING");
        var listed = list(account, 10, 2026);
        assertThat(JsonPath.<String>read(listed.body(), "$[0].budgetStatus")).isEqualTo("WARNING");
        assertThat(request("PUT", "/api/v1/transactions/" + tx,
                transactionBody(account.expense(), "EXPENSE", "100.00", "2026-10-19"), account.token()).statusCode()).isEqualTo(200);
        assertUsage(get(account, id), "100", "0", "100", "EXCEEDED");
        expense(account, account.expense(), "25.50", "2026-10-19");
        assertUsage(get(account, id), "125.50", "-25.50", "125.50", "EXCEEDED");
        assertThat(request("DELETE", "/api/v1/transactions/" + tx, null, account.token()).statusCode()).isEqualTo(204);
        assertUsage(get(account, id), "25.50", "74.50", "25.50", "SAFE");
    }

    @Test
    void dateCategoryAndOwnerBoundariesAndIncomeAreExcludedIncludingLeapYear() throws Exception {
        var account = account();
        var other = account();
        long id = id(create(account, body(account.expense(), 2, 2024, "100", 80)));
        expense(account, account.expense(), "500", "2024-01-31");
        expense(account, account.expense(), "10.01", "2024-02-01");
        expense(account, account.expense(), "20.02", "2024-02-29");
        expense(account, account.expense(), "500", "2024-03-01");
        expense(account, account.otherExpense(), "500", "2024-02-15");
        expense(other, other.expense(), "500", "2024-02-15");
        assertThat(request("POST", "/api/v1/transactions", transactionBody(account.income(), "INCOME", "500", "2024-02-15"), account.token()).statusCode()).isEqualTo(201);
        assertUsage(get(account, id), "30.03", "69.97", "30.03", "SAFE");
        assertThat(new BigDecimal(JsonPath.read(list(account, 2, 2024).body(), "$[0].spentAmount").toString())).isEqualByComparingTo("30.03");
        long december = id(create(account, body(account.expense(), 12, 2026, "100", 80)));
        expense(account, account.expense(), "7.77", "2026-12-31");
        expense(account, account.expense(), "500", "2027-01-01");
        assertUsage(get(account, december), "7.77", "92.23", "7.77", "SAFE");
    }

    @Test
    void movingTransactionToAnotherMonthOrCategoryRecalculatesBothBudgets() throws Exception {
        var account = account();
        long october = id(create(account, body(account.expense(), 10, 2026, "100", 80)));
        long november = id(create(account, body(account.otherExpense(), 11, 2026, "100", 80)));
        long tx = id(expense(account, account.expense(), "50", "2026-10-19"));
        assertUsage(get(account, october), "50", "50", "50", "SAFE");
        assertThat(request("PUT", "/api/v1/transactions/" + tx,
                transactionBody(account.otherExpense(), "EXPENSE", "50", "2026-11-01"), account.token()).statusCode()).isEqualTo(200);
        assertUsage(get(account, october), "0", "100", "0", "SAFE");
        assertUsage(get(account, november), "50", "50", "50", "SAFE");
    }

    @Test
    void categoryReferencedOnlyByBudgetCannotBeDeletedOrRetyped() throws Exception {
        var account = account();
        long id = id(create(account, body(account.expense(), 10, 2026, "100", 80)));
        String path = "/api/v1/categories/" + account.expense();
        assertError(request("DELETE", path, null, account.token()), 409, "CATEGORY_IN_USE");
        assertError(request("PUT", path, Map.of("name", "Changed", "type", "INCOME"), account.token()), 409, "CATEGORY_IN_USE");
        assertThat(request("PUT", path, Map.of("name", "Changed", "type", "EXPENSE", "color", "#ABC"), account.token()).statusCode()).isEqualTo(200);
        assertThat(request("DELETE", "/api/v1/budgets/" + id, null, account.token()).statusCode()).isEqualTo(204);
        assertThat(request("DELETE", path, null, account.token()).statusCode()).isEqualTo(204);
    }

    private record Account(UUID id, String token, long expense, long otherExpense, long income) {}
    private Account account() {
        var user = users.saveAndFlush(new User("Budget User", UUID.randomUUID() + "@example.com", passwordHash));
        var expense = categories.saveAndFlush(new Category(user, "Expense Test", CategoryType.EXPENSE, null));
        var otherExpense = categories.saveAndFlush(new Category(user, "Other Expense Test", CategoryType.EXPENSE, null));
        var income = categories.saveAndFlush(new Category(user, "Income Test", CategoryType.INCOME, null));
        return new Account(user.getId(), tokens.issue(user.getId()), expense.getId(), otherExpense.getId(), income.getId());
    }
    private HashMap<String, Object> body(long category, int month, int year, String limit, int threshold) {
        var result = new HashMap<String, Object>();
        result.put("categoryId", category); result.put("month", month); result.put("year", year);
        result.put("monthlyLimit", new BigDecimal(limit)); result.put("alertThreshold", threshold);
        return result;
    }
    private Map<String, Object> transactionBody(long category, String type, String amount, String date) {
        return Map.of("categoryId", category, "type", type, "amount", new BigDecimal(amount), "transactionDate", date);
    }
    private HttpResponse<String> expense(Account account, long category, String amount, String date) throws Exception {
        var response = request("POST", "/api/v1/transactions", transactionBody(category, "EXPENSE", amount, date), account.token());
        assertThat(response.statusCode()).isEqualTo(201);
        return response;
    }
    private HttpResponse<String> create(Account account, Map<String, Object> body) throws Exception {
        return request("POST", "/api/v1/budgets", body, account.token());
    }
    private int uncheckedCreate(Account account, Map<String, Object> body) {
        try { return create(account, body).statusCode(); } catch (Exception exception) { throw new CompletionException(exception); }
    }
    private HttpResponse<String> get(Account account, long id) throws Exception { return request("GET", "/api/v1/budgets/" + id, null, account.token()); }
    private HttpResponse<String> list(Account account, int month, int year) throws Exception { return request("GET", "/api/v1/budgets?month=" + month + "&year=" + year, null, account.token()); }
    private HttpResponse<String> request(String method, String path, Object body, String token) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(20));
            if (token != null) { builder.header("Authorization", "Bearer " + token); }
            if (body != null) { builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))); }
            else { builder.method(method, HttpRequest.BodyPublishers.noBody()); }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    private long id(HttpResponse<String> response) { assertThat(response.statusCode()).isIn(200, 201); return JsonPath.<Number>read(response.body(), "$.id").longValue(); }
    private List<Long> ids(HttpResponse<String> response) { List<Number> values = JsonPath.read(response.body(), "$[*].id"); return values.stream().map(Number::longValue).toList(); }
    private void assertUsage(HttpResponse<String> response, String spent, String remaining, String percentage, String status) {
        assertThat(response.statusCode()).isIn(200, 201);
        assertThat(new BigDecimal(JsonPath.read(response.body(), "$.spentAmount").toString())).isEqualByComparingTo(spent);
        assertThat(new BigDecimal(JsonPath.read(response.body(), "$.remainingAmount").toString())).isEqualByComparingTo(remaining);
        assertThat(new BigDecimal(JsonPath.read(response.body(), "$.percentageUsed").toString())).isEqualByComparingTo(percentage);
        assertThat(JsonPath.<String>read(response.body(), "$.budgetStatus")).isEqualTo(status);
    }
    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo(code);
        assertThat(response.body()).doesNotContain("passwordHash", "stackTrace");
    }
}
