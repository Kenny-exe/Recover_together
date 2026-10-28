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
- Entity changes require a new migration (current head: `V3__add_user_devices.sql`);
  enum-typed columns carry CHECK constraints that must be kept in sync with
  their `enums/` values (see V2/V3 for the pattern).

## Privacy invariants (mobile)

- Registration binds only `RegisterRequest` (name/email/password); profile
  updates bind only `UpdateProfileRequest` (name/email) — user/entity fields
  such as lockout or token-invalidation state are never client-bindable.
- `UserResponse` and `DeviceResponse` never expose secrets (password hashes,
  security fields, raw push tokens — devices expose a masked preview only).
- Partner check-in views use `PartnerCheckInResponse` (id/date/status/created),
  stripping the private note.
- User discovery is exact-email search only (`GET /users/search?email=`); there
  is no directory dump endpoint.
- Account deletion is self-service: `DELETE /users/me` (or `DELETE /users/{id}`
  on own id). DB-level `ON DELETE CASCADE` removes the user's owned rows;
  audit logs are retained intentionally.

## Auth

- JWT (JJWT 0.12.5) with `/auth/login` issuing tokens and `/users/register` for signup.
- `/auth/login` and `/users/register` are intended to be public endpoints.
- Authentication and endpoint authorization are enforced by Spring Security
  according to `SecurityConfig`.
- `JwtAuthenticationFilter` extracts and validates Bearer tokens when a request
  provides an Authorization header.
- Passwords are BCrypt-encoded (`PasswordEncoder` bean in `SecurityConfig`).
- Login is protected by account lockout (5 failed attempts → 15 min lock).
- `/auth/refresh` rotates refresh tokens; `/auth/logout` revokes the presented
  refresh token. Password change invalidates prior tokens and revokes refresh
  tokens of the user.

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

## Scheduling

- `BackendApplication` is annotated with `@EnableScheduling`.
- `MissedCheckInScheduler.checkMissedCheckIns()` runs daily at 21:00 server time.
- It evaluates accepted partner relationships and creates missed-check-in
  notifications when a partner has not completed their check-in.
- Scheduler behavior should be verified through testing, especially for both
  directions of a partner relationship.

## Code layout

- `controller/` — REST controllers (request mapping prefixes per class).
- `service/` — business logic including `User`, `Jwt`, `DailyCheckIn`,
  `PartnerRequest`, `PartnerCheckIn`, `Notification`, `Message`, `Achievement`,
  `MissedCheckInScheduler`, `Dashboard`, `Support`, `Device`,
  `PushNotificationService`/`FcmPushService`.
- `repository/` — Spring Data JPA repositories.
- `entity/` — JPA entities (source of truth for the schema).
- `dto/` — request/response DTOs.
- `security/` — `JwtAuthenticationFilter`.
- `config/` — `SecurityConfig`; `exception/` — `GlobalExceptionHandler`;
  `enums/` — domain enums.

## Notes

- `.gitignore` ignores `target/` and `HELP.md` (Spring Boot's template).
