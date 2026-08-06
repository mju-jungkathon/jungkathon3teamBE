CREATE TABLE integration_status (
    user_id             UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    location_linked     BOOLEAN NOT NULL DEFAULT false,
    camera_permission   BOOLEAN NOT NULL DEFAULT false,
    location_permission BOOLEAN NOT NULL DEFAULT false,
    apple_health_linked BOOLEAN NOT NULL DEFAULT false
);
