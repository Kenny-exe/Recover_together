-- V7__add_notification_preferences.sql
-- Notification delivery preferences: a global push switch, optional quiet
-- hours evaluated in the user's own timezone, and per-type push toggles.
-- In-app notifications (and the SSE stream) are always delivered; these
-- settings only gate mobile push. SOS alerts bypass every preference.
--
-- Existing users keep the previous behaviour: push enabled, no quiet hours,
-- every notification type enabled (absence of a row means enabled).

ALTER TABLE users ADD COLUMN push_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN quiet_hours_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN quiet_hours_start TIME;
ALTER TABLE users ADD COLUMN quiet_hours_end TIME;

CREATE TABLE notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notification_type VARCHAR(40) NOT NULL CHECK (notification_type IN (
        'NEW_MESSAGE', 'SOS_ALERT', 'PARTNER_RELAPSE', 'MISSED_CHECKIN',
        'ACHIEVEMENT_UNLOCKED', 'PARTNER_ACHIEVEMENT',
        'DAILY_CHECKIN_REMINDER', 'SUPPORT_REQUEST'
    )),
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_notification_preferences_user_type
        UNIQUE (user_id, notification_type)
);

CREATE INDEX idx_notification_preferences_user
    ON notification_preferences (user_id);
