package core_api.transaction;

import core_api.category.Category;
import core_api.category.CategoryNotFoundException;
import core_api.category.CategoryRepository;
import core_api.category.CategoryType;
import core_api.user.User;
import core_api.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import core_api.integration.MockTransaction;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TransactionService {
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final UserRepository users;

    public TransactionService(TransactionRepository transactions, CategoryRepository categories, UserRepository users) {
        this.transactions = transactions;
        this.categories = categories;
        this.users = users;
    }

    @Transactional
    public TransactionResponse create(UUID userId, TransactionRequest request) {
        var user = lockUser(userId);
        var category = availableCategory(userId, request.categoryId(), request.type());
        return TransactionResponse.from(transactions.saveAndFlush(new Transaction(user, category, request)));
    }

    /** One committed row per call; same owner lock and category validation as manual writes. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean importMock(UUID userId, MockTransaction row) {
        var user = lockUser(userId);
        if (transactions.existsByUser_IdAndSourceAndExternalTransactionId(userId, TransactionSource.MOCK_PROVIDER, row.externalTransactionId())) {
            return false;
        }
        String name = switch (row.categoryHint()) {
            case "SALARY" -> "Salary";
            case "FREELANCE" -> "Freelance";
            case "FOOD" -> "Food";
            case "TRANSPORTATION" -> "Transportation";
            case "HOUSING" -> "Housing";
            default -> throw new InvalidTransactionRequestException("Unknown mock category hint");
        };
        var expectedType = row.categoryHint().equals("SALARY") || row.categoryHint().equals("FREELANCE")
                ? CategoryType.INCOME : CategoryType.EXPENSE;
        if (row.type() != expectedType) throw new InvalidTransactionRequestException("Mock category hint does not match transaction type");
        var mapped = categories.findByUser_IdAndTypeAndNormalizedName(userId, row.type(), Category.normalizeName(name))
                .orElseThrow(CategoryNotFoundException::new);
        var category = availableCategory(userId, mapped.getId(), row.type());
        var request = new TransactionRequest(category.getId(), row.type(), row.amount(), row.description(), row.transactionDate());
        transactions.saveAndFlush(Transaction.imported(user, category, request, row.externalTransactionId()));
        return true;
    }

    public TransactionResponse get(UUID userId, Long id) {
        return TransactionResponse.from(ownedTransaction(userId, id));
    }

    public TransactionPageResponse list(UUID userId, TransactionQuery query) {
        if (query.categoryId() != null) {
            categories.findByIdAndUser_Id(query.categoryId(), userId).orElseThrow(CategoryNotFoundException::new);
        }
        return TransactionPageResponse.from(transactions.findAll(
                TransactionSpecifications.ownedAndMatching(userId, query), query.pageable()), query);
    }

    @Transactional
    public TransactionResponse update(UUID userId, Long id, TransactionRequest request) {
        lockUser(userId);
        var transaction = ownedTransaction(userId, id);
        var category = availableCategory(userId, request.categoryId(), request.type());
        transaction.update(category, request);
        return TransactionResponse.from(transactions.saveAndFlush(transaction));
    }

    @Transactional
    public void delete(UUID userId, Long id) {
        lockUser(userId);
        transactions.delete(ownedTransaction(userId, id));
        transactions.flush();
    }

    private Transaction ownedTransaction(UUID userId, Long id) {
        return transactions.findByIdAndUser_Id(id, userId).orElseThrow(TransactionNotFoundException::new);
    }

    private Category availableCategory(UUID userId, Long id, CategoryType type) {
        var category = categories.findByIdAndUser_Id(id, userId).orElseThrow(CategoryNotFoundException::new);
        if (category.getType() != type) {
            throw new InvalidTransactionRequestException("Category type must match transaction type");
        }
        return category;
    }

    private User lockUser(UUID userId) {
        return users.findByIdForCategoryUpdate(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication"));
    }
}
