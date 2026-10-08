CREATE TABLE user_preferences (
    user_id UUID NOT NULL REFERENCES app_users(id),
    preference_id UUID NOT NULL REFERENCES catalog_items(id),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (user_id, preference_id),
    CONSTRAINT chk_user_pref_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
