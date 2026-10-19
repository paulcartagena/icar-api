ALTER TABLE app_user
    DROP CONSTRAINT chk_app_user_role,
    ADD CONSTRAINT chk_app_user_role CHECK (role IN ('ADMIN', 'PASTOR', 'TREASURER'));

CREATE TABLE refresh_token(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL CONSTRAINT uq_refresh_token_hash UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);
