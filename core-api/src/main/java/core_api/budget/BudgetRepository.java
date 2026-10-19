package core_api.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    Optional<Budget> findByIdAndUser_Id(Long id, UUID userId);
    List<Budget> findAllByUser_IdAndMonthAndYearOrderByIdAsc(UUID userId, Integer month, Integer year);
    boolean existsByUser_IdAndCategory_IdAndMonthAndYear(UUID userId, Long categoryId, Integer month, Integer year);
    boolean existsByUser_IdAndCategory_IdAndMonthAndYearAndIdNot(UUID userId, Long categoryId, Integer month, Integer year, Long id);
    boolean existsByCategory_Id(Long categoryId);
}
