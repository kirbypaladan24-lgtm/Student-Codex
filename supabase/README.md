# Student Codex — Supabase Backend

Versioned SQL migrations and policy tests for the shared Supabase project
(Supabase-first architecture — see `docs/ADR-001-supabase-first.md`).

## Layout

```
supabase/
  migrations/          # versioned schema + RLS (the single source of truth)
  tests/policies_test.sql          # plain-SQL RLS verification (allowed + denied paths)
  tests/gc_source_identity_test.sql  # Google Classroom dedup constraints + retry-safe upsert
```

Migrations are numbered and must be applied in order. Each one leaves the
database in a secure state: tables are created together with their indexes,
constraints, guard triggers, and RLS policies.

## What the schema implements

| Area | Tables |
|------|--------|
| Identity / profile | `profiles` (auto-created from `auth.users`), `user_roles` (admin-managed), `programs` |
| Academic structure | `courses`, `sections`, `enrollments` (trusted, admin-managed) |
| Activity Beacon | `activities`, `activity_sources`, `activity_reports`, `activity_groups`, `activity_group_members`, `activity_participants` |
| Community | `posts`, `post_images` (max 3 enforced by DB), `comments` (self-referencing replies), `post_likes`, `comment_likes`, `user_blocks`, views `feed_posts` / `feed_comments` |
| Moderation | `content_reports` |
| Storage | private bucket `community-images` + policies (own-folder writes only) |

## Deduplication model (Activity Beacon)

* **Google Classroom identity** — `activity_sources.source_key`
  (`google_classroom:{courseId}:{courseWorkId}`) and
  `(provider, external_course_id, external_item_id)` are unique, so repeated
  syncs of the same coursework can never create a second source row or a
  second canonical activity. Imports upsert atomically inside the trusted
  Edge Function (Phase 5/6) via
  `on conflict (source_key) where source_key is not null do update` — the
  predicate is REQUIRED because the unique index on `source_key` is partial
  (`source_key` is nullable for manual sources); a bare `on conflict
  (source_key)` is rejected by PostgreSQL. Verified by
  `tests/gc_source_identity_test.sql`.
* **Manual reports** — never auto-merged. `activity_reports` keep their own
  status workflow (`pending → matched/confirmed/rejected/needs_review`,
  reporter may `withdraw`); linking a report to a canonical activity is a
  teacher/admin-confirmed step enforced by guard triggers. Candidate matching
  (course/section, normalized title, due-date proximity, notes) is Phase 5.
* **Cross-section copies** — separate section-level `activities` rows grouped
  organizationally through `activity_groups`; each section keeps its own id,
  source and deadline so workload stays per-audience.

## Operation routing (client SQL vs RPC vs Edge Functions)

| Operation | Path |
|-----------|------|
| Read own/section activities, feed, comments, likes, profile | Client Supabase query (RLS) |
| Create/edit own manual activities, posts, comments, likes, reports | Client Supabase query (RLS + guard triggers) |
| Soft-delete own content | Client UPDATE (RLS: deleted rows visible only to their author) |
| Atomic Classroom import / upsert by source_key | Edge Function (service role) — Phase 5/6 |
| Authoritative merges / report confirmation beyond own section | Edge Function or guarded teacher RPC — Phase 5 |
| Role grants, enrollments, course structure | Admin/service only (no client policies exist) |

## Applying migrations

### Option A — hosted project (no Docker needed)

```powershell
# run ALL commands from the StudentCodex project root (where supabase/ lives)

# one-time setup (already done on this machine):
npm install -g supabase
supabase init               # creates supabase/config.toml
supabase login

# per hosted project:
supabase link --project-ref <your-project-ref>   # ref = dashboard URL: .../project/<ref>
supabase db push            # applies pending migrations in order
```

> If `db push` reports the remote already contains these objects (applied
> earlier via the SQL Editor), align the migration history with
> `supabase migration repair --status applied <version>` for each listed
> version, then re-run `supabase db push`.

### Option B — local stack (requires Docker Desktop)

```powershell
supabase init               # creates config.toml; our migrations/ and tests/ folders are picked up automatically
supabase start              # boots the local stack and applies migrations
supabase test db            # runs tests/policies_test.sql
supabase db reset           # recreate local db from migrations
```

> The CLI is installed and initialized on this machine (`config.toml` exists).
> Docker is not installed — that only matters for Option B and `supabase test db`.

## Running the tests

Both test files are **plain SQL** (DO blocks with assertions), so they run
anywhere the migrated schema exists:

- against the hosted project: paste them into the Dashboard **SQL Editor** and run
- with the local CLI stack (needs Docker): `supabase test db`
- against any Postgres: pipe them through `psql` after applying the migrations
  (`policies_test.sql` simulates Supabase identity via `SET LOCAL ROLE` +
  `request.jwt.claims`, exactly the way PostgREST provides them)

Expected final outputs: `ALL STUDENT CODEX POLICY TESTS PASSED` and
`GC SOURCE IDENTITY TESTS PASSED`. `gc_source_identity_test.sql` is hermetic
(distinctive IDs, begin/rollback) and runs in the trusted no-JWT context the
future Classroom sync Edge Function will use.

The suite covers: anonymous denial everywhere; personal-activity privacy;
section visibility vs non-members; participant sharing; forged ownership
rejection; self-role-escalation denial; teacher vs student write rights;
verification-status escalation guards; provider source uniqueness (dedup
rule 1); report workflow (pending-only insert, reporter withdraw, staff
confirm); 3-image DB limit; duplicate-like denial; comment nesting rules
(same post, live parent, depth 5, immutable linkage); anonymous identity
never leaking through feed views; blocking; storage own-folder rules;
enrollment visibility; moderation triage; cross-section group visibility.

## Critical design rules encoded here (do not "simplify" away)

1. **RLS policies must not re-query their own table through STABLE helper
   functions** — `INSERT ... RETURNING` (PostgREST default) evaluates SELECT
   policies on the returned row, and STABLE functions cannot see the row the
   statement is inserting. `activities_select` is deliberately row-local.
2. **PostgreSQL enforces SELECT policies as WITH CHECK on UPDATE** — updated
   rows must remain visible. Soft-deleted rows therefore stay visible only to
   their author: `(deleted_at is null OR author_id = auth.uid())`.
3. **Policies must not subquery their own table** — PostgreSQL rejects it as
   infinite recursion (comments' parent validation lives in a SECURITY
   DEFINER trigger instead).
4. **No-JWT callers are the trusted service context** (Edge Functions /
   service role). Guard triggers allow them, which is safe because every
   client-facing policy is scoped `to authenticated`.
5. **Trusted data has no client write policies**: `user_roles`, `enrollments`,
   `courses`, `sections`, `programs`, `activity_groups` — a student can never
   self-assign roles or membership.

## Environment policy

- Keep separate development and production Supabase projects; never test
  against production student data.
- The publishable (anon) key is the only key that may ship in the Android
  app. Never put the service-role key or any secret in the APK, repo, or
  browser bundle.
- Google Classroom OAuth client secrets and refresh tokens belong only to
  Edge Function secrets (Phase 6) — never in client-readable tables.

## Android connection values (Phase 3)

The Android app reads its Supabase configuration at **build time** from a
git-ignored `.env` file at the project root. It is never packaged as an asset
and its values never enter source control:

```properties
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_PUBLISHABLE_KEY=<publishable key>
```

Gradle converts exactly these two variables into `BuildConfig` fields. A
missing `.env`, a missing variable, a non-https URL, an unparseable key, or a
secret key (`sb_secret_…` / legacy `service_role` JWT) fails the build with an
actionable error that never echoes the value. Rules and version pins:
`docs/ADR-002-env-and-supabase-client.md`.

The publishable key is client-safe by design: authorization is enforced by the
RLS policies in `migrations/` (verified by `tests/policies_test.sql`).
Secret / service-role keys and Google OAuth secrets stay in Supabase dashboard
secrets and Edge Function secrets (Phases 5–6) — never in the app.
