-- V8__add_account_deletion_denied_audit.sql
-- Account deletion now requires the current password. Denied attempts are
-- audited separately from failed logins so the security trail distinguishes
-- a destructive-action attempt from a normal sign-in failure.

ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_action_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check
    CHECK (action IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'USER_REGISTERED', 'ACCOUNT_DELETED',
        'PARTNER_REQUEST_SENT', 'PARTNER_REQUEST_ACCEPTED', 'PARTNER_REQUEST_REJECTED',
        'PARTNER_UNPAIRED', 'SUPPORT_REQUEST_CREATED', 'SOS_TRIGGERED',
        'LOGOUT', 'PASSWORD_CHANGED', 'ACCOUNT_LOCKED', 'PROFILE_UPDATED',
        'PASSWORD_RESET', 'ACCOUNT_DELETION_DENIED'
    ));
