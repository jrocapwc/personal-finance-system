package core_api.category;

import core_api.user.User;
import core_api.transaction.TransactionRepository;
import core_api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTests {
    @Mock private CategoryRepository categories;
    @Mock private TransactionRepository transactions;
    @Mock private UserRepository users;
    @Mock private CategoryDefaultInitializationRepository initializations;
    @InjectMocks private CategoryService service;

    @Test
    void updateUsesOwnerScopedLookupAndDoesNotSaveUnavailableCategory() {
        UUID owner = UUID.randomUUID();
        when(users.findByIdForCategoryUpdate(owner)).thenReturn(Optional.of(new User("User", "u@example.com", "hash")));
        when(categories.findByIdAndUser_Id(42L, owner)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(owner, 42L, new CategoryRequest("Education", CategoryType.EXPENSE, null)))
                .isInstanceOf(CategoryNotFoundException.class);
        verify(categories, never()).saveAndFlush(any());
    }

    @Test
    void deleteUsesOwnerScopedLookupAndDoesNotDeleteUnavailableCategory() {
        UUID owner = UUID.randomUUID();
        when(users.findByIdForCategoryUpdate(owner)).thenReturn(Optional.of(new User("User", "u@example.com", "hash")));
        when(categories.findByIdAndUser_Id(42L, owner)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(owner, 42L)).isInstanceOf(CategoryNotFoundException.class);
        verify(categories, never()).delete(any(Category.class));
    }
}
