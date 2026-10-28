# RecoverTogether API — mobile client contract

> **Status: frozen for the first mobile release.** Snapshot taken 2026-10-28 from a dev boot of backend `main` (commit `8fe57bf`; test suite 282/282 green). Generated from the live springdoc spec (`/v3/api-docs`, 52 paths / 45 schemas) and reviewed against the controllers; the hand-written sections below carry the semantics the spec cannot express.

The mobile app may build against everything in this document. Evolution rule: clients must tolerate unknown JSON fields (additive changes arrive without notice), but an existing route, field, or semantic is never removed or renamed without a coordinated contract bump.

## Conventions

### Transport

- Every endpoint speaks JSON (UTF-8) with `Content-Type: application/json`, except `GET /events` (Server-Sent Events) and `GET /health` (public liveness probe).
- Authenticated requests carry `Authorization: Bearer <accessToken>`.
- IDs are integers (monotonic; cursor pagination relies on that).
- Timestamps are ISO-8601 **without a timezone offset**, in server wall-clock time (e.g. `2026-10-28T20:49:45.123`). Treat them as opaque for ordering and display; day-based logic (streaks, check-in days, quiet hours) is computed server-side on the user's profile timezone.

### Auth & tokens

- `POST /users/register` binds `name`, `email`, `password`, optional `timezone`. Emails are normalized (trim + lowercase) server-side on register, login, and password reset — the client may send them as typed.
- `POST /auth/login` returns `LoginResponse`: `token` (access JWT), `refreshToken`, `tokenType: "Bearer"`.
- Access tokens live **15 minutes** and are opaque to the client (do not parse them).
- `POST /auth/refresh` takes the current refresh token and returns a fresh `LoginResponse`; the presented refresh token is **rotated** (single use). Store only the newest pair.
- On any 401 from a regular endpoint: try one silent refresh, replay the request once; if the refresh itself fails, drop the session and go to login. A password change or password reset invalidates all of the user's tokens immediately, so this path also covers "signed out elsewhere".
- `POST /auth/logout` requires an access token in the header **and** the refresh token in the body; it revokes that refresh token.
- Login lockout: 5 failed passwords for one account lock it for 15 minutes. Attempts while locked return **401 `Invalid email or password`** — indistinguishable from a wrong password, by design. A successful login clears the counter; after the lockout window passes, the next attempt starts fresh.
- The public auth endpoints (`/auth/login`, `/auth/refresh`, `/auth/forgot-password`, `/auth/reset-password`, `/users/register`) are IP-rate-limited to 30 requests/minute per client IP + path (fixed 60-second window). Over the limit: **429** with a `Retry-After: <seconds>` header; back off instead of retrying immediately.

### Errors

Every error body has the same envelope:

```json
{ "timestamp": "2026-10-28T20:49:45.123", "status": 400, "message": "..." }
```

- **400** — validation: the `message` is the **first** field error only (e.g. `Password must be at least 8 characters`); there is no per-field map. Route-specific 400s: `limit < 1` on the conversation endpoint, invalid IANA timezone, invalid quiet-hours configuration, `SOS_ALERT` preference disable attempt.
- **400** `Malformed JSON request` — body failed to parse.
- **401** `Invalid email or password` (login, including while locked) / missing, invalid, or expired bearer token elsewhere.
- **403** `Access denied` — authenticated but not permitted (e.g. messaging a non-partner).
- **404** `Resource not found`, or a route-specific reason (e.g. `Notification not found`).
- **409** `Database constraint violation. Operation could not be completed.` — unique-constraint races.
- **429** — IP rate limit (with `Retry-After`) or a locked account attempting account deletion (no `Retry-After`).
- **500** `An unexpected internal error occurred` — retry reads with backoff; do not blindly retry writes.
- Error responses are deliberately not enumerated per-route in the OpenAPI spec; this section is the reference.

### Pagination (cursor, `beforeId` exclusive)

Two list endpoints page backwards from newest:

| Endpoint | `limit` default | `limit` handling | `beforeId` | Order returned |
| --- | --- | --- | --- | --- |
| `GET /messages/conversation/{partnerId}` | 50 | 400 if `< 1`, clamped to 200 | exclusive: items with `id < beforeId` | chronological (oldest → newest) |
| `GET /notifications` | 20 | silently clamped to 1..100 | exclusive: items with `id < beforeId` | newest-first |

Paging recipe: request page 1 with no `beforeId`; the next page uses `beforeId` = the **smallest `id`** from the previous page; stop when a page returns fewer items than the limit (or zero). Never page by offset.

### Local-day semantics

- Every user has an IANA `timezone` (default `UTC`). It is optional on register and profile update; invalid IANA ids are rejected with 400; omitting it keeps the current value.
- A check-in belongs to the **user's local date** at submission time. Streaks, weekly reports, partner `checkedInToday`/`daysSince`, and reminder windows are all evaluated on the relevant user's local clock — the partner's status is measured on the *partner's* clock, not the viewer's.
- Clients never send local dates or zone offsets; the server derives them from the profile timezone.

### Realtime (SSE)

- `GET /events` (JWT) opens a `text/event-stream`. Keep it open while the app is foregrounded; rely on FCM push while backgrounded.
- Named events: `connected` (data `{"user":"<email>"}`), `message` (data: `ChatMessageResponse`), `notification` (data: `NotificationResponse`).
- Keepalive: an SSE comment line (`: keepalive`) every 25 s — `EventSource` handles it automatically; custom clients must ignore comment lines.
- Connections are capped at 30 minutes server-side; reconnect with exponential backoff and re-sync state from the REST endpoints (SSE delivery is best-effort, not a replay log).
- Deduplicate by entity `id` — a push and an SSE event can carry the same notification.

### Push notifications (FCM)

- Register the device token at `POST /users/me/devices` (`platform`: `ANDROID`|`IOS`, optional `deviceName`); re-registering a token transfers it to the new account (app reinstall handoff). List/remove via `GET`/`DELETE /users/me/devices[/{deviceId}]`; token previews are masked in responses.
- Pushes are gated by `GET`/`PUT /notifications/preferences`: a global push switch, optional quiet hours (`HH:mm`, both times required, `start != end`, evaluated on the user's local clock), and per-type toggles. Missing preferences mean "everything on"; updates are patch-style (null keeps the current value).
- `SOS_ALERT` bypasses every preference and cannot be disabled (400).
- Push data payload (all string values): `type` = `NotificationType`, `screen` = route hint (table below), plus context such as `partnerId` for `NEW_MESSAGE`. Android channel `recovertogether_default`; the APNs payload carries a sound.

| `type` | `screen` |
| --- | --- |
| NEW_MESSAGE | chat |
| SOS_ALERT | sos |
| PARTNER_RELAPSE | partner |
| MISSED_CHECKIN | checkin |
| DAILY_CHECKIN_REMINDER | checkin |
| ACHIEVEMENT_UNLOCKED | achievements |
| PARTNER_ACHIEVEMENT | achievements |
| SUPPORT_REQUEST | support |

### Domain rules worth knowing

- Chat messages are partner-only (403 otherwise), trimmed, and capped at 1000 characters. Opening the thread marks the partner's messages read in one bulk update; denied or rejected conversation requests never mark anything read.
- `GET /users/search?email=` is exact-email lookup only — there is no directory dump.
- Account deletion (`DELETE /users/me`, body `{"password": "..."}`) is irreversible and requires the current password; a wrong password is a 400, is audited, and counts toward the login lockout (a locked account gets 429).
- Partner check-in views (`PartnerCheckInResponse`) never include the private note.

### Enums

| Enum | Values |
| --- | --- |
| NotificationType | NEW_MESSAGE, SOS_ALERT, PARTNER_RELAPSE, MISSED_CHECKIN, ACHIEVEMENT_UNLOCKED, PARTNER_ACHIEVEMENT, DAILY_CHECKIN_REMINDER, SUPPORT_REQUEST |
| CheckInStatus | SUCCESS, RELAPSE, STRUGGLING, STRONG_URGE, NEED_SUPPORT |
| ReminderWindow | MORNING (08:00–12:00), AFTERNOON (12:00–17:00), EVENING (17:00–22:00), ANYTIME (08:00–22:00) — user-local clock |
| PartnerRequestStatus | PENDING, ACCEPTED, REJECTED |
| DevicePlatform | ANDROID, IOS |
| SupportAction | SEND_MESSAGE, CALL_ME, ENCOURAGE_ME, REMIND_ME_OF_MY_GOAL, REMIND_ME_OF_MY_COPING_PLAN, ENCOURAGE_ME_TO_CONTACT_PROFESSIONAL, GIVE_ME_SPACE |
| ResourceCategory | CRISIS, COUNSELING, SUPPORT_GROUP, HEALTHCARE, HOTLINE, OTHER |

## Endpoint inventory

<!-- BEGIN GENERATED FROM /v3/api-docs - do not hand-edit inside this region -->
### achievements

**GET /achievements** - auth: JWT
  - returns: `AchievementResponse[]`

### auth

**POST /auth/forgot-password** - auth: public
  - body: `ForgotPasswordRequest`
  - returns: JSON object (map of string)

**POST /auth/login** - auth: public
  - body: `LoginRequest`
  - returns: `LoginResponse`

**POST /auth/logout** - auth: JWT
  - body: `LogoutRequest`
  - returns: JSON object (map of string)

**POST /auth/refresh** - auth: public
  - body: `RefreshTokenRequest`
  - returns: `LoginResponse`

**POST /auth/reset-password** - auth: public
  - body: `ResetPasswordRequest`
  - returns: JSON object (map of string)

### checkin

**POST /checkin** - auth: JWT
  - body: `CheckInRequest`
  - returns: `MessageResponse`

**GET /checkin/history** - auth: JWT
  - `limit` (query, integer (int32, default 30), required: no)
  - returns: `CheckInResponse[]`

**GET /checkin/latest** - auth: JWT
  - returns: `CheckInResponse`

**GET /checkin/reminder-window** - auth: JWT
  - returns: `ReminderWindowResponse`

**PUT /checkin/reminder-window** - auth: JWT
  - body: `ReminderWindowRequest`
  - returns: `ReminderWindowResponse`

**GET /checkin/stats** - auth: JWT
  - returns: `CheckInStatsResponse`

**GET /checkin/streak** - auth: JWT
  - returns: `StreakResponse`

**GET /checkin/weekly-report** - auth: JWT
  - returns: `WeeklyReportResponse`

### dashboard

**GET /dashboard** - auth: JWT
  - returns: `DashboardResponse`

### events

**GET /events** - auth: JWT

### health

**GET /health** - auth: public
  - returns: JSON object (map of string)

### messages

**GET /messages/conversation/{partnerId}** - auth: JWT
  - `partnerId` (path, integer (int64), required: yes)
  - `limit` (query, integer (int32, default 50), required: no)
  - `beforeId` (query, integer (int64), required: no)
  - returns: `ChatMessageResponse[]`

**POST /messages/send/{partnerId}** - auth: JWT
  - `partnerId` (path, integer (int64), required: yes)
  - body: `MessageRequest`
  - returns: `MessageResponse`

**GET /messages/unread-count** - auth: JWT
  - returns: `UnreadCountResponse`

### notifications

**GET /notifications** - auth: JWT
  - `limit` (query, integer (int32, default 20), required: no)
  - `beforeId` (query, integer (int64), required: no)
  - returns: `NotificationResponse[]`

**DELETE /notifications/all** - auth: JWT
  - returns: `MessageResponse`

**GET /notifications/preferences** - auth: JWT
  - returns: `NotificationPreferencesResponse`

**PUT /notifications/preferences** - auth: JWT
  - body: `NotificationPreferencesRequest`
  - returns: `NotificationPreferencesResponse`

**PUT /notifications/read-all** - auth: JWT
  - returns: `MessageResponse`

**PUT /notifications/read/{notificationId}** - auth: JWT
  - `notificationId` (path, integer (int64), required: yes)
  - returns: `MessageResponse`

**GET /notifications/unread-count** - auth: JWT
  - returns: `UnreadCountResponse`

**DELETE /notifications/{id}** - auth: JWT
  - `id` (path, integer (int64), required: yes)
  - returns: `MessageResponse`

### partner

**POST /partner/accept/{requestId}** - auth: JWT
  - `requestId` (path, integer (int64), required: yes)
  - returns: `MessageResponse`

**GET /partner/checkin/history** - auth: JWT
  - `limit` (query, integer (int32, default 30), required: no)
  - returns: `PartnerCheckInResponse[]`

**GET /partner/checkin/latest** - auth: JWT
  - returns: `PartnerCheckInResponse`

**GET /partner/checkin/status** - auth: JWT
  - returns: `PartnerStatusResponse`

**GET /partner/current** - auth: JWT
  - returns: `PartnerResponse`

**GET /partner/last-seen** - auth: JWT
  - returns: `LastSeenResponse`

**POST /partner/reject/{requestId}** - auth: JWT
  - `requestId` (path, integer (int64), required: yes)
  - returns: `MessageResponse`

**POST /partner/request/{receiverId}** - auth: JWT
  - `receiverId` (path, integer (int64), required: yes)
  - returns: `MessageResponse`

**GET /partner/requests** - auth: JWT
  - returns: `PartnerRequestResponse[]`

**GET /partner/sent** - auth: JWT
  - returns: `SentRequestResponse[]`

**GET /partner/summary** - auth: JWT
  - returns: `PartnerSummaryResponse`

**POST /partner/unpair** - auth: JWT
  - returns: `MessageResponse`

### recovery-plan

**GET /recovery-plan** - auth: JWT
  - returns: `RecoveryPlanResponse`

**POST /recovery-plan** - auth: JWT
  - body: `RecoveryPlanRequest`
  - also HTTP 201: `RecoveryPlanResponse`

**PUT /recovery-plan** - auth: JWT
  - body: `RecoveryPlanRequest`
  - returns: `RecoveryPlanResponse`

**DELETE /recovery-plan** - auth: JWT
  - returns: `MessageResponse`

### resources

**GET /resources** - auth: JWT
  - `category` (query, string, required: no)
  - returns: `RecoveryResourceResponse[]`

**GET /resources/{id}** - auth: JWT
  - `id` (path, integer (int64), required: yes)
  - returns: `RecoveryResourceResponse`

### support

**POST /support/request** - auth: JWT
  - body: `SupportRequestDto`
  - returns: `SupportRequestResponse`

**POST /support/sos** - auth: JWT
  - returns: `MessageResponse`

### support-plan

**GET /support-plan** - auth: JWT
  - returns: `SupportPlanResponse`

**PUT /support-plan** - auth: JWT
  - body: `SupportPlanRequest`
  - returns: `SupportPlanResponse`

**GET /support-plan/partner** - auth: JWT
  - returns: `PartnerSupportResponse`

### users

**POST /users/change-password** - auth: JWT
  - body: `ChangePasswordRequest`
  - returns: JSON object (map of string)

**GET /users/me** - auth: JWT
  - returns: `UserResponse`

**PUT /users/me** - auth: JWT
  - body: `UpdateProfileRequest`
  - returns: `UserResponse`

**DELETE /users/me** - auth: JWT
  - body: `DeleteAccountRequest`
  - returns: JSON object (map of string)

**GET /users/me/devices** - auth: JWT
  - returns: `DeviceResponse[]`

**POST /users/me/devices** - auth: JWT
  - body: `DeviceRegistrationRequest`
  - returns: `DeviceResponse`

**DELETE /users/me/devices/{deviceId}** - auth: JWT
  - `deviceId` (path, integer (int64), required: yes)
  - returns: JSON object (map of string)

**POST /users/register** - auth: public
  - body: `RegisterRequest`
  - returns: `UserResponse`

**GET /users/search** - auth: JWT
  - `email` (query, string, required: yes)
  - returns: `UserResponse`

**GET /users/{id}** - auth: JWT
  - `id` (path, integer (int64), required: yes)
  - returns: `UserResponse`

**PUT /users/{id}** - auth: JWT
  - `id` (path, integer (int64), required: yes)
  - body: `UpdateProfileRequest`
  - returns: `UserResponse`

**DELETE /users/{id}** - auth: JWT
  - `id` (path, integer (int64), required: yes)
  - body: `DeleteAccountRequest`
  - returns: string

## Response schemas

#### `AchievementResponse`

| Field | Type | Required |
| --- | --- | --- |
| `title` | string | no |
| `earnedAt` | string (date-time) | no |


#### `ChangePasswordRequest`

| Field | Type | Required |
| --- | --- | --- |
| `currentPassword` | string (min 1) | yes |
| `newPassword` | string (min 8; max 128) | yes |


#### `ChatMessageResponse`

| Field | Type | Required |
| --- | --- | --- |
| `id` | integer | no |
| `senderName` | string | no |
| `content` | string | no |
| `createdAt` | string (date-time) | no |


#### `CheckInRequest`

| Field | Type | Required |
| --- | --- | --- |
| `status` | string (enum: SUCCESS | RELAPSE | STRUGGLING | STRONG_URGE | NEED_SUPPORT) | yes |
| `note` | string | no |


#### `CheckInResponse`

| Field | Type | Required |
| --- | --- | --- |
| `id` | integer | no |
| `date` | string (date) | no |
| `status` | string (enum: SUCCESS | RELAPSE | STRUGGLING | STRONG_URGE | NEED_SUPPORT) | no |
| `note` | string | no |
| `createdAt` | string (date-time) | no |


#### `CheckInStatsResponse`

| Field | Type | Required |
| --- | --- | --- |
| `totalCheckIns` | integer | no |
| `successCount` | integer | no |
| `relapseCount` | integer | no |
| `successRate` | number | no |
| `currentStreak` | integer | no |
| `bestStreak` | integer | no |


#### `DashboardResponse`

| Field | Type | Required |
| --- | --- | --- |
| `currentStreak` | integer | no |
| `bestStreak` | integer | no |
| `totalCheckIns` | integer | no |
| `successCount` | integer | no |
| `relapseCount` | integer | no |
| `successRate` | number | no |
| `unreadMessages` | integer | no |
| `partnerName` | string | no |
| `partnerCurrentStreak` | integer | no |
| `partnerBestStreak` | integer | no |
| `partnerLastSeen` | string (date-time) | no |
| `partnerCheckedInToday` | boolean | no |
| `daysSinceLastCheckIn` | integer | no |
| `achievementCount` | integer | no |


#### `DeleteAccountRequest`

| Field | Type | Required |
| --- | --- | --- |
| `password` | string (min 1) | yes |


#### `DeviceRegistrationRequest`

| Field | Type | Required |
| --- | --- | --- |
| `platform` | string (enum: ANDROID | IOS) | yes |
| `token` | string (min 0; max 4096) | yes |
| `deviceName` | string (min 0; max 100) | no |


#### `DeviceResponse`

| Field | Type | Required |
| --- | --- | --- |
| `id` | integer | no |
| `platform` | string (enum: ANDROID | IOS) | no |
| `deviceName` | string | no |
| `tokenPreview` | string | no |
| `active` | boolean | no |
| `createdAt` | string (date-time) | no |


#### `ForgotPasswordRequest`

| Field | Type | Required |
| --- | --- | --- |
| `email` | string (email; min 1) | yes |


#### `LastSeenResponse`

| Field | Type | Required |
| --- | --- | --- |
| `name` | string | no |
| `lastSeen` | string (date-time) | no |


#### `LoginRequest`

| Field | Type | Required |
| --- | --- | --- |
| `email` | string (email; min 1) | yes |
| `password` | string (min 1) | yes |


#### `LoginResponse`

| Field | Type | Required |
| --- | --- | --- |
| `token` | string | no |
| `refreshToken` | string | no |
| `tokenType` | string | no |


#### `LogoutRequest`

| Field | Type | Required |
| --- | --- | --- |
| `refreshToken` | string | no |


#### `MessageRequest`

| Field | Type | Required |
| --- | --- | --- |
| `content` | string (min 0; max 1000) | yes |


#### `MessageResponse`

| Field | Type | Required |
| --- | --- | --- |
| `message` | string | no |


#### `NotificationPreferencesRequest`

| Field | Type | Required |
| --- | --- | --- |
| `pushNotificationsEnabled` | boolean | no |
| `quietHoursEnabled` | boolean | no |
| `quietHoursStart` | string | no |
| `quietHoursEnd` | string | no |
| `typePreferences` | object (map of boolean; map of NotificationType name -> boolean) | no |


#### `NotificationPreferencesResponse`

| Field | Type | Required |
| --- | --- | --- |
| `pushNotificationsEnabled` | boolean | no |
| `quietHoursEnabled` | boolean | no |
| `quietHoursStart` | string | no |
| `quietHoursEnd` | string | no |
| `typePreferences` | object (map of boolean; map of NotificationType name -> boolean) | no |


#### `NotificationResponse`

| Field | Type | Required |
| --- | --- | --- |
| `id` | integer | no |
| `type` | string (enum: NEW_MESSAGE | SOS_ALERT | PARTNER_RELAPSE | MISSED_CHECKIN | ACHIEVEMENT_UNLOCKED | PARTNER_ACHIEVEMENT | DAILY_CHECKIN_REMINDER | SUPPORT_REQUEST) | no |
| `message` | string | no |
| `read` | boolean | no |
| `createdAt` | string (date-time) | no |


#### `PartnerCheckInResponse`

| Field | Type | Required |
| --- | --- | --- |
| `id` | integer | no |
| `date` | string (date) | no |
| `status` | string (enum: SUCCESS | RELAPSE | STRUGGLING | STRONG_URGE | NEED_SUPPORT) | no |
| `createdAt` | string (date-time) | no |


#### `PartnerRequestResponse`

| Field | Type | Required |
| --- | --- | --- |
| `id` | integer | no |
| `senderName` | string | no |
| `receiverName` | string | no |
| `status` | string (enum: PENDING | ACCEPTED | REJECTED) | no |
| `createdAt` | string (date-time) | no |


#### `PartnerResponse`

| Field | Type | Required |
| --- | --- | --- |
| `id` | integer | no |
| `name` | string | no |
| `email` | string | no |


#### `PartnerStatusResponse`

| Field | Type | Required |
| --- | --- | --- |
| `partnerCheckedInToday` | boolean | no |
| `daysSinceLastCheckIn` | integer | no |
| `lastCheckInDate` | string (date) | no |


#### `PartnerSummaryResponse`

| Field | Type | Required |
| --- | --- | --- |
| `name` | string | no |
| `email` | string | no |
| `currentStreak` | integer | no |
| `bestStreak` | integer | no |


#### `PartnerSupportResponse`

| Field | Type | Required |
| --- | --- | --- |
| `partnerName` | string | no |
| `supportActions` | string[] | no |
| `goal` | string | no |
| `copingStrategies` | string[] | no |


#### `RecoveryPlanRequest`

| Field | Type | Required |
| --- | --- | --- |
| `goal` | string (min 1) | yes |
| `copingStrategies` | string[] | no |
| `triggers` | string[] | no |
| `contacts` | string[] | no |


#### `RecoveryPlanResponse`

| Field | Type | Required |
| --- | --- | --- |
| `id` | integer | no |
| `goal` | string | no |
| `copingStrategies` | string[] | no |
| `triggers` | string[] | no |
| `contacts` | string[] | no |
| `createdAt` | string (date-time) | no |
| `updatedAt` | string (date-time) | no |


#### `RecoveryResourceResponse`

| Field | Type | Required |
| --- | --- | --- |
| `id` | integer | no |
| `name` | string | no |
| `description` | string | no |
| `category` | string (enum: CRISIS | COUNSELING | SUPPORT_GROUP | HEALTHCARE | HOTLINE | OTHER) | no |
| `contactNumber` | string | no |
| `websiteUrl` | string | no |
| `email` | string | no |
| `operatingHours` | string | no |
| `active` | boolean | no |


#### `RefreshTokenRequest`

| Field | Type | Required |
| --- | --- | --- |
| `refreshToken` | string (min 1) | yes |


#### `RegisterRequest`

| Field | Type | Required |
| --- | --- | --- |
| `name` | string (min 2; max 100) | yes |
| `email` | string (email; min 1) | yes |
| `password` | string (min 8; max 128) | yes |
| `timezone` | string (min 0; max 64) | no |


#### `ReminderWindowRequest`

| Field | Type | Required |
| --- | --- | --- |
| `window` | string (enum: MORNING | AFTERNOON | EVENING | ANYTIME) | yes |


#### `ReminderWindowResponse`

| Field | Type | Required |
| --- | --- | --- |
| `window` | string (enum: MORNING | AFTERNOON | EVENING | ANYTIME) | no |
| `startTime` | string | no |
| `endTime` | string | no |


#### `ResetPasswordRequest`

| Field | Type | Required |
| --- | --- | --- |
| `token` | string (min 1) | yes |
| `newPassword` | string (min 8; max 128) | yes |


#### `SentRequestResponse`

| Field | Type | Required |
| --- | --- | --- |
| `requestId` | integer | no |
| `receiverName` | string | no |


#### `StreakResponse`

| Field | Type | Required |
| --- | --- | --- |
| `currentStreak` | integer | no |
| `bestStreak` | integer | no |


#### `SupportPlanRequest`

| Field | Type | Required |
| --- | --- | --- |
| `supportActions` | string[] | yes |


#### `SupportPlanResponse`

| Field | Type | Required |
| --- | --- | --- |
| `supportActions` | string[] | no |
| `updatedAt` | string (date-time) | no |


#### `SupportRequestDto`

| Field | Type | Required |
| --- | --- | --- |
| `note` | string | no |


#### `SupportRequestResponse`

| Field | Type | Required |
| --- | --- | --- |
| `partnerNotified` | boolean | no |
| `message` | string | no |
| `fallbackResources` | string[] | no |


#### `UnreadCountResponse`

| Field | Type | Required |
| --- | --- | --- |
| `unreadCount` | integer | no |


#### `UpdateProfileRequest`

| Field | Type | Required |
| --- | --- | --- |
| `name` | string (min 2; max 100) | yes |
| `email` | string (email; min 1) | yes |
| `timezone` | string (min 0; max 64) | no |


#### `UserResponse`

| Field | Type | Required |
| --- | --- | --- |
| `id` | integer | no |
| `name` | string | no |
| `email` | string | no |
| `timezone` | string | no |
| `createdAt` | string (date-time) | no |


#### `WeeklyReportResponse`

| Field | Type | Required |
| --- | --- | --- |
| `successDays` | integer | no |
| `relapseDays` | integer | no |
| `successRate` | number | no |
| `currentStreak` | integer | no |
| `bestStreak` | integer | no |
<!-- END GENERATED -->
