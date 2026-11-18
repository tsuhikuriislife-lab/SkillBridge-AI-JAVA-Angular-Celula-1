-- Crea la base de datos del microservicio de notificaciones si no existe.
-- Postgres ejecuta este archivo SOLO al crear el volumen por primera vez;
-- para volúmenes existentes usa: make db-notifications
SELECT 'CREATE DATABASE notifications'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'notifications')\gexec
