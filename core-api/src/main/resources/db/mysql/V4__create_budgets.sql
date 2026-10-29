CREATE TABLE budgets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    category_id BIGINT NOT NULL,
    category_type VARCHAR(7) NOT NULL DEFAULT 'EXPENSE',
    `month` INTEGER NOT NULL,
    `year` INTEGER NOT NULL,
    monthly_limit DECIMAL(19,2) NOT NULL,
    alert_threshold INTEGER NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_budgets_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_budgets_expense_category FOREIGN KEY (category_id, user_id, category_type)
        REFERENCES categories(id, user_id, type),
    CONSTRAINT uk_budgets_user_category_period UNIQUE (user_id, category_id, `month`, `year`),
    CONSTRAINT ck_budgets_expense CHECK (category_type = 'EXPENSE'),
    CONSTRAINT ck_budgets_month CHECK (`month` BETWEEN 1 AND 12),
    CONSTRAINT ck_budgets_year CHECK (`year` BETWEEN 2000 AND 2100),
    CONSTRAINT ck_budgets_limit CHECK (monthly_limit > 0),
    CONSTRAINT ck_budgets_threshold CHECK (alert_threshold BETWEEN 1 AND 99)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE INDEX idx_budgets_user_period ON budgets(user_id, `year`, `month`, id);
