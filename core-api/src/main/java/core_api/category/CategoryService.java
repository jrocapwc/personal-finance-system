package core_api.category;

import core_api.user.User;
import core_api.transaction.TransactionRepository;
import core_api.budget.BudgetRepository;
import core_api.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class CategoryService {
    private static final List<String> INCOME_DEFAULTS = List.of(
            "Salary", "Freelance", "Investment Income", "Other Income");
    private static final List<String> EXPENSE_DEFAULTS = List.of(
            "Food", "Housing", "Transportation", "Entertainment", "Healthcare", "Other");
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final BudgetRepository budgets;
    private final UserRepository users;
    private final CategoryDefaultInitializationRepository initializations;

    public CategoryService(CategoryRepository categories, UserRepository users,
                           CategoryDefaultInitializationRepository initializations, TransactionRepository transactions, BudgetRepository budgets) {
        this.categories = categories;
        this.transactions = transactions;
        this.budgets = budgets;
        this.users = users;
        this.initializations = initializations;
    }

    public List<CategoryResponse> list(UUID userId, CategoryType type) {
        initializeDefaults(lockUser(userId));
        var result = type == null ? categories.findAllByUser_IdOrderByTypeAscNormalizedNameAscIdAsc(userId)
                : categories.findAllByUser_IdAndTypeOrderByNormalizedNameAscIdAsc(userId, type);
        return result.stream().map(CategoryResponse::from).toList();
    }

    public CategoryResponse create(UUID userId, CategoryRequest request) {
        var user = lockUser(userId);
        initializeDefaults(user);
        if (categories.existsByUser_IdAndTypeAndNormalizedName(userId, request.type(), Category.normalizeName(request.name()))) {
            throw new DuplicateCategoryException();
        }
        return save(new Category(user, request.name(), request.type(), request.color()));
    }

    public CategoryResponse update(UUID userId, Long id, CategoryRequest request) {
        lockUser(userId);
        var category = categories.findByIdAndUser_Id(id, userId).orElseThrow(CategoryNotFoundException::new);
        if (categories.existsByUser_IdAndTypeAndNormalizedNameAndIdNot(
                userId, request.type(), Category.normalizeName(request.name()), id)) {
            throw new DuplicateCategoryException();
        }
        if (category.getType() != request.type() && (transactions.existsByCategory_Id(id) || budgets.existsByCategory_Id(id))) {
            throw new CategoryInUseException();
        }
        category.update(request.name(), request.type(), request.color());
        return save(category);
    }

    public void delete(UUID userId, Long id) {
        lockUser(userId);
        var category = categories.findByIdAndUser_Id(id, userId).orElseThrow(CategoryNotFoundException::new);
        if (transactions.existsByCategory_Id(id) || budgets.existsByCategory_Id(id)) {
            throw new CategoryInUseException();
        }
        categories.delete(category);
        categories.flush();
    }

    private User lockUser(UUID userId) {
        // Serialize initialization and writes per owner, including across multiple API instances.
        return users.findByIdForCategoryUpdate(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication"));
    }

    private void initializeDefaults(User user) {
        if (initializations.existsById(user.getId())) {
            return;
        }
        addDefaults(user, CategoryType.INCOME, INCOME_DEFAULTS);
        addDefaults(user, CategoryType.EXPENSE, EXPENSE_DEFAULTS);
        initializations.saveAndFlush(new CategoryDefaultInitialization(user.getId()));
    }

    private void addDefaults(User user, CategoryType type, List<String> names) {
        for (String name : names) {
            if (!categories.existsByUser_IdAndTypeAndNormalizedName(user.getId(), type, Category.normalizeName(name))) {
                categories.save(new Category(user, name, type, null));
            }
        }
    }

    private CategoryResponse save(Category category) {
        try {
            return CategoryResponse.from(categories.saveAndFlush(category));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && violation.getConstraintName() != null
                        && violation.getConstraintName().toLowerCase(Locale.ROOT).contains("uk_categories_user_type_name")) {
                    throw new DuplicateCategoryException();
                }
            }
            throw exception;
        }
    }
}
