package core_api.integration;

import core_api.category.CategoryService;
import core_api.category.CategoryNotFoundException;
import core_api.transaction.TransactionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MockImportServiceTests {
    final JsonMapper json = JsonMapper.builder().build();
    @Test void rowFailuresAreCountedAfterRollbackAndDoNotStopOtherRows() {
        var client = mock(MockTransactionClient.class);
        var categories = mock(CategoryService.class);
        var transactions = mock(TransactionService.class);
        UUID owner = UUID.randomUUID();
        var row = json.readTree(MockTransactionClientTests.ROW);
        when(client.fetch()).thenReturn(List.of(row, row, row, row, json.readTree("{}")));
        when(transactions.importMock(eq(owner), any())).thenReturn(true).thenReturn(false)
                .thenThrow(new CategoryNotFoundException()).thenThrow(new DataIntegrityViolationException("private SQL"));
        var result = new MockImportService(client, categories, transactions).importFor(owner);
        assertThat(result).isEqualTo(new MockImportService.ImportResult(1, 1, 3, 5));
        verify(categories).list(owner, null);
    }
    @Test void upstreamFailureDoesNotInitializeCategoriesOrWriteTransactions() {
        var client = mock(MockTransactionClient.class);
        var categories = mock(CategoryService.class);
        var transactions = mock(TransactionService.class);
        when(client.fetch()).thenThrow(IntegrationException.unavailable());
        assertThatThrownBy(() -> new MockImportService(client, categories, transactions).importFor(UUID.randomUUID()))
                .isInstanceOf(IntegrationException.class);
        verifyNoInteractions(categories, transactions);
    }
    @ParameterizedTest @ValueSource(strings = {"0.00", "-1.00", "1.001", "1e3", "100000000000000000.00"})
    void invalidAmountsAreRejected(String amount) {
        assertThatThrownBy(() -> MockTransaction.decode(json.readTree(MockTransactionClientTests.ROW.replace("12345.67", amount))))
                .isInstanceOf(InvalidMockTransactionException.class);
    }
    @Test void invalidDateNumericAmountAndInjectedOwnershipAreRejected() {
        for (String row : List.of(MockTransactionClientTests.ROW.replace("2026-10-01", "2026-02-30"),
                MockTransactionClientTests.ROW.replace("\"12345.67\"", "12345.67"),
                MockTransactionClientTests.ROW.replace("\"source\":", "\"ownerId\":\"other\",\"source\":"),
                MockTransactionClientTests.ROW.replace("MOCK_PROVIDER", "MANUAL"))) {
            assertThatThrownBy(() -> MockTransaction.decode(json.readTree(row))).isInstanceOf(InvalidMockTransactionException.class);
        }
    }
}
