DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM app_users
        WHERE role = 'ADMIN'
        GROUP BY role
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot enforce one administrator: multiple ADMIN users already exist';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_users_single_admin
    ON app_users (role)
    WHERE role = 'ADMIN';