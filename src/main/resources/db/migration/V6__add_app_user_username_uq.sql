ALTER TABLE app_user
    ADD CONSTRAINT uq_app_user_username UNIQUE (username);