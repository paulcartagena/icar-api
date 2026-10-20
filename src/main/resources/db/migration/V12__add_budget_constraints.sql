ALTER TABLE budget
    ADD CONSTRAINT chk_budget_period CHECK (period_end >= period_start),
    ADD CONSTRAINT chk_budget_planned_amount CHECK (planned_amount > 0);

CREATE INDEX idx_budget_category_id ON budget (category_id);
