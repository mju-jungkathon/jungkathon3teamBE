CREATE TABLE recovery_guides (
    recovery_guide_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    running_session_id UUID NOT NULL UNIQUE REFERENCES running_sessions(running_session_id) ON DELETE CASCADE,
    measured_bpm        INTEGER,
    summary_message     TEXT,
    cooldown_timer_sec  INTEGER,
    created_at           TIMESTAMP NOT NULL DEFAULT now()
);
