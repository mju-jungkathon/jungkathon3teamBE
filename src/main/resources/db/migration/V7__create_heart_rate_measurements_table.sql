CREATE TABLE heart_rate_measurements (
    heart_rate_measurement_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    running_session_id         UUID NOT NULL REFERENCES running_sessions(running_session_id) ON DELETE CASCADE,
    heart_rate_source          VARCHAR(30),
    avg_bpm                    INTEGER,
    max_bpm                    INTEGER,
    hrv_ms                     INTEGER,
    measured_at                TIMESTAMP NOT NULL,
    sync_status                VARCHAR(20),
    signal_quality              VARCHAR(20)
);

CREATE INDEX idx_heart_rate_measurements_session_id ON heart_rate_measurements(running_session_id);
