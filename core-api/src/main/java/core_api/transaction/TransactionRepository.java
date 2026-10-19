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
