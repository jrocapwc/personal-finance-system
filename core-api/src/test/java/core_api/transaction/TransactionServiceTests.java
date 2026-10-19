package core_api.transaction;

import core_api.category.Category;
import core_api.category.CategoryNotFoundException;
import core_api.category.CategoryRepository;
import core_api.category.CategoryType;
import core_api.user.User;
import core_api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTests {
    @Mock private TransactionRepository transactions;
    @Mock private CategoryRepository categories;
    @Mock private UserRepository users;
    @InjectMocks private TransactionService service;

    @Test
    void unavailableTransactionUsesOwnerScopedLookupAndCannotBeSaved() {
        UUID owner = UUID.randomUUID();
        when(users.findByIdForCategoryUpdate(owner)).thenReturn(Optional.of(new User("User", "u@example.com", "hash")));
        assertThatThrownBy(() -> service.update(owner, 42L, request())).isInstanceOf(TransactionNotFoundException.class);
        verify(transactions).findByIdAndUser_Id(42L, owner);
        verify(transactions, never()).saveAndFlush(any());
        verify(categories, never()).findByIdAndUser_Id(any(), any());
    }

    @Test
    void foreignCategoryCannotBeUsedForCreation() {
        UUID owner = UUID.randomUUID();
        when(users.findByIdForCategoryUpdate(owner)).thenReturn(Optional.of(new User("User", "u@example.com", "hash")));
        assertThatThrownBy(() -> service.create(owner, request())).isInstanceOf(CategoryNotFoundException.class);
        verify(categories).findByIdAndUser_Id(1L, owner);
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void mismatchedTypeCannotBeSaved() {
        UUID owner = UUID.randomUUID();
        var user = new User("User", "u@example.com", "hash");
        when(users.findByIdForCategoryUpdate(owner)).thenReturn(Optional.of(user));
        when(categories.findByIdAndUser_Id(1L, owner))
                .thenReturn(Optional.of(new Category(user, "Expense", CategoryType.EXPENSE, null)));
        assertThatThrownBy(() -> service.create(owner, request())).isInstanceOf(InvalidTransactionRequestException.class);
        verify(transactions, never()).saveAndFlush(any());
    }

    private TransactionRequest request() {
        return new TransactionRequest(1L, CategoryType.INCOME, new BigDecimal("10.00"), null, LocalDate.of(2026, 10, 19));
    }
}
