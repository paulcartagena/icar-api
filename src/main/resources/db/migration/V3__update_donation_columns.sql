ALTER TABLE donation
    ADD COLUMN registered_by UUID
        CONSTRAINT fk_donation_registered_by REFERENCES app_user(id) ON DELETE SET NULL;

ALTER TABLE donation
    DROP COLUMN currency;
