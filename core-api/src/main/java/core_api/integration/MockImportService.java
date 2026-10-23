package core_api.integration;

import core_api.category.CategoryService;
import core_api.category.CategoryNotFoundException;
import core_api.transaction.InvalidTransactionRequestException;
import core_api.transaction.TransactionService;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class MockImportService {
    private final MockTransactionClient client;
    private final CategoryService categories;
    private final TransactionService transactions;
    public MockImportService(MockTransactionClient client, CategoryService categories, TransactionService transactions) {
        this.client = client; this.categories = categories; this.transactions = transactions;
    }
    public ImportResult importFor(UUID owner) {
        // No database transaction or owner lock spans the external network request.
        var rows = client.fetch();
        categories.list(owner, null); // Existing one-time default-category initialization.
        int imported = 0, skipped = 0, failed = 0;
        for (var row : rows) {
            try {
                var transaction = MockTransaction.decode(row);
                if (transactions.importMock(owner, transaction)) imported++; else skipped++;
            } catch (InvalidMockTransactionException | InvalidTransactionRequestException | CategoryNotFoundException exception) {
                failed++;
            } catch (DataAccessException exception) {
                // A per-row transaction has rolled back. Never expose SQL, payloads, or constraint text.
                failed++;
            }
        }
        return new ImportResult(imported, skipped, failed, rows.size());
    }
    public record ImportResult(int importedCount, int skippedCount, int failedCount, int totalProcessed) {}
}
