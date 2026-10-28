# AGENTS.md — RecoverTogether backend

Spring Boot 3.5.14, Java 21, single-module Maven project. PostgreSQL + JWT auth.

## Build, run, test

- Use the Maven wrapper, not system `mvn`:
  - Linux/macOS: `./mvnw <goal>`
  - Windows: `.\mvnw.cmd <goal>`
- Run dev server: `./mvnw spring-boot:run`
- Run tests: `./mvnw test`
- Java 21 is required (`java.version=21` in `pom.xml`).

## Required services / env

Tests are `@SpringBootTest` and boot the real context — they are **not** isolated:
- A live PostgreSQL database is required at
  `jdbc:postgresql://localhost:5432/recovertogether`.
- Database credentials are provided through the local development environment
  and must not be committed to the repository.
- `application.properties` resolves the datasource from `${DB_URL}`,
  `${DB_USERNAME}`, `${DB_PASSWORD}` with local dev fallbacks; deployments
  should inject these environment variables explicitly.
- `jwt.secret=${JWT_SECRET}` is mandatory at runtime. The app will fail to
  start if `JWT_SECRET` is unset.
- Tests override the JWT secret through
  `src/test/resources/application.properties`, so `JWT_SECRET` is not needed
  for `mvnw test`.

## Schema

- Version-controlled Flyway database migrations under `src/main/resources/db/migration/`.
- `spring.flyway.baseline-on-migrate=true` and `spring.flyway.baseline-version=1` protect existing databases.
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate validates entity mappings against the database schema at startup without performing automatic DDL changes.
- The PostgreSQL database must be reachable for migrations and schema validation.
- Entity changes require a new migration (current head:
  `V9__add_message_conversation_indexes.sql`); enum-typed columns carry
  CHECK constraints that must be kept in sync with their `enums/` values (see
  V2/V3 for the pattern).
- Databases created by Hibernate before Flyway was enabled were baselined at
  V1, so V1's `ON DELETE CASCADE` / `SET NULL` policies were missing on their
  generated foreign keys. V4 drops those constraints dynamically by table and
  recreates them with stable names and the V1 policies; keep this drift in
  mind when adding user-owned tables (a new FK must cascade or account
  deletion will fail with 409 Conflict).

## Privacy invariants (mobile)

- Registration binds only `RegisterRequest` (name/email/password/timezone);
  profile updates bind only `UpdateProfileRequest` (name/email/timezone) —
  user/entity fields such as lockout or token-invalidation state are never
  client-bindable.
- `UserResponse` and `DeviceResponse` never expose secrets (password hashes,
  security fields, raw push tokens — devices expose a masked preview only).
- Partner check-in views use `PartnerCheckInResponse` (id/date/status/created),
  stripping the private note.
- User discovery is exact-email search only (`GET /users/search?email=`); there
  is no directory dump endpoint.
- Account deletion is self-service: `DELETE /users/me` (or `DELETE /users/{id}`
  on own id). Both require a JSON body with the caller's current `password`
  (`DeleteAccountRequest`); deletion is irreversible, so a valid access token
  alone is not enough. Wrong passwords return 400 and are audited as
  `ACCOUNT_DELETION_DENIED`; deletion shares the login lockout counters
  (5 failures → 15 min, locked accounts get 429). DB-level
  `ON DELETE CASCADE` removes the user's owned rows; audit logs are retained
  intentionally.

## Auth

- JWT (JJWT 0.12.5) with `/auth/login` issuing tokens and `/users/register` for signup.
- `/auth/login` and `/users/register` are intended to be public endpoints.
- Authentication and endpoint authorization are enforced by Spring Security
  according to `SecurityConfig`.
- `JwtAuthenticationFilter` extracts and validates Bearer tokens when a request
  provides an Authorization header.
- Passwords are BCrypt-encoded (`PasswordEncoder` bean in `SecurityConfig`).
- Login is protected by account lockout (5 failed attempts → 15 min lock).
- Emails are normalized (trim + lowercase) on registration, login, and password
  reset, so casing or padding never blocks an otherwise valid login.
- `/auth/refresh` rotates refresh tokens; `/auth/logout` revokes the presented
  refresh token. Password change invalidates prior tokens and revokes refresh
  tokens of the user.

## Password reset

- Public endpoints: `POST /auth/forgot-password` (`email`) and
  `POST /auth/reset-password` (`token`, `newPassword`).
- Both responses are enumeration-safe: unknown and known emails receive the
  same generic message. Requests are throttled per user (60s cooldown, max 3
  per hour) without revealing whether the account exists.
- Codes are 256-bit URL-safe random values, valid for 30 minutes, stored only
  as SHA-256 hashes in `password_reset_tokens`, and consumed exactly once via
  an atomic update. Only the most recently issued code is valid.
- A successful reset updates the password, clears failed-attempt/lockout state,
  invalidates access tokens (`tokensInvalidatedBefore`), revokes all refresh
  tokens, and writes a `PASSWORD_RESET` audit entry.
- `MailService`/`SmtpMailService`: SMTP is used when `spring.mail.host` is set;
  otherwise delivery is a log-only no-op. Configure `app.mail.from`
  (`MAIL_FROM`) and optionally `app.mail.log-reset-codes=true`
  (`MAIL_LOG_RESET_CODES`) for local testing only — never in production.
  Mail failures are swallowed so they cannot break the reset workflow.

## Mobile push notifications

- Mobile clients register their FCM token at `POST /users/me/devices`
  (platform `ANDROID`/`IOS`, optional device name); list and remove via
  `GET /users/me/devices` and `DELETE /users/me/devices/{id}`.
- Device tokens are stored in `user_devices` (unique per token, FK cascade on
  user delete). A token registered by a new owner replaces the old binding
  (app reinstall handoff).
- `NotificationService.createNotification` fans out through
  `PushNotificationService` (`FcmPushService`, Firebase Admin SDK) — pushes are
  best-effort and never break the calling workflow.
- Push is disabled (log-only no-op) unless `fcm.credentials-path` points to a
  Firebase service-account JSON. It must stay a non-secret path — never commit
  the service-account file.
- Devices go stale on uninstall: sending returns `UNREGISTERED` and the device
  is deactivated automatically.
- Push payloads carry string data for deep linking: `type` (NotificationType),
  `screen` (`NotificationService.screenFor`), and context such as `partnerId`
  for `NEW_MESSAGE`. Android channel `recovertogether_default`, APNS sound on.
- Delivery preferences live at `GET`/`PUT /notifications/preferences`
  (`NotificationPreferenceController`): a global push switch, optional quiet
  hours (`HH:mm`, evaluated on the user's local clock), and per-type push
  toggles. A missing `notification_preferences` row means enabled, so new
  types default on. Updates are patch-style (null keeps the current value,
  blank quiet-hours time clears it); enabling quiet hours requires both times
  and `start != end`.
- Preferences only gate FCM push. In-app notifications and the SSE stream are
  always delivered, so the inbox stays complete. Quiet hours support overnight
  windows (start inclusive, end exclusive). `SOS_ALERT` bypasses the global
  switch, per-type toggles and quiet hours, and cannot be disabled (400).
- Preference lookup failures never block notification creation; push falls
  back to the previous "deliver" behaviour and the in-app row is still saved.

## Realtime updates (SSE)

- `GET /events` (`text/event-stream`, JWT-authenticated) opens a foreground
  stream per user; connections are tracked in `RealtimeEventService`.
- Events: `connected` `{user}`, `message` (`ChatMessageResponse`), `notification`
  (`NotificationResponse`). Chat sends also derive a `NEW_MESSAGE` notification.
- Server heartbeats every 25s; emitters time out after 30 min so mobile
  clients should reconnect with backoff. Delivery is best-effort and never
  breaks the primary workflow.
- Both SSE and FCM fire from `NotificationService.createNotification`; SSE
  covers the foreground app, FCM covers backgrounded/killed apps.
- `SecurityConfig` permits `ASYNC` and `ERROR` dispatcher types so the async
  completion of the SSE stream is not re-authorized. `JwtAuthenticationFilter`
  (a `OncePerRequestFilter`) skips ASYNC dispatches by default.

## Chat messages

- `GET /messages/conversation/{partnerId}?limit=&beforeId=` pages the thread at
  the database level: one cursor query on `id` (strictly older than
  `beforeId`, which is exclusive) fetched newest-window-first and reversed, so
  responses stay chronological. `limit` defaults to 50 and is clamped to
  1..200 (`limit < 1` → 400); `beforeId` is the smallest id of the previous
  page when paging backwards. `MessageRepository.findConversationPage` is
  backed by V9's composite `(sender_id, receiver_id, id)` indexes in both
  directions — never load the whole thread into memory again.
- Opening the thread marks the reader's unread messages from that partner
  read with one bulk update (`markReceivedFromSenderAsRead`, issued before the
  page fetch inside a `@Transactional` service method).
  `GET /messages/unread-count` counts unread received messages; denied or
  rejected conversation requests never mark anything read.
- Sends are partner-only (403 otherwise), trimmed, and capped at 1000 chars;
  each send persists, derives a `NEW_MESSAGE` notification, and publishes SSE.

## Timezone / local-day semantics

- Every user has an IANA `timezone` (`users.timezone`, default `UTC`,
  max 64 chars). Registration and profile updates accept an optional
  `timezone`; omitted values keep the existing/default zone. Invalid IANA ids
  are rejected with 400.
- `UserTimeZone` is the single source of truth: reads fall back to UTC for
  missing or invalid stored values so schedulers never crash; writes use
  `normalize`/`normalizeOrDefault`.
- Check-ins, streaks, weekly reports, partner `checkedInToday`/`daysSince`, and
  reminder windows all use the relevant user's local day. A check-in belongs
  to the local date at submission time. Partner status is measured on the
  partner's clock, not the viewer's.
- Notification timestamps remain server-local; the missed-check-in duplicate
  guard converts the user's local day start into server time.

## Scheduling

- `BackendApplication` is annotated with `@EnableScheduling`.
- `MissedCheckInScheduler.checkMissedCheckIns()` runs every 15 minutes server
  time. It converts the instant into each user's timezone and only evaluates
  users whose local hour is 21 (covers :00/:15/:30/:45 offsets).
- It evaluates accepted partner relationships in both directions and creates
  missed-check-in notifications when a user has not checked in on their local
  day. Duplicate protection is per user-local day via notification
  `createdAt`, so repeated runs cannot re-alert and a new day is never
  suppressed by an old alert.
- `RandomCheckInReminderScheduler.runReminderCheck()` also runs every 15
  minutes around the clock; `CheckInReminderService.processReminders()`
  generates/evaluates each user's reminder in their own local time and
  `ReminderWindow` (MORNING/AFTERNOON/EVENING/ANYTIME). One schedule per user
  per local date (`uk_reminder_schedule_user_date`); changing the window after
  today's reminder was sent takes effect on a future day.
- Scheduler behavior is verified with fixed `ZonedDateTime`/local times in
  tests, including both relationship directions, per-user zones, 21:00 gating,
  and per-local-day duplicate cutoffs.

## Code layout

- `controller/` — REST controllers (request mapping prefixes per class).
- `service/` — business logic including `User`, `Jwt`, `DailyCheckIn`,
  `PartnerRequest`, `PartnerCheckIn`, `Notification`, `Message`, `Achievement`,
  `CheckInReminderService`, `RandomCheckInReminderScheduler`,
  `MissedCheckInScheduler`, `Dashboard`, `Support`, `Device`, `UserTimeZone`,
  `RealtimeEventService` (SSE), `PushNotificationService`/`FcmPushService`,
  `NotificationPreferenceService`, `PasswordResetService`,
  `MailService`/`SmtpMailService`.
- `repository/` — Spring Data JPA repositories.
- `entity/` — JPA entities (source of truth for the schema).
- `dto/` — request/response DTOs.
- `security/` — `JwtAuthenticationFilter`.
- `config/` — `SecurityConfig`; `exception/` — `GlobalExceptionHandler`;
  `enums/` — domain enums.

## Notes

- `.gitignore` ignores `target/` and `HELP.md` (Spring Boot's template).
