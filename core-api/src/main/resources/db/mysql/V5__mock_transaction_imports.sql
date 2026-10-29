-- Additive upgrade; retain all existing manual transactions and migration checksums.
ALTER TABLE transactions MODIFY COLUMN source VARCHAR(32) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE transactions ADD COLUMN external_transaction_id VARCHAR(100);
ALTER TABLE transactions DROP CHECK ck_transactions_source;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_source CHECK (
    (source = 'MANUAL' AND external_transaction_id IS NULL) OR
    (source = 'MOCK_PROVIDER' AND external_transaction_id IS NOT NULL AND LENGTH(TRIM(external_transaction_id)) > 0)
);
ALTER TABLE transactions ADD CONSTRAINT uk_transactions_owner_source_external
    UNIQUE (user_id, source, external_transaction_id);
