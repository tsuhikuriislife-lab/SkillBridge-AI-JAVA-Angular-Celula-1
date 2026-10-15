ALTER TABLE schedule_history RENAME TO booking_notifications;

ALTER TABLE booking_notifications
    RENAME CONSTRAINT schedule_history_pkey TO booking_notifications_pkey;

ALTER TABLE booking_notifications
    RENAME CONSTRAINT uq_schedule_history_delivery TO uq_booking_notifications_delivery;

ALTER TABLE booking_notifications
    RENAME CONSTRAINT chk_schedule_history_status TO chk_booking_notifications_status;

ALTER TABLE booking_notifications
    RENAME CONSTRAINT chk_schedule_history_type TO chk_booking_notifications_type;

ALTER TABLE booking_notifications
    RENAME CONSTRAINT schedule_history_booking_id_fkey TO fk_booking_notifications_booking;

ALTER INDEX idx_schedule_history_expiration
    RENAME TO idx_booking_notifications_expiration;

ALTER INDEX idx_schedule_history_booking_created
    RENAME TO idx_booking_notifications_booking_created;
