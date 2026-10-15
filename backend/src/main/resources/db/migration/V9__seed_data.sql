-- Insert a default provider
INSERT INTO app_users (id, name, email, password, role, status) VALUES
('00000000-0000-0000-0000-000000000001', 'Proveedor Inicial', 'provider@skillbridge.com', '$2a$10$tZ2f6g9l.1U/7f918QZ6oO0K2y0d9L/l1U9s8gZ6oO0K2y0d9L/l1', 'PROVIDER', 'ACTIVE') ON CONFLICT DO NOTHING;

-- Insert categories (migrating the 3 categories from V1)
INSERT INTO catalog_items (id, name, code, detail, type, status) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Backend', 'CAT-BACKEND', 'Categoría Backend', 'CATEGORY', 'ACTIVE'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Frontend', 'CAT-FRONTEND', 'Categoría Frontend', 'CATEGORY', 'ACTIVE'),
('cccccccc-cccc-cccc-cccc-cccccccccccc', 'Cloud', 'CAT-CLOUD', 'Categoría Cloud', 'CATEGORY', 'ACTIVE') ON CONFLICT DO NOTHING;

-- Insert services (migrating the 3 offerings from V1)
INSERT INTO services (id, name, category_id, price, detail, short_description, capacity, code, status, created_by) VALUES
('11111111-1111-1111-1111-111111111111', 'Mentoría Java Backend', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 85000, 'Sesión de arquitectura, Spring Boot y APIs REST.', 'Sesión de arquitectura, Spring Boot y APIs REST.', 10, 'SRV-JAVA-01', 'ACTIVE', '00000000-0000-0000-0000-000000000001'),
('22222222-2222-2222-2222-222222222222', 'Mentoría Angular', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 75000, 'Sesión práctica de Angular, RxJS y arquitectura frontend.', 'Sesión práctica de Angular, RxJS y arquitectura frontend.', 10, 'SRV-ANG-01', 'ACTIVE', '00000000-0000-0000-0000-000000000001'),
('33333333-3333-3333-3333-333333333333', 'Diseño de Arquitectura Cloud', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 120000, 'Revisión de una solución distribuida con Docker y cloud.', 'Revisión de una solución distribuida con Docker y cloud.', 10, 'SRV-CLD-01', 'ACTIVE', '00000000-0000-0000-0000-000000000001') ON CONFLICT DO NOTHING;

