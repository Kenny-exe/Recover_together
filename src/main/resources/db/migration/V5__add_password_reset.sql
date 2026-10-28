-- V5__add_password_reset.sql
-- Self-service password reset tokens and the PASSWORD_RESET audit action.
--
-- Only a SHA-256 hash of the reset code is stored; the raw code is sent to
-- the user by email and can be consumed exactly once.

CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_tokens_expiry ON password_reset_tokens (expires_at);

ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_action_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check
    CHECK (action IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'USER_REGISTERED', 'ACCOUNT_DELETED',
        'PARTNER_REQUEST_SENT', 'PARTNER_REQUEST_ACCEPTED', 'PARTNER_REQUEST_REJECTED',
        'PARTNER_UNPAIRED', 'SUPPORT_REQUEST_CREATED', 'SOS_TRIGGERED',
        'LOGOUT', 'PASSWORD_CHANGED', 'ACCOUNT_LOCKED', 'PROFILE_UPDATED',
        'PASSWORD_RESET'
    ));
