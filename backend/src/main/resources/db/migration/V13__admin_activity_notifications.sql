CREATE TABLE admin_activity_notifications (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES app_users(id),
    action VARCHAR(40) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id UUID,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_activity_actor_created
    ON admin_activity_notifications (actor_id, created_at DESC);
