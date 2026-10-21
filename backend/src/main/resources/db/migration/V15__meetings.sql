-- Sistema de reuniones virtuales con LiveKit asociadas a los servicios.
-- Entidades: meetings (ciclo de vida de la reunion), meeting_participants (seguimiento
-- individual) y meeting_history (trazabilidad auditable de eventos).

CREATE TABLE meetings (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES services(id),
    schedule_id UUID NOT NULL REFERENCES service_schedules(id),
    meeting_date DATE NOT NULL,
    start_time TIME NOT NULL,
    duration_minutes INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    livekit_room VARCHAR(120) NOT NULL UNIQUE,
    room_created BOOLEAN NOT NULL DEFAULT FALSE,
    scheduled_start TIMESTAMPTZ NOT NULL,
    actual_start TIMESTAMPTZ,
    actual_end TIMESTAMPTZ,
    max_end TIMESTAMPTZ,
    extension_minutes INT NOT NULL DEFAULT 20,
    extension_used BOOLEAN NOT NULL DEFAULT FALSE,
    cancellation_reason TEXT,
    cancelled_by UUID REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_meeting_status CHECK (status IN ('PROGRAMADA', 'EN_CURSO', 'CANCELADA', 'NO_REALIZADA', 'FINALIZADA')),
    CONSTRAINT chk_meeting_duration CHECK (duration_minutes > 0),
    CONSTRAINT chk_meeting_extension CHECK (extension_minutes >= 0),
    CONSTRAINT uq_meeting_per_session UNIQUE (service_id, schedule_id, meeting_date)
);

CREATE TABLE meeting_participants (
    id UUID PRIMARY KEY,
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users(id),
    role VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    joined_at TIMESTAMPTZ,
    left_at TIMESTAMPTZ,
    denial_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_participant_role CHECK (role IN ('PROVIDER', 'PARTICIPANT')),
    CONSTRAINT chk_participant_status CHECK (status IN ('AUTORIZADO', 'INGRESO_REGISTRADO', 'EN_SESION', 'SALIDA_REGISTRADA', 'NO_ASISTIO', 'ACCESO_DENEGADO'))
);

CREATE TABLE meeting_history (
   id UUID PRIMARY KEY,
   meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
   user_id UUID,
   event_type VARCHAR(40) NOT NULL,
   result VARCHAR(30),
   reason TEXT,
   metadata TEXT,
   created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
   CONSTRAINT chk_meeting_event_type CHECK (event_type IN (
     'REUNION_PROGRAMADA',
     'SALA_CREADA',
     'REUNION_INICIADA',
     'PARTICIPANTE_INGRESO',
     'PARTICIPANTE_SALIO',
     'ACCESO_RECHAZADO',
     'EXTENSION_CONCEDIDA',
     'REUNION_CANCELADA',
     'REUNION_NO_REALIZADA',
     'REUNION_FINALIZADA',
     'CIERRE_POR_LIMITE',
     'ERROR_LIVEKIT'
     ))
);

CREATE INDEX idx_meetings_service ON meetings(service_id);
CREATE INDEX idx_meetings_status_date ON meetings(status, meeting_date);
CREATE INDEX idx_meeting_participants_user ON meeting_participants(user_id);
CREATE INDEX idx_meeting_participants_meeting ON meeting_participants(meeting_id);
CREATE INDEX idx_meeting_history_meeting ON meeting_history(meeting_id, created_at);
