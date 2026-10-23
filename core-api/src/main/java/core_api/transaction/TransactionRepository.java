package core_api.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {
    Optional<Transaction> findByIdAndUser_Id(Long id, UUID userId);
    boolean existsByCategory_Id(Long categoryId);
    boolean existsByUser_IdAndSourceAndExternalTransactionId(UUID userId, TransactionSource source, String externalId);

    interface DashboardCategoryTotal {
        core_api.category.CategoryType getType();
        Long getCategoryId();
        String getCategoryName();
        String getColor();
        BigDecimal getTotalAmount();
    }

    interface DashboardDailyTotal {
        LocalDate getTransactionDate();
        BigDecimal getTotalAmount();
    }

    @Query("""
            select t.type as type, c.id as categoryId, c.name as categoryName, c.color as color,
                   sum(t.amount) as totalAmount
            from Transaction t join t.category c
            where t.user.id = :userId and c.user.id = :userId
              and t.transactionDate >= :start and t.transactionDate < :end
            group by t.type, c.id, c.name, c.color
            """)
    List<DashboardCategoryTotal> summarizeCategories(@Param("userId") UUID userId,
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("""
            select t.transactionDate as transactionDate, sum(t.amount) as totalAmount from Transaction t
            where t.user.id = :userId and t.type = core_api.category.CategoryType.EXPENSE
              and t.transactionDate >= :start and t.transactionDate < :end
            group by t.transactionDate
            """)
    List<DashboardDailyTotal> sumDailyExpenses(@Param("userId") UUID userId,
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("""
            select t from Transaction t where t.user.id = :userId
              and t.transactionDate >= :start and t.transactionDate < :end
            order by t.transactionDate desc, t.createdAt desc, t.id desc
            """)
    List<Transaction> findRecentForDashboard(@Param("userId") UUID userId,
            @Param("start") LocalDate start, @Param("end") LocalDate end,
            org.springframework.data.domain.Pageable pageable);
    interface ExpenseTotal {
        Long getCategoryId();
        BigDecimal getSpentAmount();
    }

    @Query("""
            select t.category.id as categoryId, sum(t.amount) as spentAmount from Transaction t
            where t.user.id = :userId and t.type = core_api.category.CategoryType.EXPENSE
            and t.transactionDate >= :start and t.transactionDate < :end
            group by t.category.id
            """)
    List<ExpenseTotal> sumExpensesByCategory(@Param("userId") UUID userId,
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("""
            select sum(t.amount) from Transaction t
            where t.user.id = :userId and t.category.id = :categoryId
            and t.type = core_api.category.CategoryType.EXPENSE
            and t.transactionDate >= :start and t.transactionDate < :end
            """)
    BigDecimal sumExpenseForCategory(@Param("userId") UUID userId, @Param("categoryId") Long categoryId,
            @Param("start") LocalDate start, @Param("end") LocalDate end);
}
