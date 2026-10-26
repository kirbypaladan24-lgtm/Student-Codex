# Offline architecture and synchronization plan (Room + WorkManager)

Status: **design for Phase 4/5 — not yet implemented.** Written during the
Supabase foundation phase so the database/client decisions made now cannot
accidentally lock the app out of the offline behaviour the product requires.
Nothing on this page is claimed to be running today; it is the contract the
future sync layer must implement and be tested against.

## Roles (single source of truth rule)

| Layer | Role |
|-------|------|
| Supabase PostgreSQL | Source of truth for all **synchronized** data (activities, feed, profile) |
| Room (Android) | Local cache for offline reads + an **outbox** of pending local writes |
| WorkManager | Retryable background synchronization; the only component that pushes the outbox |

Supabase does **not** synchronize Room. There is no magic bridge: the app
itself pulls changes, pushes queued writes, and reconciles them. Room is never
treated as a second source of truth (ADR-001 rule).

## How each concern is handled

### Locally recorded activities
A manual activity is written to Room **first** (with a client-generated UUID
as its primary key and `sync_state = pending`). The Beacon UI reads from Room,
so the entry is immediately visible and survives restarts even with no
network. It is marked `synced` only after the server confirms the insert.

### Queued changes (outbox)
Every local mutation appends an immutable operation row to a Room `outbox`
table: `(id, entity, entity_id, op, payload_json, created_at, attempts,
next_retry_at)`. UI writes never talk to Supabase directly — only the sync
worker does. The app surfaces a "pending changes" count derived from this
table, plus a last-sync timestamp, so the user always knows what is unsent.

### Failed network requests
Failures never drop the operation: the worker increments `attempts` and
schedules `next_retry_at` with exponential backoff (capped), under a network
availability constraint. WorkManager unique work names per entity make
re-enqueue idempotent. Operations that permanently fail (e.g. rejected by a
policy) flip to `failed` and are surfaced in the UI as an explicit error with
the option to edit or discard — never silently discarded.

### Retry-safe synchronization (idempotency)
- **Manual activities** push as upserts keyed by the row UUID: replaying the
  same insert is a no-op (`on conflict (id) do update`).
- **Google Classroom imports** (Phase 5/6) upsert keyed by the database-unique
  `activity_sources.source_key` (`on conflict (source_key) do update`), so a
  retried sync run converges to exactly one source row and one canonical
  activity.
- Likes/reports use unique `(user, target)` constraints server-side; a replay
  is absorbed by the database.

### Duplicate prevention
Dedup is a database guarantee, not an in-memory check (plan rule 8): provider
sources are unique on `source_key` / `(provider, external_course_id,
external_item_id)`; manual-vs-canonical merging is **never automatic** —
candidate matching goes through the report/match review workflow with teacher
or admin confirmation. The sync worker must not invent merges to "clean up".

### Conflicting updates
Conflict rules are per entity and explicit:
- **Personal activities**: last-writer-wins, guarded by `updated_at` — the
  worker sends its write only if it is newer than the server row it last saw;
  otherwise the server version wins, the local row is refreshed, and the UI
  shows a non-destructive "updated elsewhere" notice. Local edits are never
  silently overwritten without the user seeing the newer value.
- **Section/course-scoped activity fields**: server wins (teachers are the
  authoritative editors); the app re-reads and re-caches.
- **Community content**: ownership + guard triggers on the server decide;
  conflicts surface as errors to the author, never as silent merges.

### Reconciliation (pull)
Each sync pass: (1) push outbox in FIFO order, (2) pull server changes since
the per-table `synced_at` cursor, (3) apply server state to Room, (4) advance
cursors transactionally with the Room write. A full re-pull (cursor reset)
exists as a manual recovery action. Soft deletes propagate through the pull
so locally cached rows disappear correctly.

## Testing commitments for the sync phase
- airplane-mode entry → offline create/edit → reconnect → data reaches
  Supabase exactly once;
- kill/retry of the worker mid-flight → no duplicate rows (constraint-backed);
- two devices editing the same personal activity → defined winner + visible
  notice, no silent loss;
- clock skew handling for `updated_at` comparisons (server timestamps only).

## What deliberately does NOT change now
Room and WorkManager remain the local stack — no new sync library, no
realtime dependency, and no change to the Supabase schema for sync purposes
until this phase is implemented and tested end to end.
