-- V16__admin_test_meeting.sql
-- Habilitar rol ADMIN en meeting_participants y flexibilizar unicidad para salas de prueba

ALTER TABLE meeting_participants DROP CONSTRAINT IF EXISTS chk_participant_role;
ALTER TABLE meeting_participants ADD CONSTRAINT chk_participant_role CHECK (role IN ('PROVIDER', 'PARTICIPANT', 'ADMIN'));

ALTER TABLE meetings DROP CONSTRAINT IF EXISTS uq_meeting_per_session;
