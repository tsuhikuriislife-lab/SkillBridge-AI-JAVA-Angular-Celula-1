CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE offerings (
    id UUID PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(1200) NOT NULL,
    category VARCHAR(80) NOT NULL,
    price NUMERIC(12,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    offering_id UUID NOT NULL REFERENCES offerings(id),
    customer_id UUID NOT NULL REFERENCES app_users(id),
    scheduled_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_offerings_active ON offerings(active);
CREATE INDEX idx_bookings_customer ON bookings(customer_id);
CREATE INDEX idx_bookings_offering ON bookings(offering_id);

INSERT INTO offerings (id, title, description, category, price, active) VALUES
('11111111-1111-1111-1111-111111111111', 'Mentoría Java Backend', 'Sesión de arquitectura, Spring Boot y APIs REST.', 'BACKEND', 85000, true),
('22222222-2222-2222-2222-222222222222', 'Mentoría Angular', 'Sesión práctica de Angular, RxJS y arquitectura frontend.', 'FRONTEND', 75000, true),
('33333333-3333-3333-3333-333333333333', 'Diseño de Arquitectura Cloud', 'Revisión de una solución distribuida con Docker y cloud.', 'CLOUD', 120000, true);