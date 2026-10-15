-- V14__seed_test_users.sql
-- Seed an ADMIN test user if none exists
INSERT INTO app_users (id, name, email, password, role, status, created_at, updated_at)
SELECT 
    '00000000-0000-0000-0000-000000000002',
    'Admin Test',
    'admin@skillbridge.com',
    '$2a$10$49qAVP4FWPTQAafyOtkzduWq5Q/KkkdLMRd5xXScA8Mm5D0bQIBqa', -- password123
    'ADMIN',
    'ACTIVE',
    now(),
    now()
WHERE NOT EXISTS (
    SELECT 1 FROM app_users WHERE role = 'ADMIN' OR email = 'admin@skillbridge.com'
);

-- Seed an extra PROVIDER test user if none exists
INSERT INTO app_users (id, name, email, password, role, status, created_at, updated_at)
SELECT 
    '00000000-0000-0000-0000-000000000003',
    'Proveedor de Pruebas',
    'provider_test@skillbridge.com',
    '$2a$10$49qAVP4FWPTQAafyOtkzduWq5Q/KkkdLMRd5xXScA8Mm5D0bQIBqa', -- password123
    'PROVIDER',
    'ACTIVE',
    now(),
    now()
WHERE NOT EXISTS (
    SELECT 1 FROM app_users WHERE email = 'provider_test@skillbridge.com'
);

