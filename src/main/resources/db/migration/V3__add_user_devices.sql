-- V3__add_user_devices.sql
-- Device registry for push notifications (FCM tokens), plus PROFILE_UPDATED audit action

CREATE TABLE user_devices (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(10) NOT NULL CHECK (platform IN ('ANDROID', 'IOS')),
    token VARCHAR(4096) NOT NULL,
    device_name VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_devices_token UNIQUE (token)
);

CREATE INDEX idx_user_devices_user ON user_devices (user_id);

ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_action_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check
    CHECK (action IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'USER_REGISTERED', 'ACCOUNT_DELETED',
        'PARTNER_REQUEST_SENT', 'PARTNER_REQUEST_ACCEPTED', 'PARTNER_REQUEST_REJECTED',
        'PARTNER_UNPAIRED', 'SUPPORT_REQUEST_CREATED', 'SOS_TRIGGERED',
        'LOGOUT', 'PASSWORD_CHANGED', 'ACCOUNT_LOCKED', 'PROFILE_UPDATED'
    ));
