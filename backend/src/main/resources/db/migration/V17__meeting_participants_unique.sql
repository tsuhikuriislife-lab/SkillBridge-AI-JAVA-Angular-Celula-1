-- V17__meeting_participants_unique.sql
-- Garantizar unicidad de participantes por reunión y eliminar registros duplicados residuales

DELETE FROM meeting_participants mp
WHERE mp.id NOT IN (
    SELECT id FROM (
        SELECT DISTINCT ON (meeting_id, user_id) id
        FROM meeting_participants
        ORDER BY meeting_id, user_id, updated_at DESC, created_at DESC
    ) latest
);

ALTER TABLE meeting_participants DROP CONSTRAINT IF EXISTS uq_participant_per_meeting;
ALTER TABLE meeting_participants ADD CONSTRAINT uq_participant_per_meeting UNIQUE (meeting_id, user_id);

