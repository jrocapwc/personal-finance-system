package core_api.budget;

import core_api.category.Category;
import core_api.category.CategoryNotFoundException;
import core_api.category.CategoryRepository;
import core_api.category.CategoryType;
import core_api.transaction.TransactionRepository;
import core_api.user.User;
import core_api.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BudgetService {
    private final BudgetRepository budgets;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final UserRepository users;

    public BudgetService(BudgetRepository budgets, CategoryRepository categories,
                         TransactionRepository transactions, UserRepository users) {
        this.budgets = budgets; this.categories = categories;
        this.transactions = transactions; this.users = users;
    }

    @Transactional
    public BudgetResponse create(UUID userId, BudgetRequest request) {
        var user = lockUser(userId);
        var category = expenseCategory(userId, request.categoryId());
        if (budgets.existsByUser_IdAndCategory_IdAndMonthAndYear(userId, request.categoryId(), request.month(), request.year())) {
            throw new DuplicateBudgetException();
        }
        return response(userId, save(new Budget(user, category, request)));
    }

    public List<BudgetResponse> list(UUID userId, int month, int year) {
        var period = new BudgetPeriod(month, year);
        var result = budgets.findAllByUser_IdAndMonthAndYearOrderByIdAsc(userId, month, year);
        if (result.isEmpty()) { return List.of(); }
        var spending = spending(userId, period);
        return result.stream().map(budget -> BudgetResponse.from(budget,
                spending.getOrDefault(budget.getCategoryId(), BigDecimal.ZERO))).toList();
    }

    public BudgetResponse get(UUID userId, Long id) { return response(userId, owned(userId, id)); }

    @Transactional
    public BudgetResponse update(UUID userId, Long id, BudgetRequest request) {
        lockUser(userId);
        var budget = owned(userId, id);
        var category = expenseCategory(userId, request.categoryId());
        if (budgets.existsByUser_IdAndCategory_IdAndMonthAndYearAndIdNot(
                userId, request.categoryId(), request.month(), request.year(), id)) {
            throw new DuplicateBudgetException();
        }
        budget.update(category, request);
        return response(userId, save(budget));
    }

    @Transactional
    public void delete(UUID userId, Long id) {
        lockUser(userId);
        budgets.delete(owned(userId, id));
        budgets.flush();
    }

    private Budget owned(UUID userId, Long id) {
        return budgets.findByIdAndUser_Id(id, userId).orElseThrow(BudgetNotFoundException::new);
    }

    private BudgetResponse response(UUID userId, Budget budget) {
        var period = new BudgetPeriod(budget.getMonth(), budget.getYear());
        var spent = transactions.sumExpenseForCategory(userId, budget.getCategoryId(), period.start(), period.endExclusive());
        return BudgetResponse.from(budget, spent);
    }

    private Map<Long, BigDecimal> spending(UUID userId, BudgetPeriod period) {
        return transactions.sumExpensesByCategory(userId, period.start(), period.endExclusive()).stream()
                .collect(Collectors.toMap(TransactionRepository.ExpenseTotal::getCategoryId,
                        TransactionRepository.ExpenseTotal::getSpentAmount));
    }

    private Category expenseCategory(UUID userId, Long id) {
        var category = categories.findByIdAndUser_Id(id, userId).orElseThrow(CategoryNotFoundException::new);
        if (category.getType() != CategoryType.EXPENSE) {
            throw new InvalidBudgetRequestException("Budgets require an EXPENSE category");
        }
        return category;
    }

    private User lockUser(UUID userId) {
        return users.findByIdForCategoryUpdate(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication"));
    }

    private Budget save(Budget budget) {
        try { return budgets.saveAndFlush(budget); }
        catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && violation.getConstraintName() != null
                        && violation.getConstraintName().toLowerCase(Locale.ROOT).contains("uk_budgets_user_category_period")) {
                    throw new DuplicateBudgetException();
                }
            }
            throw exception;
        }
    }
}
