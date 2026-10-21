ALTER TABLE donation
    ADD COLUMN voided BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN voided_at TIMESTAMP,
    ADD COLUMN void_reason VARCHAR(250),
    ADD CONSTRAINT chk_donation_void CHECK (
        (voided = false AND voided_at IS NULL AND void_reason IS NULL)
        OR (voided = true AND voided_at IS NOT NULL AND void_reason IS NOT NULL)
    ),
    ADD CONSTRAINT chk_donation_amount CHECK (amount > 0);
