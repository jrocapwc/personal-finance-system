package core_api.budget;

import core_api.category.Category;
import core_api.category.CategoryType;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "budgets", uniqueConstraints = @UniqueConstraint(name = "uk_budgets_user_category_period",
        columnNames = {"user_id", "category_id", "\"month\"", "\"year\""}))
public class Budget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false, updatable = false, length = 7)
    private CategoryType categoryType = CategoryType.EXPENSE;

    @Column(name = "\"month\"", nullable = false)
    private Integer month;

    @Column(name = "\"year\"", nullable = false)
    private Integer year;

    @Column(name = "monthly_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyLimit;

    @Column(name = "alert_threshold", nullable = false)
    private Integer alertThreshold;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Budget() {}

    public Budget(User user, Category category, BudgetRequest request) {
        this.user = user;
        update(category, request);
    }

    public void update(Category category, BudgetRequest request) {
        this.category = category;
        month = request.month();
        year = request.year();
        monthlyLimit = request.monthlyLimit().setScale(2, RoundingMode.UNNECESSARY);
        alertThreshold = request.alertThreshold();
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getCategoryId() { return category.getId(); }
    public Integer getMonth() { return month; }
    public Integer getYear() { return year; }
    public BigDecimal getMonthlyLimit() { return monthlyLimit; }
    public Integer getAlertThreshold() { return alertThreshold; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
