-- "Plan your visit" requests sent from the public website, so someone from the church contacts the person
CREATE TABLE visit_request(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(25) NOT NULL,
    email VARCHAR(150) NOT NULL,
    message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- The admin list shows the newest first
CREATE INDEX idx_visit_request_created_at ON visit_request (created_at DESC);
