# ADR-001: Supabase-first shared backend

Status: Accepted. Date: Phase 0. Deciders: product owner + engineering.

## Context

Student Codex needs identity, a relational source of truth, image storage, and trusted
server-side logic for Google Classroom sync. The previous plan draft assumed a custom
Express/Node API with Prisma and Firebase Auth + Storage.

## Decision

Use **Supabase as the shared backend platform for the MVP**:

- **Auth:** Supabase Auth. The Supabase Auth user UUID is the app's stable identity and
  the `profiles.id` foreign key. No Firebase Authentication in parallel.
- **Database:** Supabase-managed PostgreSQL with Row Level Security (RLS) on every
  API-exposed table and intentional SQL grants.
- **Migrations:** version-controlled SQL under `supabase/migrations/`, managed with the
  Supabase CLI. Migrations are the repeatable schema source of truth.
- **Trusted logic:** Supabase Edge Functions (Deno/TypeScript) for privileged operations:
  Google Classroom OAuth token exchange/refresh and sync, privileged reconciliation, and
  operations that must not be direct client writes.
- **Storage:** Supabase Storage for community images; object paths/metadata in PostgreSQL.
- **Android client:** the community-maintained `supabase-kt` client, pinned to a version
  verified compatible with the project's Kotlin/Gradle/Android versions, isolated behind
  `SupabaseDataSource`/repository boundaries so the data layer can be replaced without
  rewriting Compose UI. If compatibility fails, use one consistent Retrofit/OkHttp
  adapter against Supabase's HTTP APIs instead; never mix client libraries.
- **Teacher website:** React + TypeScript + Vite with `@supabase/supabase-js`, same
  project and RLS model, built last.

Keys: only the publishable/anon key ships in clients (permissions come from verified
sessions + RLS). The service-role/secret key and Google OAuth secrets stay server-side
(Edge Function secrets), never in the APK, browser bundle, or Git.

## Alternatives considered

- **Express + Prisma + Firebase Auth/Storage:** more moving parts, two identity systems
  to reconcile, more deployment surface. Rejected for the MVP; not needed for the
  product's requirements.
- **NestJS:** only if a team already prefers it. Not applicable.

## Consequences

- RLS policies must be designed and tested (allowed AND denied paths) per table/view.
- `supabase-kt` is community-maintained: compatibility must be verified at Phase 2 start
  and the client kept behind repository interfaces.
- Edge Function execution limits bound sync batch sizes; a dedicated worker/backend may
  be added later ONLY via a new ADR documenting the specific limitation that Supabase
  services cannot satisfy.
- Room on Android is a local cache/outbox, never a second source of truth.
