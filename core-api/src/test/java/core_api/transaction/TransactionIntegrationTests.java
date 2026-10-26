package core_api.transaction;

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
import java.time.LocalDate;
import java.util.ArrayList;
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
class TransactionIntegrationTests extends JwtTestSupport {
    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private CategoryRepository categories;
    @Autowired private TransactionRepository transactions;
    @Autowired private TokenService tokens;
    @Autowired private PasswordEncoder passwords;
    @Autowired private ObjectMapper mapper;
    private String passwordHash;

    @BeforeAll
    void preparePasswordHash() { passwordHash = passwords.encode("Transaction-test-password!"); }

    @ParameterizedTest
    @ValueSource(strings = {"INCOME", "EXPENSE"})
    void createsAndRetrievesExactAmountWithSafeResponse(String type) throws Exception {
        var account = account();
        long category = type.equals("INCOME") ? account.income() : account.expense();
        var response = create(account, category, type, "12345678901234567.89", "Test description", "2026-10-19");
        assertThat(response.statusCode()).isEqualTo(201);
        long id = id(response);
        var stored = transactions.findById(id).orElseThrow();
        assertThat(stored.getAmount()).isEqualByComparingTo("12345678901234567.89");
        assertThat(stored.getSource()).isEqualTo(TransactionSource.MANUAL);
        assertThat(response.body()).contains("12345678901234567.89");
        assertThat(JsonPath.<String>read(response.body(), "$.source")).isEqualTo("MANUAL");
        assertThat(JsonPath.<String>read(response.body(), "$.transactionDate")).isEqualTo("2026-10-19");
        assertThat(JsonPath.<String>read(response.body(), "$.createdAt")).isNotBlank();
        var retrieved = request("GET", "/api/v1/transactions/" + id, null, account.token());
        assertThat(retrieved.statusCode()).isEqualTo(200);
        assertThat(id(retrieved)).isEqualTo(id);
        assertSafe(retrieved.body(), account);
    }

    @Test
    void updatesAllMutableFieldsAndPreservesCreationAndSource() throws Exception {
        var account = account();
        long id = id(create(account, account.expense(), "EXPENSE", "12.34", "Old description", "2026-10-01"));
        var before = transactions.findById(id).orElseThrow();
        var payload = payload(account.income(), "INCOME", "250.00", null, "2026-10-19");
        var response = request("PUT", "/api/v1/transactions/" + id, payload, account.token());
        assertThat(response.statusCode()).isEqualTo(200);
        var after = transactions.findById(id).orElseThrow();
        assertThat(after.getCategoryId()).isEqualTo(account.income());
        assertThat(after.getType()).isEqualTo(CategoryType.INCOME);
        assertThat(after.getAmount()).isEqualByComparingTo("250.00");
        assertThat(after.getDescription()).isNull();
        assertThat(after.getTransactionDate()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThat(after.getUpdatedAt()).isAfter(before.getUpdatedAt());
        assertThat(after.getSource()).isEqualTo(TransactionSource.MANUAL);
    }

    @Test
    void deletesAndSubsequentReadsAndMutationsReturn404() throws Exception {
        var account = account();
        long id = id(create(account, account.expense(), "EXPENSE", "20.00", "Delete me", "2026-10-19"));
        var deleted = request("DELETE", "/api/v1/transactions/" + id, null, account.token());
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(deleted.body()).isEmpty();
        assertThat(transactions.findById(id)).isEmpty();
        assertError(request("GET", "/api/v1/transactions/" + id, null, account.token()), 404, "TRANSACTION_NOT_FOUND");
        assertError(request("PUT", "/api/v1/transactions/" + id,
                payload(account.income(), "INCOME", "1.00", null, "2026-10-19"), account.token()), 404, "TRANSACTION_NOT_FOUND");
        assertError(request("DELETE", "/api/v1/transactions/" + id, null, account.token()), 404, "TRANSACTION_NOT_FOUND");
    }

    @Test
    void listsWithCombinedFiltersInclusiveDatesAndStablePagination() throws Exception {
        var account = account();
        long first = id(create(account, account.expense(), "EXPENSE", "10.00", "Coffee morning", "2026-10-01"));
        long second = id(create(account, account.expense(), "EXPENSE", "20.00", "COFFEE evening", "2026-10-02"));
        create(account, account.expense(), "EXPENSE", "30.00", "Tea", "2026-10-03");
        create(account, account.income(), "INCOME", "1000.00", "Coffee bonus", "2026-10-02");
        var foreign = account();
        create(foreign, foreign.expense(), "EXPENSE", "100.00", "Coffee foreign", "2026-10-01");
        String filters = "?type=EXPENSE&categoryId=" + account.expense()
                + "&startDate=2026-10-01&endDate=2026-10-02&keyword=coffee&size=1&sort=amount&direction=ASC";
        var page0 = list(account, filters);
        assertThat(page0.statusCode()).isEqualTo(200);
        assertThat(contentIds(page0)).containsExactly(first);
        assertThat(JsonPath.<Integer>read(page0.body(), "$.totalElements")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(page0.body(), "$.totalPages")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(page0.body(), "$.page")).isZero();
        assertThat(JsonPath.<Integer>read(page0.body(), "$.size")).isEqualTo(1);
        assertThat(JsonPath.<Boolean>read(page0.body(), "$.first")).isTrue();
        var page1 = list(account, filters + "&page=1");
        assertThat(contentIds(page1)).containsExactly(second);
        assertThat(JsonPath.<Boolean>read(page1.body(), "$.last")).isTrue();
        assertThat(contentIds(list(account, "?type=INCOME"))).hasSize(1);
        assertThat(contentIds(list(account, "?categoryId=" + account.expense()))).hasSize(3);
        assertThat(contentIds(list(account, "?startDate=2026-10-03"))).hasSize(1);
        assertThat(contentIds(list(account, "?endDate=2026-10-01"))).containsExactly(first);
        assertThat(contentIds(list(account, "?keyword=tea"))).hasSize(1);
        assertThat(JsonPath.<Integer>read(list(account, "").body(), "$.totalElements")).isEqualTo(4);
        assertThat(contentIds(list(account, "?page=20&size=1"))).isEmpty();
    }

    @Test
    void defaultPaginationIsBoundedAndTieBreakersAreDeterministic() throws Exception {
        var account = account();
        var expected = new ArrayList<Long>();
        for (int i = 0; i < 23; i++) {
            expected.add(id(create(account, account.expense(), "EXPENSE", "1.00", "Item", "2026-10-19")));
        }
        var first = list(account, "");
        assertThat(contentIds(first)).hasSize(20).isEqualTo(expected.reversed().subList(0, 20));
        assertThat(JsonPath.<Integer>read(first.body(), "$.totalElements")).isEqualTo(23);
        assertThat(contentIds(list(account, "?page=1"))).isEqualTo(expected.reversed().subList(20, 23));
        assertThat(contentIds(list(account, "?sort=id&direction=asc&size=100"))).isEqualTo(expected);
    }

    @Test
    void keywordTreatsSqlWildcardsAsLiteralCharacters() throws Exception {
        var account = account();
        long literal = id(create(account, account.expense(), "EXPENSE", "5.00", "100%_done\\today", "2026-10-19"));
        create(account, account.expense(), "EXPENSE", "6.00", "100percentXdone", "2026-10-19");
        assertThat(contentIds(list(account, "?keyword=%25_"))).containsExactly(literal);
        assertThat(contentIds(list(account, "?keyword=%5Ctoday"))).containsExactly(literal);
        assertThat(contentIds(list(account, "?keyword=%27%20OR%201%3D1"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1.00", "1.001", "100000000000000000.00"})
    void invalidAmountsAreRejectedWithoutRounding(String amount) throws Exception {
        var account = account();
        assertError(create(account, account.expense(), "EXPENSE", amount, null, "2026-10-19"), 400, "VALIDATION_FAILED");
        assertThat(JsonPath.<Integer>read(list(account, "").body(), "$.totalElements")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-amount", "missing-date", "missing-type", "missing-category", "bad-date", "bad-type", "long-description", "userId", "source", "id"})
    void invalidBodyAndOwnershipFieldsReturn400(String scenario) throws Exception {
        var account = account();
        var payload = payload(account.expense(), "EXPENSE", "1.00", "Valid", "2026-10-19");
        switch (scenario) {
            case "missing-amount" -> payload.remove("amount");
            case "missing-date" -> payload.remove("transactionDate");
            case "missing-type" -> payload.remove("type");
            case "missing-category" -> payload.remove("categoryId");
            case "bad-date" -> payload.put("transactionDate", "2026-02-30");
            case "bad-type" -> payload.put("type", "TRANSFER");
            case "long-description" -> payload.put("description", "x".repeat(256));
            case "userId" -> payload.put("userId", UUID.randomUUID().toString());
            case "source" -> payload.put("source", "MANUAL");
            case "id" -> payload.put("id", 42);
            default -> throw new IllegalStateException();
        }
        assertThat(request("POST", "/api/v1/transactions", payload, account.token()).statusCode()).isEqualTo(400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?size=0", "?size=101", "?page=abc", "?page=2147483647&size=100",
            "?type=BAD", "?categoryId=abc", "?categoryId=-1", "?startDate=not-a-date", "?endDate=2026-02-30",
            "?startDate=2026-10-20&endDate=2026-10-19", "?sort=userId", "?sort=category.name", "?direction=SIDEWAYS"})
    void invalidFiltersReturn400(String query) throws Exception {
        assertError(list(account(), query), 400, "INVALID_REQUEST");
    }

    @Test
    void excessiveKeywordReturns400() throws Exception {
        assertError(list(account(), "?keyword=" + "x".repeat(256)), 400, "INVALID_REQUEST");
    }

    @Test
    void rejectsTypeMismatchAndForeignOrMissingCategoriesOnCreateAndUpdate() throws Exception {
        var account = account();
        var other = account();
        assertError(create(account, account.expense(), "INCOME", "10", null, "2026-10-19"), 400, "INVALID_REQUEST");
        assertError(create(account, other.expense(), "EXPENSE", "10", null, "2026-10-19"), 404, "CATEGORY_NOT_FOUND");
        assertError(create(account, Long.MAX_VALUE, "EXPENSE", "10", null, "2026-10-19"), 404, "CATEGORY_NOT_FOUND");
        long id = id(create(account, account.expense(), "EXPENSE", "10", "Original", "2026-10-19"));
        assertError(request("PUT", "/api/v1/transactions/" + id,
                payload(other.expense(), "EXPENSE", "30", "Hijacked", "2026-10-19"), account.token()), 404, "CATEGORY_NOT_FOUND");
        assertError(request("PUT", "/api/v1/transactions/" + id,
                payload(account.expense(), "INCOME", "30", null, "2026-10-19"), account.token()), 400, "INVALID_REQUEST");
        assertThat(transactions.findById(id).orElseThrow().getDescription()).isEqualTo("Original");
        assertError(list(account, "?categoryId=" + other.expense()), 404, "CATEGORY_NOT_FOUND");
    }

    @Test
    void anotherUserCannotReadUpdateOrDeleteTransaction() throws Exception {
        var a = account();
        var b = account();
        long id = id(create(b, b.expense(), "EXPENSE", "10", "Private", "2026-10-19"));
        assertError(request("GET", "/api/v1/transactions/" + id, null, a.token()), 404, "TRANSACTION_NOT_FOUND");
        assertError(request("PUT", "/api/v1/transactions/" + id,
                payload(a.expense(), "EXPENSE", "20", null, "2026-10-19"), a.token()), 404, "TRANSACTION_NOT_FOUND");
        assertError(request("DELETE", "/api/v1/transactions/" + id, null, a.token()), 404, "TRANSACTION_NOT_FOUND");
        assertThat(contentIds(list(a, ""))).isEmpty();
        assertThat(transactions.findById(id).orElseThrow().getDescription()).isEqualTo("Private");
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "get", "create", "update", "delete"})
    void missingJwtReturns401(String operation) throws Exception {
        var method = switch (operation) { case "create" -> "POST"; case "update" -> "PUT"; case "delete" -> "DELETE"; default -> "GET"; };
        String path = "/api/v1/transactions" + (List.of("get", "update", "delete").contains(operation) ? "/1" : "");
        assertError(request(method, path, null, null), 401, "UNAUTHORIZED");
    }

    @Test
    void referencedCategoryCannotBeDeletedOrRetypedButCanBeRenamed() throws Exception {
        var account = account();
        long id = id(create(account, account.expense(), "EXPENSE", "10", null, "2026-10-19"));
        String path = "/api/v1/categories/" + account.expense();
        assertError(request("DELETE", path, null, account.token()), 409, "CATEGORY_IN_USE");
        assertError(request("PUT", path, Map.of("name", "Changed", "type", "INCOME"), account.token()), 409, "CATEGORY_IN_USE");
        assertThat(request("PUT", path, Map.of("name", "Renamed", "type", "EXPENSE", "color", "#ABC"), account.token()).statusCode()).isEqualTo(200);
        assertThat(request("DELETE", "/api/v1/transactions/" + id, null, account.token()).statusCode()).isEqualTo(204);
        assertThat(request("DELETE", path, null, account.token()).statusCode()).isEqualTo(204);
    }

    @Test
    void categoryDeletionRacingTransactionCreationKeepsReferencesValid() throws Exception {
        var account = account();
        var create = CompletableFuture.supplyAsync(() -> {
            try { return create(account, account.expense(), "EXPENSE", "10", null, "2026-10-19").statusCode(); }
            catch (Exception exception) { throw new CompletionException(exception); }
        });
        var delete = CompletableFuture.supplyAsync(() -> {
            try { return request("DELETE", "/api/v1/categories/" + account.expense(), null, account.token()).statusCode(); }
            catch (Exception exception) { throw new CompletionException(exception); }
        });
        int createStatus = create.get(20, TimeUnit.SECONDS);
        int deleteStatus = delete.get(20, TimeUnit.SECONDS);
        assertThat(List.of(createStatus, deleteStatus)).isIn(List.of(201, 409), List.of(404, 204));
    }

    private record Account(UUID id, String token, long income, long expense) {}
    private Account account() {
        var user = users.saveAndFlush(new User("Transaction User", UUID.randomUUID() + "@example.com", passwordHash));
        var income = categories.saveAndFlush(new Category(user, "Income Test", CategoryType.INCOME, null));
        var expense = categories.saveAndFlush(new Category(user, "Expense Test", CategoryType.EXPENSE, null));
        return new Account(user.getId(), tokens.issue(user.getId()), income.getId(), expense.getId());
    }

    private HttpResponse<String> create(Account account, long category, String type, String amount, String description, String date) throws Exception {
        return request("POST", "/api/v1/transactions", payload(category, type, amount, description, date), account.token());
    }
    private HashMap<String, Object> payload(long category, String type, String amount, String description, String date) {
        var body = new HashMap<String, Object>();
        body.put("categoryId", category); body.put("type", type); body.put("amount", new BigDecimal(amount));
        body.put("description", description); body.put("transactionDate", date);
        return body;
    }
    private HttpResponse<String> list(Account account, String query) throws Exception {
        return request("GET", "/api/v1/transactions" + query, null, account.token());
    }
    private HttpResponse<String> request(String method, String path, Object body, String token) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(20));
            if (token == null && !method.equals("GET")) core_api.CsrfTestSupport.protect(client, "http://localhost:" + port, builder);
            if (token != null) { builder.header("Authorization", "Bearer " + token); }
            if (body != null) {
                builder.header("Content-Type", "application/json").method(method,
                        HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            } else { builder.method(method, HttpRequest.BodyPublishers.noBody()); }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    private long id(HttpResponse<String> response) {
        assertThat(response.statusCode()).isIn(200, 201);
        return JsonPath.<Number>read(response.body(), "$.id").longValue();
    }
    private List<Long> contentIds(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        List<Number> ids = JsonPath.read(response.body(), "$.content[*].id");
        return ids.stream().map(Number::longValue).toList();
    }
    private void assertSafe(String body, Account account) {
        assertThat(body).doesNotContain("userId", "password", account.id().toString(), "\"user\"", "\"category\"");
    }
    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo(code);
        assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(status);
        assertThat(response.body()).doesNotContain("passwordHash", "stackTrace");
    }
}
