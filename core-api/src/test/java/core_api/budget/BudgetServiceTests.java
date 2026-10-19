package core_api.budget;

import core_api.category.Category;
import core_api.category.CategoryNotFoundException;
import core_api.category.CategoryRepository;
import core_api.category.CategoryType;
import core_api.transaction.TransactionRepository;
import core_api.user.User;
import core_api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTests {
    @Mock private BudgetRepository budgets;
    @Mock private CategoryRepository categories;
    @Mock private TransactionRepository transactions;
    @Mock private UserRepository users;
    @InjectMocks private BudgetService service;

    @Test
    void foreignBudgetCannotBeReadOrUpdated() {
        UUID owner = UUID.randomUUID();
        assertThatThrownBy(() -> service.get(owner, 42L)).isInstanceOf(BudgetNotFoundException.class);
        when(users.findByIdForCategoryUpdate(owner)).thenReturn(Optional.of(new User("User", "u@example.com", "hash")));
        assertThatThrownBy(() -> service.update(owner, 42L, request())).isInstanceOf(BudgetNotFoundException.class);
        verify(budgets, never()).saveAndFlush(any());
        verify(categories, never()).findByIdAndUser_Id(any(), any());
    }

    @Test
    void foreignCategoryCannotBeSaved() {
        UUID owner = UUID.randomUUID();
        when(users.findByIdForCategoryUpdate(owner)).thenReturn(Optional.of(new User("User", "u@example.com", "hash")));
        assertThatThrownBy(() -> service.create(owner, request())).isInstanceOf(CategoryNotFoundException.class);
        verify(categories).findByIdAndUser_Id(1L, owner);
        verify(budgets, never()).saveAndFlush(any());
    }

    @Test
    void incomeCategoryCannotBeSaved() {
        UUID owner = UUID.randomUUID();
        var user = new User("User", "u@example.com", "hash");
        when(users.findByIdForCategoryUpdate(owner)).thenReturn(Optional.of(user));
        when(categories.findByIdAndUser_Id(1L, owner)).thenReturn(Optional.of(new Category(user, "Income", CategoryType.INCOME, null)));
        assertThatThrownBy(() -> service.create(owner, request())).isInstanceOf(InvalidBudgetRequestException.class);
        verify(budgets, never()).saveAndFlush(any());
    }

    @Test
    void monthlyListUsesOneGroupedSumInsteadOfAnAggregatePerBudget() {
        UUID owner = UUID.randomUUID();
        var category = org.mockito.Mockito.mock(Category.class);
        when(category.getId()).thenReturn(1L);
        var user = new User("User", "u@example.com", "hash");
        var budget = new Budget(user, category, request());
        when(budgets.findAllByUser_IdAndMonthAndYearOrderByIdAsc(owner, 10, 2026)).thenReturn(List.of(budget));
        when(transactions.sumExpensesByCategory(owner, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1))).thenReturn(List.of());
        assertThat(service.list(owner, 10, 2026)).hasSize(1).allSatisfy(response -> assertThat(response.spentAmount()).isZero());
        verify(transactions).sumExpensesByCategory(owner, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1));
        verify(transactions, never()).sumExpenseForCategory(any(), any(), any(), any());
    }

    private BudgetRequest request() { return new BudgetRequest(1L, 10, 2026, new BigDecimal("100.00"), 80); }
}
