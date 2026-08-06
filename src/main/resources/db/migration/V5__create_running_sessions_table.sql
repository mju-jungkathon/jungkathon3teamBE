CREATE TABLE running_sessions (
    running_session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    started_at          TIMESTAMP NOT NULL,
    ended_at            TIMESTAMP,
    duration_sec        INTEGER,
    distance_km         DOUBLE PRECISION,
    intensity           VARCHAR(20),
    uv_index_at_start   INTEGER,
    status               VARCHAR(20) NOT NULL,
    lat                  DOUBLE PRECISION,
    lng                  DOUBLE PRECISION
);

CREATE INDEX idx_running_sessions_user_id ON running_sessions(user_id);
