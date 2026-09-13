-- V2__align_enum_check_constraints.sql
-- Safely update PostgreSQL check constraints to match all current domain enums

ALTER TABLE daily_checkins DROP CONSTRAINT IF EXISTS daily_checkins_status_check;
ALTER TABLE daily_checkins ADD CONSTRAINT daily_checkins_status_check
    CHECK (status IN ('SUCCESS', 'RELAPSE', 'STRUGGLING', 'STRONG_URGE', 'NEED_SUPPORT'));

ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_action_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check
    CHECK (action IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'USER_REGISTERED', 'ACCOUNT_DELETED',
        'PARTNER_REQUEST_SENT', 'PARTNER_REQUEST_ACCEPTED', 'PARTNER_REQUEST_REJECTED',
        'PARTNER_UNPAIRED', 'SUPPORT_REQUEST_CREATED', 'SOS_TRIGGERED',
        'LOGOUT', 'PASSWORD_CHANGED', 'ACCOUNT_LOCKED'
    ));

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN (
        'NEW_MESSAGE', 'SOS_ALERT', 'PARTNER_RELAPSE', 'MISSED_CHECKIN',
        'ACHIEVEMENT_UNLOCKED', 'PARTNER_ACHIEVEMENT', 'DAILY_CHECKIN_REMINDER', 'SUPPORT_REQUEST'
    ));
