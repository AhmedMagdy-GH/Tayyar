# Module 16: Notifications

Module 16 adds a durable, user-owned in-application inbox. It does not add email, SMS, push,
WebSockets, Redis, Kafka, frontend UI, or a general-purpose notification-send API.

## Persistence model

Flyway migration `V14__create_notifications.sql` creates `notifications` with UUID identity,
immutable recipient ownership, a controlled notification type, an `IN_APP` channel, bounded title
and body fields, an optional controlled related-entity reference, `created_at`, nullable `read_at`,
and a unique bounded deduplication key. Database triggers prevent ownership/content mutation,
read-state reversal, and normal deletion. Targeted indexes support newest-first recipient inboxes
and unread inboxes.

The supported producer-backed types are:

- `ORDER_ACCEPTED`, `ORDER_REJECTED`, `ORDER_PREPARING`, `ORDER_READY_FOR_PICKUP`
- `ORDER_OUT_FOR_DELIVERY`, `ORDER_DELIVERED`
- `DELIVERY_ASSIGNED`
- `RESTAURANT_APPLICATION_APPROVED`, `RESTAURANT_APPLICATION_REJECTED`

No type was added without an implemented producer. `ORDER_PLACED`, `ORDER_CANCELLED`, and
`RESTAURANT_APPLICATION_SUBMITTED` are deferred. The current model has no explicit active flag on
restaurant memberships or branch staff assignments, so v1 does not silently invent the requested
active operational-recipient policy. Platform-wide admin fan-out is likewise deferred until an
explicit admin notification policy exists.

## API and ownership

Every authenticated, non-suspended user may use one role-neutral inbox:

- `GET /api/v1/notifications?page=0&size=20&read=false`
- `GET /api/v1/notifications/{notificationId}`
- `POST /api/v1/notifications/{notificationId}/read`
- `POST /api/v1/notifications/read-all`

`page` is limited to 0–10000 and `size` to 1–100. Only the optional `read=true|false` filter is
accepted; arbitrary sorts and repeated/unknown parameters are rejected. Results order by
`created_at DESC, id DESC`. Responses are DTOs with `Cache-Control: no-store`; recipient IDs and
deduplication keys are not returned.

All reads and mutations derive ownership from the authenticated session. Foreign and unknown
notification IDs use the same 404 response. Writes retain the existing session-CSRF protection.
There is no `/users/{userId}/notifications` route and no public notification creation route.

Marking a notification read is idempotent and preserves its first `read_at`. Mark-all updates only
the caller's notifications that are unread when its SQL update obtains the relevant row locks;
notifications committed afterward remain unread.

## Transactional production and deduplication

Notification insertion participates in the existing PostgreSQL transaction and requires an active
transaction. A required insertion failure rolls back the related order transition, assignment, or
application decision. Pure `IN_APP` delivery therefore needs no outbox.

Recipients come only from authoritative state: the order's persisted `customer_id`, the created
assignment's persisted driver request after eligibility/locking checks, and the application's
persisted applicant. Controllers never accept a notification recipient or content.

Deduplication keys use immutable business identity:

- order ID + one-way target status + recipient;
- assignment ID + assigned driver;
- application ID + revision + decision + applicant.

The unique database constraint is the final concurrent-retry guard. Inserts use conflict-ignore
only for that known deduplication key; other insertion failures still abort the transaction.

## Content, query cost, and retention

Text is generated server-side and contains only concise public restaurant/application state. The
inbox reads solely from `notifications`: one count query and one bounded page query, with no source
entity lookup loop. Source presentation data needed by the message is copied only at creation.

V1 has no user delete, archive, or pruning operation. Notifications are retained. A reviewed
retention/pruning policy remains technical debt.

## External-channel extension

`NotificationService` is the business production boundary and `channel` is fixed to `IN_APP` in
v1. A future migration can add delivery-attempt/outbox records and a background worker for email,
SMS, or push while leaving business modules calling the same boundary. External channels must not
be marked delivered until a real provider integration exists.

## Verification

`NotificationsIT` uses PostgreSQL 18 Testcontainers and covers ownership, anonymous and suspended
sessions, deterministic bounded pagination, unread filtering, private response fields, idempotent
read, scoped mark-all, CSRF, malformed UUIDs, parameter abuse, absence of a send endpoint,
deduplication, immutable ownership/content, controlled types, and retention. Order, driver, and
restaurant integration suites cover produced types, duplicate/stale requests, and transactional
rollback on required notification failures.
