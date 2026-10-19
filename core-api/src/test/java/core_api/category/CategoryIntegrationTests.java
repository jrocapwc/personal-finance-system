package core_api.category;

import com.jayway.jsonpath.JsonPath;
import core_api.JwtTestSupport;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
class CategoryIntegrationTests extends JwtTestSupport {
    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private CategoryRepository categories;
    @Autowired private CategoryDefaultInitializationRepository initializations;
    @Autowired private TokenService tokens;
    @Autowired private ObjectMapper mapper;
    @Autowired private PasswordEncoder passwords;
    private String passwordHash;

    @BeforeAll
    void preparePasswordHash() {
        passwordHash = passwords.encode("Category-test-password!");
    }

    @Test
    void listsAllTenDefaultsAndFiltersTypesForExistingUser() throws Exception {
        var account = account();
        assertThat(initializations.existsById(account.id())).isFalse();
        var first = request("GET", "", null, account.token());
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(names(first)).containsExactlyInAnyOrder("Salary", "Freelance", "Investment Income", "Other Income",
                "Food", "Housing", "Transportation", "Entertainment", "Healthcare", "Other");
        assertThat(initializations.existsById(account.id())).isTrue();
        var income = request("GET", "?type=INCOME", null, account.token());
        assertThat(names(income)).containsExactly("Freelance", "Investment Income", "Other Income", "Salary");
        var expenses = request("GET", "?type=EXPENSE", null, account.token());
        assertThat(names(expenses)).hasSize(6);
        assertThat(JsonPath.<List<String>>read(expenses.body(), "$[*].type")).containsOnly("EXPENSE");
        assertThat(first.body()).doesNotContain("userId", "password", "normalizedName", account.id().toString());
    }

    @Test
    void repeatedRequestsDoNotDuplicateDefaults() throws Exception {
        var account = account();
        var first = request("GET", "", null, account.token());
        var second = request("GET", "", null, account.token());
        assertThat(ids(second)).isEqualTo(ids(first));
        assertThat(categories.findAllByUser_IdOrderByTypeAscNormalizedNameAscIdAsc(account.id())).hasSize(10);
    }

    @Test
    void concurrentFirstRequestsSeedExactlyOnce() throws Exception {
        var account = account();
        var futures = new ArrayList<CompletableFuture<HttpResponse<String>>>();
        for (int i = 0; i < 4; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try { return request("GET", "", null, account.token()); }
                catch (Exception exception) { throw new CompletionException(exception); }
            }));
        }
        for (var future : futures) {
            var response = future.get(20, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(names(response)).hasSize(10);
        }
        assertThat(categories.findAllByUser_IdOrderByTypeAscNormalizedNameAscIdAsc(account.id())).hasSize(10);
    }

    @Test
    void createsCategoryBeforeAnyListAndInitializesDefaults() throws Exception {
        var account = account();
        var response = request("POST", "", payload("  Professional   Education  ", "EXPENSE", "#abc"), account.token());
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(response.body(), "$.name")).isEqualTo("Professional Education");
        assertThat(JsonPath.<String>read(response.body(), "$.color")).isEqualTo("#ABC");
        assertThat(JsonPath.<String>read(response.body(), "$.createdAt")).isNotBlank();
        assertThat(JsonPath.<String>read(response.body(), "$.updatedAt")).isNotBlank();
        assertThat(JsonPath.<Number>read(response.body(), "$.id").longValue()).isPositive();
        assertThat(names(request("GET", "", null, account.token()))).hasSize(11);
    }

    @Test
    void updatesCategoryAndClearsColorWithoutChangingCreationTime() throws Exception {
        var account = account();
        var created = request("POST", "", payload("Education", "EXPENSE", "#123456"), account.token());
        long id = id(created);
        var before = categories.findByIdAndUser_Id(id, account.id()).orElseThrow();
        var updated = request("PUT", "/" + id, Map.of("name", "Courses", "type", "INCOME"), account.token());
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(id(updated)).isEqualTo(id);
        assertThat(JsonPath.<String>read(updated.body(), "$.name")).isEqualTo("Courses");
        assertThat(JsonPath.<String>read(updated.body(), "$.type")).isEqualTo("INCOME");
        assertThat(JsonPath.<Object>read(updated.body(), "$.color")).isNull();
        var after = categories.findByIdAndUser_Id(id, account.id()).orElseThrow();
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThat(after.getUpdatedAt()).isAfter(before.getUpdatedAt());
    }

    @Test
    void updatingSameCategoryWithSameNormalizedNameIsAllowed() throws Exception {
        var account = account();
        var created = request("POST", "", Map.of("name", "Education", "type", "EXPENSE"), account.token());
        var updated = request("PUT", "/" + id(created), Map.of("name", " EDUCATION ", "type", "EXPENSE"), account.token());
        assertThat(updated.statusCode()).isEqualTo(200);
    }

    @Test
    void deletesCategoryAndReturns404OnSubsequentMutation() throws Exception {
        var account = account();
        long id = id(request("POST", "", Map.of("name", "Education", "type", "EXPENSE"), account.token()));
        var deleted = request("DELETE", "/" + id, null, account.token());
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(deleted.body()).isEmpty();
        assertThat(categories.findById(id)).isEmpty();
        assertError(request("DELETE", "/" + id, null, account.token()), 404, "CATEGORY_NOT_FOUND");
        assertError(request("PUT", "/" + id, Map.of("name", "Courses", "type", "INCOME"), account.token()),
                404, "CATEGORY_NOT_FOUND");
    }

    @Test
    void deletedAndRenamedDefaultsAreNotRecreated() throws Exception {
        var account = account();
        var defaults = request("GET", "", null, account.token());
        long food = defaultId(defaults, "Food");
        long salary = defaultId(defaults, "Salary");
        assertThat(request("DELETE", "/" + food, null, account.token()).statusCode()).isEqualTo(204);
        assertThat(request("PUT", "/" + salary, Map.of("name", "Wages", "type", "INCOME"), account.token()).statusCode()).isEqualTo(200);
        for (int i = 0; i < 2; i++) {
            var listed = request("GET", "", null, account.token());
            assertThat(names(listed)).hasSize(9).contains("Wages").doesNotContain("Salary", "Food");
        }
    }

    @Test
    void duplicateCreationAndUpdateReturnConflict() throws Exception {
        var account = account();
        assertThat(request("POST", "", Map.of("name", "Side   Project", "type", "INCOME"), account.token()).statusCode()).isEqualTo(201);
        assertError(request("POST", "", Map.of("name", " side project ", "type", "INCOME"), account.token()), 409, "DUPLICATE_CATEGORY");
        assertError(request("POST", "", Map.of("name", " food ", "type", "EXPENSE"), account.token()), 409, "DUPLICATE_CATEGORY");
        long id = id(request("POST", "", Map.of("name", "Education", "type", "EXPENSE"), account.token()));
        assertError(request("PUT", "/" + id, Map.of("name", " FOOD ", "type", "EXPENSE"), account.token()), 409, "DUPLICATE_CATEGORY");
        assertThat(categories.findById(id).orElseThrow().getName()).isEqualTo("Education");
    }

    @Test
    void sameNameIsAllowedForDifferentTypeOrUser() throws Exception {
        var a = account();
        var b = account();
        assertThat(request("POST", "", Map.of("name", "Education", "type", "EXPENSE"), a.token()).statusCode()).isEqualTo(201);
        assertThat(request("POST", "", Map.of("name", "education", "type", "INCOME"), a.token()).statusCode()).isEqualTo(201);
        assertThat(request("POST", "", Map.of("name", "EDUCATION", "type", "EXPENSE"), b.token()).statusCode()).isEqualTo(201);
    }

    @Test
    void foreignUserCannotListModifyOrDeleteAnotherUsersCategory() throws Exception {
        var a = account();
        var b = account();
        long id = id(request("POST", "", Map.of("name", "Private Category", "type", "EXPENSE"), b.token()));
        assertThat(ids(request("GET", "", null, a.token()))).doesNotContain(id);
        var payload = Map.of("name", "Stolen", "type", "INCOME");
        assertError(request("PUT", "/" + id, payload, a.token()), 404, "CATEGORY_NOT_FOUND");
        assertError(request("DELETE", "/" + id, null, a.token()), 404, "CATEGORY_NOT_FOUND");
        assertThat(categories.findByIdAndUser_Id(id, b.id()).orElseThrow().getName()).isEqualTo("Private Category");
    }

    @ParameterizedTest
    @ValueSource(strings = {"blank", "long", "missing-name", "missing-type", "bad-type", "bad-color", "empty-color", "userId", "id"})
    void invalidInputAndOwnershipInjectionAreRejected(String scenario) throws Exception {
        var account = account();
        var payload = new HashMap<String, Object>(payload("Education", "EXPENSE", "#123ABC"));
        switch (scenario) {
            case "blank" -> payload.put("name", " \t\n ");
            case "long" -> payload.put("name", "x".repeat(101));
            case "missing-name" -> payload.remove("name");
            case "missing-type" -> payload.remove("type");
            case "bad-type" -> payload.put("type", "INVALID");
            case "bad-color" -> payload.put("color", "red");
            case "empty-color" -> payload.put("color", "");
            case "userId" -> payload.put("userId", UUID.randomUUID().toString());
            case "id" -> payload.put("id", 123);
            default -> throw new IllegalStateException();
        }
        assertThat(request("POST", "", payload, account.token()).statusCode()).isEqualTo(400);
        assertThat(categories.findAllByUser_IdOrderByTypeAscNormalizedNameAscIdAsc(account.id())).isEmpty();
    }

    @Test
    void invalidUpdateAndFilterAndPathReturn400() throws Exception {
        var account = account();
        long id = id(request("POST", "", Map.of("name", "Education", "type", "EXPENSE"), account.token()));
        assertError(request("PUT", "/" + id, Map.of("name", " ", "type", "INCOME"), account.token()), 400, "VALIDATION_FAILED");
        assertError(request("GET", "?type=invalid", null, account.token()), 400, "INVALID_REQUEST");
        assertError(request("DELETE", "/not-a-number", null, account.token()), 400, "INVALID_REQUEST");
        assertThat(categories.findById(id).orElseThrow().getName()).isEqualTo("Education");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE"})
    void missingJwtIsRejectedForEveryMethod(String method) throws Exception {
        String path = method.equals("PUT") || method.equals("DELETE") ? "/1" : "";
        Object body = method.equals("POST") || method.equals("PUT") ? Map.of("name", "Education", "type", "EXPENSE") : null;
        assertError(request(method, path, body, null), 401, "UNAUTHORIZED");
    }

    @Test
    void concurrentDuplicateCreatesProduceOneCreatedAndOneConflict() throws Exception {
        var account = account();
        request("GET", "", null, account.token());
        var futures = new ArrayList<CompletableFuture<Integer>>();
        for (int i = 0; i < 2; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try { return request("POST", "", Map.of("name", "Education", "type", "EXPENSE"), account.token()).statusCode(); }
                catch (Exception exception) { throw new CompletionException(exception); }
            }));
        }
        assertThat(List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(201, 409);
    }

    private record Account(UUID id, String token) {}
    private Account account() {
        var user = users.saveAndFlush(new User("Category User", UUID.randomUUID() + "@example.com", passwordHash));
        return new Account(user.getId(), tokens.issue(user.getId()));
    }

    private HttpResponse<String> request(String method, String suffix, Object body, String token) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/categories" + suffix))
                    .timeout(Duration.ofSeconds(20));
            if (token != null) { builder.header("Authorization", "Bearer " + token); }
            if (body != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            } else { builder.method(method, HttpRequest.BodyPublishers.noBody()); }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private Map<String, Object> payload(String name, String type, String color) {
        return Map.of("name", name, "type", type, "color", color);
    }
    private List<String> names(HttpResponse<String> response) { return JsonPath.read(response.body(), "$[*].name"); }
    private List<Long> ids(HttpResponse<String> response) {
        List<Number> values = JsonPath.read(response.body(), "$[*].id");
        return values.stream().map(Number::longValue).toList();
    }
    private long id(HttpResponse<String> response) { return JsonPath.<Number>read(response.body(), "$.id").longValue(); }
    private long defaultId(HttpResponse<String> response, String name) {
        List<Number> values = JsonPath.read(response.body(), "$[?(@.name == '" + name + "')].id");
        return values.getFirst().longValue();
    }
    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo(code);
        assertThat(response.body()).doesNotContain("passwordHash", "stackTrace");
    }
}
