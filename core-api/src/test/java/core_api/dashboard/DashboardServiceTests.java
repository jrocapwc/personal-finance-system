package core_api.dashboard;

import core_api.budget.BudgetService;
import core_api.category.CategoryType;
import core_api.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTests {
    @Mock private TransactionRepository transactions;
    @Mock private BudgetService budgets;
    @InjectMocks private DashboardService dashboard;

    @Test
    void passesOnlyAuthenticatedOwnerAndHalfOpenBoundsAndLimitsRecentQuery() {
        UUID owner = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 12, 1), end = LocalDate.of(2027, 1, 1);
        when(transactions.summarizeCategories(owner, start, end)).thenReturn(List.of());
        when(transactions.sumDailyExpenses(owner, start, end)).thenReturn(List.of());
        when(transactions.findRecentForDashboard(any(), any(), any(), any())).thenReturn(List.of());
        when(budgets.list(owner, 12, 2026)).thenReturn(List.of());
        var response = dashboard.get(owner, 12, 2026);
        assertThat(response.dailySpendingTrend()).hasSize(31);
        verify(transactions).summarizeCategories(owner, start, end);
        verify(transactions).sumDailyExpenses(owner, start, end);
        var page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(transactions).findRecentForDashboard(org.mockito.ArgumentMatchers.eq(owner),
                org.mockito.ArgumentMatchers.eq(start), org.mockito.ArgumentMatchers.eq(end), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(5);
        assertThat(page.getValue().getOffset()).isZero();
        verify(budgets).list(owner, 12, 2026);
    }

    @Test
    void invalidPeriodFailsBeforeAnyQueries() {
        assertThatThrownBy(() -> dashboard.get(UUID.randomUUID(), 13, 2026))
                .isInstanceOf(core_api.budget.InvalidBudgetRequestException.class);
        verifyNoInteractions(transactions, budgets);
    }

    @Test
    void categoryTieBreakersDoNotDependOnDatabaseRowOrder() {
        UUID owner = UUID.randomUUID();
        var a = row(2L);
        var b = row(1L);
        when(transactions.summarizeCategories(any(), any(), any())).thenReturn(List.of(a, b));
        when(transactions.sumDailyExpenses(any(), any(), any())).thenReturn(List.of());
        when(transactions.findRecentForDashboard(any(), any(), any(), any())).thenReturn(List.of());
        when(budgets.list(owner, 10, 2026)).thenReturn(List.of());
        assertThat(dashboard.get(owner, 10, 2026).topSpendingCategories()).extracting(CategoryBreakdown::categoryId).containsExactly(1L, 2L);
    }

    private TransactionRepository.DashboardCategoryTotal row(long id) {
        var row = mock(TransactionRepository.DashboardCategoryTotal.class);
        when(row.getType()).thenReturn(CategoryType.EXPENSE);
        when(row.getCategoryId()).thenReturn(id);
        when(row.getCategoryName()).thenReturn("Category");
        when(row.getTotalAmount()).thenReturn(new BigDecimal("10.00"));
        return row;
    }
}
