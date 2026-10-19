package core_api.category;

import core_api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "categories", uniqueConstraints = @UniqueConstraint(
        name = "uk_categories_user_type_name", columnNames = {"user_id", "type", "normalized_name"}))
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    private CategoryType type;

    @Column(length = 7)
    private String color;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Category() {
    }

    public Category(User user, String name, CategoryType type, String color) {
        this.user = user;
        update(name, type, color);
    }

    public static String cleanName(String name) {
        return name == null ? null : name.replaceAll("(?U)\\s+", " ").strip();
    }

    public static String normalizeName(String name) {
        return cleanName(name).toLowerCase(Locale.ROOT);
    }

    public void update(String name, CategoryType type, String color) {
        this.name = cleanName(name);
        this.normalizedName = normalizeName(name);
        this.type = type;
        this.color = color == null ? null : color.toUpperCase(Locale.ROOT);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getNormalizedName() { return normalizedName; }
    public CategoryType getType() { return type; }
    public String getColor() { return color; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
