-- Reemplaza el stack viejo (offerings/bookings) por el nuevo esquema
DROP TABLE IF EXISTS bookings;
DROP TABLE IF EXISTS offerings;

CREATE TABLE services (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    category_id UUID NOT NULL REFERENCES catalog_items(id),
    price NUMERIC(12,2) NOT NULL,
    detail TEXT,
    short_description VARCHAR(500),
    learning_objectives TEXT,
    prerequisites TEXT,
    capacity INT,
    code VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    created_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_service_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_service_price CHECK (price >= 0)
);

CREATE TABLE service_schedules (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    start_day VARCHAR(20) NOT NULL,
    session_duration INT NOT NULL,
    frequency VARCHAR(50) NOT NULL,
    number_of_sessions INT NOT NULL,
    start_date DATE NOT NULL,
    CONSTRAINT chk_schedule_sessions CHECK (number_of_sessions > 0),
    CONSTRAINT chk_schedule_duration CHECK (session_duration > 0)
);

CREATE TABLE user_services (
    user_id UUID NOT NULL REFERENCES app_users(id),
    service_id UUID NOT NULL REFERENCES services(id),
    status VARCHAR(30) NOT NULL,
    remaining_sessions INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    PRIMARY KEY (user_id, service_id),
    CONSTRAINT chk_enrollment_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE schedule_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    service_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    CONSTRAINT chk_history_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_services_category ON services(category_id);
CREATE INDEX idx_services_created_by ON services(created_by);
CREATE INDEX idx_user_services_service ON user_services(service_id);
CREATE INDEX idx_history_user_service ON schedule_history(user_id, service_id);
