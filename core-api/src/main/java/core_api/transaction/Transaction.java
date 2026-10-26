package core_api.transaction;

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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
public class Transaction {
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
    @Column(nullable = false, length = 7)
    private CategoryType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private TransactionSource source = TransactionSource.MANUAL;

    @Column(name = "external_transaction_id", updatable = false, length = 100)
    private String externalTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {
    }

    public Transaction(User user, Category category, TransactionRequest request) {
        this.user = user;
        update(category, request);
    }

    static Transaction imported(User user, Category category, TransactionRequest request, String externalId) {
        var transaction = new Transaction(user, category, request);
        transaction.source = TransactionSource.MOCK_PROVIDER;
        transaction.externalTransactionId = externalId;
        return transaction;
    }

    public static Transaction statement(User user, Category category, TransactionRequest request, String externalId) {
        var transaction = new Transaction(user, category, request);
        transaction.source = TransactionSource.CSV_STATEMENT;
        transaction.externalTransactionId = externalId;
        return transaction;
    }

    public void update(Category category, TransactionRequest request) {
        this.category = category;
        this.type = request.type();
        this.amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        this.description = request.description();
        this.transactionDate = request.transactionDate();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getCategoryId() { return category.getId(); }
    public CategoryType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public TransactionSource getSource() { return source; }
    public String getExternalTransactionId() { return externalTransactionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
