CREATE TABLE recovery_actions (
    action_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recovery_guide_id  UUID NOT NULL REFERENCES recovery_guides(recovery_guide_id) ON DELETE CASCADE,
    type                VARCHAR(50),
    title               VARCHAR(100),
    description          TEXT
);

CREATE INDEX idx_recovery_actions_guide_id ON recovery_actions(recovery_guide_id);
