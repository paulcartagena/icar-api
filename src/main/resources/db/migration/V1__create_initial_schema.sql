--INITIAL SCHEMA--

CREATE TABLE about_us (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(250) NOT NULL,
    body TEXT NOT NULL ,
    image_url VARCHAR(500) NOT NULL
);

CREATE TABLE ministry(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description TEXT
);

CREATE TABLE service_schedule(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ministry_id UUID REFERENCES ministry(id),
    day_of_week VARCHAR(25) NOT NULL,
    start_time TIME NOT NULL
);

CREATE TABLE contact_info(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    address VARCHAR(250) NOT NULL,
    phone VARCHAR(25) NOT NULL,
    email VARCHAR(150) NOT NULL,
    map_url VARCHAR(500) NOT NULL
);

CREATE TABLE event(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(250) NOT NULL,
    description TEXT NOT NULL,
    start_datetime TIMESTAMP NOT NULL,
    end_datetime TIMESTAMP NOT NULL,
    location VARCHAR(100),
    active BOOLEAN NOT NULL
);

CREATE TABLE event_photo(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    display_order INT
);

CREATE TABLE member(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID, -- NO ON DELETE CASCADE, you cant delete family with assigned members --
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(150),
    phone VARCHAR(25),
    join_date DATE NOT NULL,
    status VARCHAR(25) NOT NULL
        CONSTRAINT chk_member_status CHECK (status IN ('VISITOR', 'NEW', 'IN_DISCIPLESHIP', 'ACTIVE', 'INACTIVE', 'DECEASED'))
);

CREATE TABLE family(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    responsible_member_id UUID NOT NULL REFERENCES member(id),
    family_name VARCHAR(100) NOT NULL,
    notes VARCHAR(150)
);

ALTER TABLE member ADD CONSTRAINT fk_member_family
    FOREIGN KEY (family_id)REFERENCES family(id);

CREATE TABLE app_user(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(25) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(250) NOT NULL,
    role VARCHAR(25) NOT NULL CONSTRAINT chk_app_user_role CHECK (role IN ('ADMIN', 'TREASURER')),
    status VARCHAR(25) NOT NULL CONSTRAINT chk_app_user_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'REVOKED'))
);

CREATE TABLE category(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    type VARCHAR(25) NOT NULL CONSTRAINT chk_category_type CHECK (type IN ('INCOME', 'EXPENSE')),
    active BOOLEAN NOT NULL
);

CREATE TABLE donation(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    donor_name VARCHAR(150) NOT NULL,
    message VARCHAR(500),
    amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    method VARCHAR(25) NOT NULL CONSTRAINT chk_donation_method CHECK (method IN ('CASH', 'PAYPAL', 'TRANSFER')),
    status VARCHAR(25) NOT NULL CONSTRAINT chk_donation_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    paypal_order_id VARCHAR(100),
    paypal_capture_id VARCHAR(100),
    payer_email VARCHAR(150),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    completed_at TIMESTAMP
);

CREATE TABLE income(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES category(id),
    source_donation_id UUID REFERENCES donation(id) ON DELETE SET NULL,
    amount NUMERIC(10,2) NOT NULL,
    description VARCHAR(250),
    date DATE NOT NULL,
    registered_by UUID REFERENCES app_user(id) ON DELETE SET NULL
);

CREATE TABLE expense(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES category(id),
    amount NUMERIC(10,2) NOT NULL,
    description VARCHAR(250),
    date DATE NOT NULL,
    receipt_url VARCHAR(500),
    registered_by UUID REFERENCES app_user(id) ON DELETE SET NULL
);

CREATE TABLE budget(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES category(id),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    planned_amount NUMERIC(10,2) NOT NULL,
    created_by UUID NOT NULL REFERENCES app_user(id)
);






