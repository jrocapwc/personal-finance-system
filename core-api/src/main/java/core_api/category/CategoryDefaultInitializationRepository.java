package core_api.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CategoryDefaultInitializationRepository extends JpaRepository<CategoryDefaultInitialization, UUID> {
}
