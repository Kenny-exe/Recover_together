-- V4__enforce_cascading_user_foreign_keys.sql
-- Repair foreign keys on databases that were created by Hibernate before
-- Flyway was enabled (baselined at V1, so V1's referential actions were never
-- applied). Hibernate generates its own constraint names with the default
-- NO ACTION behaviour, which made self-service account deletion fail with a
-- 409 Conflict (e.g. refresh_tokens.user_id).
--
-- Constraint names differ between Hibernate- and Flyway-created schemas, so
-- drop every foreign key on the affected tables dynamically and recreate them
-- with stable names and the V1 policies. This is idempotent: it also runs
-- cleanly on databases that already have the correct constraints.

DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT c.conname, t.relname AS table_name
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE c.contype = 'f'
          AND n.nspname = current_schema()
          AND c.confrelid IN (
              'users'::regclass,
              'recovery_plans'::regclass,
              'support_plans'::regclass
          )
          AND t.relname IN (
              'refresh_tokens',
              'check_in_reminder_schedules',
              'daily_checkins',
              'partner_requests',
              'notifications',
              'messages',
              'achievements',
              'recovery_plans',
              'support_plans',
              'support_requests',
              'user_devices',
              'recovery_plan_contacts',
              'recovery_plan_coping_strategies',
              'recovery_plan_triggers',
              'support_plan_actions'
          )
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', fk.table_name, fk.conname);
    END LOOP;
END $$;

-- User-owned rows are removed with the account.
ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE check_in_reminder_schedules
    ADD CONSTRAINT fk_check_in_reminder_schedules_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE daily_checkins
    ADD CONSTRAINT fk_daily_checkins_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE partner_requests
    ADD CONSTRAINT fk_partner_requests_sender
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE partner_requests
    ADD CONSTRAINT fk_partner_requests_receiver
    FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_receiver
    FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE messages
    ADD CONSTRAINT fk_messages_sender
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE messages
    ADD CONSTRAINT fk_messages_receiver
    FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE achievements
    ADD CONSTRAINT fk_achievements_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE recovery_plans
    ADD CONSTRAINT fk_recovery_plans_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE support_plans
    ADD CONSTRAINT fk_support_plans_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE support_requests
    ADD CONSTRAINT fk_support_requests_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE support_requests
    ADD CONSTRAINT fk_support_requests_partner
    FOREIGN KEY (partner_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE user_devices
    ADD CONSTRAINT fk_user_devices_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Plan children follow their owning plan.
ALTER TABLE recovery_plan_contacts
    ADD CONSTRAINT fk_recovery_plan_contacts_plan
    FOREIGN KEY (plan_id) REFERENCES recovery_plans(id) ON DELETE CASCADE;

ALTER TABLE recovery_plan_coping_strategies
    ADD CONSTRAINT fk_recovery_plan_coping_strategies_plan
    FOREIGN KEY (plan_id) REFERENCES recovery_plans(id) ON DELETE CASCADE;

ALTER TABLE recovery_plan_triggers
    ADD CONSTRAINT fk_recovery_plan_triggers_plan
    FOREIGN KEY (plan_id) REFERENCES recovery_plans(id) ON DELETE CASCADE;

ALTER TABLE support_plan_actions
    ADD CONSTRAINT fk_support_plan_actions_plan
    FOREIGN KEY (plan_id) REFERENCES support_plans(id) ON DELETE CASCADE;
