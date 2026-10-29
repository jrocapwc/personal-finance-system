ALTER TABLE users ADD COLUMN token_version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE users ADD CONSTRAINT ck_users_token_version CHECK (token_version >= 0);
