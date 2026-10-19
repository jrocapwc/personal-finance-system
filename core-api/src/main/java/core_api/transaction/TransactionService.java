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
