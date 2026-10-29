CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    type VARCHAR(7) NOT NULL,
    color VARCHAR(7),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_categories_user_type_name UNIQUE (user_id, type, normalized_name),
    CONSTRAINT ck_categories_name_nonempty CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_categories_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_categories_color CHECK (color IS NULL OR REGEXP_LIKE(color, '^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- The unique index above covers user/type/name lookups; this covers unfiltered user lists.
CREATE INDEX idx_categories_user_name ON categories(user_id, normalized_name, id);

-- Tracks one-time seeding independently of category names and deletions.
CREATE TABLE category_default_initializations (
    user_id CHAR(36) NOT NULL PRIMARY KEY,
    initialized_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_category_defaults_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
