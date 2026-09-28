ALTER TABLE income
    ADD CONSTRAINT uq_income_source_donation UNIQUE (source_donation_id);