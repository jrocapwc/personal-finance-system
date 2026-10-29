ALTER TABLE transactions DROP CONSTRAINT ck_transactions_source;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_source CHECK (
 (source = 'MANUAL' AND external_transaction_id IS NULL) OR
 (source IN ('MOCK_PROVIDER', 'CSV_STATEMENT') AND external_transaction_id IS NOT NULL AND LENGTH(TRIM(external_transaction_id)) > 0)
);
CREATE TABLE demo_connections (
 user_id UUID PRIMARY KEY REFERENCES users(id),
 connected BOOLEAN NOT NULL,
 last_sync TIMESTAMP WITH TIME ZONE
);
CREATE TABLE statement_imports (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES users(id),
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
 status VARCHAR(12) NOT NULL,
 imported_count INTEGER NOT NULL DEFAULT 0,
 skipped_count INTEGER NOT NULL DEFAULT 0,
 total_count INTEGER NOT NULL,
 CONSTRAINT ck_statement_status CHECK (status IN ('PREVIEW','CONFIRMED','CANCELLED','EXPIRED'))
);
CREATE INDEX idx_statement_expiry ON statement_imports(status, expires_at);
CREATE TABLE statement_rows (
 batch_id UUID NOT NULL REFERENCES statement_imports(id),
 row_number INTEGER NOT NULL,
 transaction_date DATE,
 description VARCHAR(255),
 type VARCHAR(7),
 amount DECIMAL(19,2),
 external_id VARCHAR(100) NOT NULL,
 validation_error VARCHAR(255),
 PRIMARY KEY(batch_id, row_number)
);
