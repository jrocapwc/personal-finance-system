package core_api.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category_default_initializations")
public class CategoryDefaultInitialization {
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "initialized_at", nullable = false)
    private Instant initializedAt;

    protected CategoryDefaultInitialization() {
    }

    public CategoryDefaultInitialization(UUID userId) {
        this.userId = userId;
        this.initializedAt = Instant.now();
    }
}
