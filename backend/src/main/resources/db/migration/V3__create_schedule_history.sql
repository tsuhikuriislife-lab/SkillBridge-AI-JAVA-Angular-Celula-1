CREATE TABLE schedule_history (
    notification_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    notification_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    error_code VARCHAR(80),
    CONSTRAINT uq_schedule_history_delivery
        UNIQUE (booking_id, event_type, notification_type),
    CONSTRAINT chk_schedule_history_status
        CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    CONSTRAINT chk_schedule_history_type
        CHECK (notification_type IN ('IN_APP'))
);

CREATE INDEX idx_schedule_history_expiration
    ON schedule_history(expires_at);

CREATE INDEX idx_schedule_history_booking_created
    ON schedule_history(booking_id, created_at DESC);
