package core_api.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByUser_IdOrderByTypeAscNormalizedNameAscIdAsc(UUID userId);
    List<Category> findAllByUser_IdAndTypeOrderByNormalizedNameAscIdAsc(UUID userId, CategoryType type);
    Optional<Category> findByIdAndUser_Id(Long id, UUID userId);
    boolean existsByUser_IdAndTypeAndNormalizedName(UUID userId, CategoryType type, String normalizedName);
    boolean existsByUser_IdAndTypeAndNormalizedNameAndIdNot(UUID userId, CategoryType type, String normalizedName, Long id);
}
