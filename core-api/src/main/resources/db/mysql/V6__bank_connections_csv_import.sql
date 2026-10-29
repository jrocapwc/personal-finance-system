ALTER TABLE transactions DROP CHECK ck_transactions_source;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_source CHECK (
 (source = 'MANUAL' AND external_transaction_id IS NULL) OR
 (source IN ('MOCK_PROVIDER', 'CSV_STATEMENT') AND external_transaction_id IS NOT NULL AND LENGTH(TRIM(external_transaction_id)) > 0)
);
CREATE TABLE demo_connections (
 user_id CHAR(36) PRIMARY KEY,
 connected BOOLEAN NOT NULL,
 last_sync DATETIME(6), CONSTRAINT fk_demo_connections_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE statement_imports (
 id CHAR(36) PRIMARY KEY,
 user_id CHAR(36) NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 status VARCHAR(12) NOT NULL,
 imported_count INTEGER NOT NULL DEFAULT 0,
 skipped_count INTEGER NOT NULL DEFAULT 0,
 total_count INTEGER NOT NULL,
 CONSTRAINT fk_statement_imports_user FOREIGN KEY (user_id) REFERENCES users(id),
 CONSTRAINT ck_statement_status CHECK (status IN ('PREVIEW','CONFIRMED','CANCELLED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE INDEX idx_statement_expiry ON statement_imports(status, expires_at);
CREATE TABLE statement_rows (
 batch_id CHAR(36) NOT NULL,
 `row_number` INTEGER NOT NULL,
 transaction_date DATE,
 description VARCHAR(255),
 type VARCHAR(7),
 amount DECIMAL(19,2),
 external_id VARCHAR(100) NOT NULL,
 validation_error VARCHAR(255),
 PRIMARY KEY(batch_id, `row_number`), CONSTRAINT fk_statement_rows_batch FOREIGN KEY (batch_id) REFERENCES statement_imports(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
