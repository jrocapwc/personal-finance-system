package core_api.integration;

import core_api.JwtTestSupport;
import core_api.category.*;
import core_api.security.TokenService;
import core_api.transaction.*;
import core_api.user.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MockImportIntegrationTests extends JwtTestSupport {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired CategoryService categoryService;
    @Autowired TransactionService transactions;
    @Autowired TransactionRepository repository;
    @Autowired MockImportService imports;
    @Autowired TokenService tokens;
    @MockitoBean MockTransactionClient provider;
    final JsonMapper json = JsonMapper.builder().build();
    JsonNode income() { return json.readTree(MockTransactionClientTests.ROW); }
    JsonNode expense() { return json.readTree(MockTransactionClientTests.ROW.replace("fictional-1", "fictional-2")
            .replace("INCOME", "EXPENSE").replace("SALARY", "FOOD").replace("12345.67", "125.50")); }
    @BeforeEach void setup() { when(provider.fetch()).thenReturn(List.of(income(), expense())); }

    @Test void importsOwnedCategoriesAndSkipsDuplicatesWithoutChangingManualTransactions() throws Exception {
        var user = account();
        var food = categoryService.list(user.getId(), CategoryType.EXPENSE).stream().filter(c -> c.name().equals("Food")).findFirst().orElseThrow();
        var manual = transactions.create(user.getId(), new TransactionRequest(food.id(), CategoryType.EXPENSE,
                new BigDecimal("77.25"), "Manual preserved", LocalDate.of(2026, 10, 2)));
        var originalManual = transactions.get(user.getId(), manual.id());
        assertResult(post(user, "", null), 2, 0, 0, 2);
        assertResult(post(user, "", null), 0, 2, 0, 2);
        assertThat(transactions.get(user.getId(), manual.id())).isEqualTo(originalManual);
        var saved = owned(user);
        assertThat(saved).hasSize(3);
        var importedFood = saved.stream().filter(row -> row.source() == TransactionSource.MOCK_PROVIDER && row.type() == CategoryType.EXPENSE).findFirst().orElseThrow();
        assertThat(importedFood.categoryId()).isEqualTo(food.id());
        assertThat(importedFood.amount()).isEqualByComparingTo("125.50");
        var salary = saved.stream().filter(row -> row.source() == TransactionSource.MOCK_PROVIDER && row.type() == CategoryType.INCOME).findFirst().orElseThrow();
        assertThat(categories.findByIdAndUser_Id(salary.categoryId(), user.getId()).orElseThrow().getName()).isEqualTo("Salary");
        assertThat(repository.existsByUser_IdAndSourceAndExternalTransactionId(user.getId(), TransactionSource.MOCK_PROVIDER, "fictional-1")).isTrue();
    }
    @Test void sameExternalIdsAreIndependentAcrossOwnersAndCrudRemainsIsolated() throws Exception {
        var a = account(); var b = account();
        assertResult(post(a, "", null), 2, 0, 0, 2);
        assertThat(owned(b)).isEmpty();
        assertResult(post(b, "", null), 2, 0, 0, 2);
        var aRows = owned(a); var bRows = owned(b);
        assertThat(aRows).hasSize(2); assertThat(bRows).hasSize(2);
        assertThat(aRows).extracting(TransactionResponse::categoryId).doesNotContainAnyElementsOf(bRows.stream().map(TransactionResponse::categoryId).toList());
        assertThatThrownBy(() -> transactions.get(a.getId(), bRows.getFirst().id())).isInstanceOf(TransactionNotFoundException.class);
        assertThatThrownBy(() -> transactions.delete(a.getId(), bRows.getFirst().id())).isInstanceOf(TransactionNotFoundException.class);
    }
    @Test void invalidUnknownHintAndWrongTypeAreFailedAndDuplicateWithinBatchIsSkipped() throws Exception {
        var a = account();
        when(provider.fetch()).thenReturn(List.of(income(), income(), expense().deepCopy(),
                json.readTree(MockTransactionClientTests.ROW.replace("fictional-1", "unknown-hint").replace("SALARY", "UNKNOWN")),
                json.readTree(MockTransactionClientTests.ROW.replace("fictional-1", "wrong-type").replace("INCOME", "EXPENSE")),
                json.readTree(MockTransactionClientTests.ROW.replace("fictional-1", "zero").replace("12345.67", "0.00"))));
        assertResult(post(a, "", null), 2, 1, 3, 6);
        assertThat(owned(a)).hasSize(2);
    }
    @Test void deletedCategoryIsNotRecreatedOrBorrowedFromAnotherOwner() throws Exception {
        var a = account(); var b = account();
        categoryService.list(b.getId(), null);
        var salary = categoryService.list(a.getId(), CategoryType.INCOME).stream().filter(c -> c.name().equals("Salary")).findFirst().orElseThrow();
        categoryService.delete(a.getId(), salary.id());
        assertResult(post(a, "", null), 1, 0, 1, 2);
        assertThat(owned(a)).hasSize(1);
    }
    @Test void concurrentImportsCommitEachOwnerExternalIdOnce() throws Exception {
        var a = account();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> imports.importFor(a.getId()));
            var second = executor.submit(() -> imports.importFor(a.getId()));
            var one = first.get(); var two = second.get();
            assertThat(one.importedCount() + two.importedCount()).isEqualTo(2);
            assertThat(one.skippedCount() + two.skippedCount()).isEqualTo(2);
            assertThat(one.failedCount() + two.failedCount()).isZero();
        }
        assertThat(owned(a)).hasSize(2);
    }
    @Test void requiresJwtAndRejectsOwnershipInputs() throws Exception {
        assertThat(request(null, "", null).statusCode()).isEqualTo(401);
        assertThat(request("invalid-jwt", "", null).statusCode()).isEqualTo(401);
        verifyNoInteractions(provider);
        var a = account();
        assertThat(post(a, "?userId=" + UUID.randomUUID(), null).statusCode()).isEqualTo(400);
        assertThat(post(a, "", "{\"userId\":\"other\"}").statusCode()).isEqualTo(400);
        verifyNoInteractions(provider);
    }
    @Test void unavailableTimeoutAndInternalAuthenticationHaveControlledErrors() throws Exception {
        var a = account();
        for (var error : List.of(IntegrationException.unavailable(),
                new IntegrationException(504, "INTEGRATION_TIMEOUT", "Mock integration service timed out"),
                new IntegrationException(502, "INTEGRATION_AUTH_FAILED", "Mock integration service authentication failed"))) {
            doThrow(error).when(provider).fetch();
            var response = post(a, "", null);
            assertThat(response.statusCode()).isEqualTo(error.status());
            assertThat(json.readTree(response.body()).path("code").asString()).isEqualTo(error.code());
            assertThat(response.body()).doesNotContain("stackTrace", "password", MockTransactionClientTests.KEY);
        }
        assertThat(owned(a)).isEmpty();
    }
    private List<TransactionResponse> owned(User owner) {
        return transactions.list(owner.getId(), new TransactionQuery(null, null, null, null, null, 0, 100, "id", "ASC")).content();
    }
    private User account() { return users.saveAndFlush(new User("Import test", UUID.randomUUID() + "@example.com", "test-hash")); }
    private HttpResponse<String> post(User user, String query, String body) throws Exception { return request(tokens.issue(user.getId()), query, body); }
    private HttpResponse<String> request(String token, String query, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/transactions/import/mock" + query));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        try (var http = HttpClient.newHttpClient()) {
            return http.send(request.POST(body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    private void assertResult(HttpResponse<String> response, int imported, int skipped, int failed, int total) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body())).isEqualTo(json.readTree("{\"importedCount\":" + imported + ",\"skippedCount\":" + skipped +
                ",\"failedCount\":" + failed + ",\"totalProcessed\":" + total + "}"));
    }
}
