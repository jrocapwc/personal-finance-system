-- A transaction's category must belong to its owner and have the same type.
ALTER TABLE categories ADD CONSTRAINT uk_categories_id_user_type UNIQUE (id, user_id, type);

CREATE TABLE transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    category_id BIGINT NOT NULL,
    type VARCHAR(7) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    description VARCHAR(255),
    transaction_date DATE NOT NULL,
    source VARCHAR(6) NOT NULL DEFAULT 'MANUAL',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_transactions_category_owner_type FOREIGN KEY (category_id, user_id, type)
        REFERENCES categories(id, user_id, type),
    CONSTRAINT ck_transactions_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_transactions_amount CHECK (amount > 0),
    CONSTRAINT ck_transactions_source CHECK (source = 'MANUAL')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE INDEX idx_transactions_user_date ON transactions(user_id, transaction_date, id);
CREATE INDEX idx_transactions_user_type ON transactions(user_id, type);
CREATE INDEX idx_transactions_user_category ON transactions(user_id, category_id);
