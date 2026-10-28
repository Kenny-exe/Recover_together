-- V6__add_user_timezone.sql
-- Per-user IANA timezone so check-ins, streaks, reminder windows and missed
-- check-ins are evaluated on the user's local day rather than the server's.
-- Existing rows default to UTC until the user (or mobile client) sets one.

ALTER TABLE users ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';
