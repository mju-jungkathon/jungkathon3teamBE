CREATE TABLE user_goals (
    user_id         UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    goal_type       VARCHAR(50),
    weekly_run_goal INTEGER,
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);
