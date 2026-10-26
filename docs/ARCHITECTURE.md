# Student Codex - Architecture & Screen Map

Related: `docs/PRD.md`, `docs/ADR-001-supabase-first.md`, `.plan/PLAN.md` (authoritative).

## 1. System overview

```text
Android Student Codex (Kotlin + Compose)
  |- Activity Beacon | Guidance Assistant | Student Community
  |- Room cache / sync outbox (local cache only)
  |- Supabase Kotlin client (behind repositories)
        |
        v
  Supabase Auth + Data API + Storage
        |
        v
  Supabase PostgreSQL + RLS  <-- single source of truth
        ^
        |
  Supabase Edge Functions (trusted workflows)
        |
        +----> Google Classroom API (separate OAuth authorization)

Teacher Website (React + TS + supabase-js) - same Supabase project, same RLS model (built last)
```

## 2. Android layering (unidirectional data flow)

```text
Composable screens (stateless where possible)
   ^ render immutable UiState        | events
ViewModels (screen-level, StateFlow<UiState>)
   ^                                 | calls
Repositories (interfaces; UI never sees Supabase/Room/HTTP details)
   ^                                 |
Data sources: SupabaseDataSource (Phase 2+), Room cache (Phase 4), fake demo data (Phase 1)
```

Rules: no API calls, matching rules, or database logic inside Composables; ViewModels have
no Activity/View references; every data screen renders loading / empty / error / content;
all user-facing text lives in `res/values/strings.xml`.

## 3. Screen map (Phase 1 foundation)

| Tab (bottom navigation) | Route | Phase 1 scope | Later phases |
|---|---|---|---|
| Home | `home` | next deadlines, recent community posts, quick actions | live data, notifications |
| Activity Beacon | `beacon` | filtered, date-grouped list; source + verification labels; demo data | manual entry (P4), dedup matching (P5), Classroom sync (P6) |
| Community | `community` | feed with local like toggle; demo data | post creation, comments/replies, images (P9) |
| Guidance | `guidance` | rule-based assistant with suggested topics | expanded rules (P8) |
| Profile | `profile` | demo profile + planned-integration statuses | sign-in (P3), Classroom connect (P6) |

Detail routes (activity detail, post detail, manual entry) are added with their phases.

## 4. Data model draft (relational concepts; final SQL in supabase/migrations)

- `profiles` (id = auth.users.id, username, program, year_level)
- courses / sections / enrollments
- `activities` (canonical: title, type, course/section, due_at, effort, scope,
  verification_status)
- `activity_sources` (provider + external ids + source_key UNIQUE; e.g.
  `google_classroom:{courseId}:{courseWorkId}`)
- `activity_reports` (student-submitted evidence; pending/matched/confirmed/rejected)
- `activity_groups` + members (optional cross-section grouping)
- community: `posts`, `comments` (nullable parent_comment_id), `likes` (unique per
  user+target), image metadata referencing Storage objects; moderation records
- Dedup rules: provider identity upsert (unique constraint + transaction/RPC);
  manual candidate matching (course + normalized title + date proximity) with explicit
  confirmation; reports counted once per canonical activity.

## 5. Milestone checklist (exit criteria per phase)

- [x] Phase 0 - repository audit; PRD; ADR; architecture/screen map; stack verified.
- [ ] Phase 1 - app installs & navigates; design language consistent; empty/loading/error
      states from the start; no business logic in screens; compile + unit tests pass.
- [ ] Phase 2 - Supabase project, migrations, RLS tested (allowed + denied).
- [ ] Phase 3 - Supabase Auth sign-in/out; profile RLS.
- [ ] Phase 4 - manual activities end-to-end + Room cache.
- [ ] Phase 5 - dedup + candidate matching with confirmation.
- [ ] Phase 6 - Classroom read-only sync via Edge Functions.
- [ ] Phase 7 - workload overview & polish.
- [ ] Phase 8 - guidance assistant full rules.
- [ ] Phase 9 - community module.
- [ ] Phase 10 - quality/privacy/security pass.
- [ ] Phase 11 - teacher web dashboard.
- [ ] Phase 12 - end-to-end rehearsal & delivery.

## 6. Testing strategy

- **Phase 1:** ViewModel unit tests (state transitions), guidance engine rules test.
- **Phase 2+:** pgTAP/`supabase test db` RLS policy tests (allowed AND denied),
  repository integration tests, dedup concurrency tests, Compose UI tests for core paths.
- Commands: `.\gradlew.bat :app:testDebugUnitTest`, `.\gradlew.bat :app:compileDebugKotlin`.

## 7. Phase 0 audit summary (current repository state)

Fresh Android Studio "Empty Activity" template: AGP 9.2.1, Kotlin 2.2.10, Gradle 9.4.1
wrapper (JDK 21 toolchain auto-provisioned), Compose BOM 2026.02.01, Material 3,
compileSdk 36.1 / minSdk 26 / targetSdk 36, namespace `com.example.studentcodex`,
single-activity template app, no custom code, no tests beyond template, VS Code
diagnostics clean. No `.skills/` constraints besides the Android skill; no prior git
history usable via CLI on this machine (git hangs - likely the WindowsApps alias stub;
commit through Android Studio if needed).

Open verification items for Phase 2 start: `supabase-kt` version compatibility with
Kotlin 2.2.10; Docker availability for the Supabase CLI local workflow.
