UPDATE app_user SET status = 'INACTIVE' WHERE status = 'REVOKED';

ALTER TABLE app_user
    DROP CONSTRAINT chk_app_user_status,
    ADD CONSTRAINT chk_app_user_status CHECK (status IN ('ACTIVE', 'INACTIVE'));
