CREATE TABLE stretching_sessions (
    stretching_session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    type                   VARCHAR(50),
    started_at             TIMESTAMP NOT NULL
);

CREATE INDEX idx_stretching_sessions_user_id ON stretching_sessions(user_id);
