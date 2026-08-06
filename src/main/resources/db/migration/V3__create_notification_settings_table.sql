CREATE TABLE notification_settings (
    user_id               UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    running_reminder_time TIME,
    weekly_report_day     VARCHAR(20),
    weekly_report_time    TIME
);
