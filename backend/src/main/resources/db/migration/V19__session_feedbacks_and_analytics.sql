-- V19__session_feedbacks_and_analytics.sql
-- Pipeline Analítico: Tablas de feedback de alumnos y métricas consolidadas de mentores (HU-PS05)

-- 1. Tabla Fuente: Calificaciones y comentarios de los alumnos post-sesión (CA 5.1)
CREATE TABLE IF NOT EXISTS session_feedbacks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID,
    mentor_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_feedbacks_mentor ON session_feedbacks(mentor_id);
CREATE INDEX IF NOT EXISTS idx_feedbacks_processed ON session_feedbacks(processed_at);

-- 2. Tabla Destino: Consolidado analítico por mentor generado por PySpark (CA 5.3)
CREATE TABLE IF NOT EXISTS mentor_analytics (
    mentor_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    total_feedbacks INT NOT NULL DEFAULT 0,
    avg_rating NUMERIC(3,2) NOT NULL DEFAULT 0.00,
    weighted_score NUMERIC(4,2) NOT NULL DEFAULT 0.00,
    positive_count INT NOT NULL DEFAULT 0,
    neutral_count INT NOT NULL DEFAULT 0,
    critical_count INT NOT NULL DEFAULT 0,
    top_strengths TEXT,
    areas_of_improvement TEXT,
    last_processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. Crear usuario estudiante para pruebas si no existe
INSERT INTO app_users (id, name, email, password, role, status, created_at, updated_at)
SELECT 
    '00000000-0000-0000-0000-000000000004',
    'Estudiante de Prueba',
    'student_test@skillbridge.com',
    '$2a$10$49qAVP4FWPTQAafyOtkzduWq5Q/KkkdLMRd5xXScA8Mm5D0bQIBqa',
    'PARTICIPANT',
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM app_users WHERE email = 'student_test@skillbridge.com'
);

-- 4. Semillas de prueba para validar el procesamiento analítico y de LLM (CA 5.1, CA 5.2)
-- Mentor 1 (Proveedor Inicial): Feedbacks con pedagogía, puntualidad y técnica
INSERT INTO session_feedbacks (mentor_id, student_id, rating, comment, created_at)
VALUES 
(
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000004',
    5,
    'Excelente mentoría. El mentor domina Spring Boot y Java a la perfección, además explicó la arquitectura hexagonal con mucha paciencia y claridad didáctica.',
    NOW() - INTERVAL '3 days'
),
(
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000004',
    2,
    'El tutor llegó 20 minutos tarde a la reunión virtual. Aunque tiene buen nivel técnico, tuvimos que correr al final por la falta de puntualidad.',
    NOW() - INTERVAL '2 days'
),
(
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000004',
    3,
    'La sesión estuvo regular. Se cubrió el temario técnico pero sentí que las explicaciones fueron muy aceleradas y faltó pedagogía.',
    NOW() - INTERVAL '1 day'
);

-- Mentor 2 (Proveedor de Pruebas): Feedback positivo de frontend
INSERT INTO session_feedbacks (mentor_id, student_id, rating, comment, created_at)
VALUES 
(
    '00000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000004',
    5,
    'Súper puntual y muy claro al enseñar. Resolvió todas las dudas de Angular y RxJS sin problema.',
    NOW() - INTERVAL '4 hours'
);