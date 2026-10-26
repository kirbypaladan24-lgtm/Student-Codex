# Student Codex — Professional Build Plan for GLM 5.3

**Build order:** Android student application first; teacher web dashboard last.  
**Product:** One student-focused Android app with three connected modules and a separate teacher web view, using one Supabase project and one shared PostgreSQL source of truth.
**Architecture decision:** Supabase-first; teacher website is built last. Room remains the Android local cache.

---

## 1. Product Vision

Student Codex helps students manage academic life and helps teachers make better-informed scheduling decisions.

### The three modules

1. **Activity Beacon — primary differentiator**
   - Collects published Google Classroom coursework and announcements that the signed-in student is authorized to access.
   - Lets students record face-to-face or otherwise unlisted activities manually.
   - Combines activity sources without counting the same assignment multiple times.
   - Shows the student their own upcoming workload.
   - Later, the teacher website presents aggregated workload information and lets teachers preview a proposed deadline before assigning work.

2. **Student Guidance Assistant**
   - A deterministic, rule-based chatbot for study guidance, time management, app navigation, and common academic questions.
   - Uses maintained answer categories, keyword/phrase matching, and a clear fallback when no suitable answer exists.
   - It is not marketed as a general AI chatbot and must not invent official university policies.

3. **Student Community**
   - Posts with up to three images.
   - Likes on posts and comments.
   - Nested replies; a reply is itself a comment and can receive likes and further replies.
   - Reply text automatically references the person being replied to, such as `@username`.
   - Authors can edit or delete their own posts/comments/replies.
   - Optional anonymous posting in the public interface.
   - Minimal profile: university, program/course, and year level; no bio field.
   - Reporting, blocking/muting where appropriate, and moderation controls are required for a responsible release.

### Product principle

Do not build three disconnected mini-apps. Give students one consistent account, navigation, design system, notification approach, and backend. Make Activity Beacon the main proof-of-value feature for the first release.

---

## 2. Recommended Architecture and Stack — Supabase First

**Architecture decision:** Use Supabase as the shared backend platform for the MVP. Do not set up a separate Express/Node API, Prisma, Firebase Auth, or Firebase Storage by default. Add a separate backend only if a proven requirement cannot be handled safely and reliably by Supabase services.

### Android student application

- **Language/UI:** Kotlin + Jetpack Compose + Material 3.
- **Architecture:** Single-activity app, screen-level ViewModels, unidirectional data flow, repositories that isolate UI code from remote/local data sources.
- **State/concurrency:** Kotlin coroutines and `StateFlow`.
- **Navigation:** Jetpack Navigation for Compose.
- **Local database:** Room for cached activities, downloaded metadata, drafts, and an outbox for supported offline actions.
- **Background work:** WorkManager for retryable synchronization when connectivity is available; work must be idempotent.
- **HTTP:** Use the community-maintained `supabase-kt` Kotlin Multiplatform client if its current version is compatible with the project's Kotlin/Gradle/Android versions. Pin compatible versions and isolate it behind a `SupabaseDataSource`/repository boundary. If compatibility is a blocker, document the issue and use a single consistent Retrofit/OkHttp adapter to call Supabase's supported HTTP APIs rather than mixing multiple client libraries.
- **Offline policy:** Room is a local cache, not a second source of truth. Show last-sync time and pending changes. Define conflict rules per entity; do not silently overwrite server changes.

**Important SDK note:** Supabase's Kotlin quickstart is official documentation, but the `supabase-kt` library itself is community-maintained. Verify current compatibility and APIs during Phase 0 rather than assuming it is an official Supabase SDK.

### Supabase services (shared source of truth)

- **Database:** Supabase-managed PostgreSQL.
- **Authentication:** Supabase Auth for Student Codex identities. Use the Supabase Auth user UUID as the app's stable identity and reference it from the `profiles` table. Do not introduce Firebase Authentication in parallel for the MVP.
- **Database access:** Supabase Data API/client libraries with explicit PostgreSQL grants and Row Level Security (RLS). Clients may contain the publishable/anon project key because it is not a secret; permissions must come from verified user sessions and database policies. Never place a Supabase secret/service-role key in the APK, browser bundle, Git repository, or public configuration.
- **Schema/migrations:** Version-controlled SQL migrations under `supabase/migrations/`, managed with the Supabase CLI. Prefer migrations over one-off manual Dashboard SQL edits so the database can be reproduced in another environment.
- **Server-side logic:** Supabase Edge Functions (Deno/TypeScript) for trusted operations, especially Google Classroom OAuth token exchange/refresh and sync, privileged reconciliation, and operations that should not be exposed as direct client writes.
- **Storage:** Supabase Storage for community images. Store object paths and metadata in PostgreSQL, not image bytes. Use suitable bucket access rules and Storage policies; do not make private images public merely for convenience.
- **Realtime:** Optional. Do not make Realtime a dependency for the MVP unless a demonstrated requirement needs it.
- **Testing:** Test database constraints and RLS policies, including denied access. Use `supabase test db`/pgTAP where supported and add Android/integration tests for repository flows.

### Security rules that are part of the architecture

1. Enable RLS on every table/view exposed through the Data API and set grants intentionally. A policy does not remove an overly broad SQL grant.
2. Test both allowed and denied cases for `select`, `insert`, `update`, and `delete` as applicable.
3. Treat role assignments as trusted server/admin-managed data. A student must not be able to make themselves a teacher by editing their profile.
4. Use `auth.uid()` and verified membership/role records in policies; do not trust a user ID, course ID, section, ownership flag, or role sent by the client without authorization checks.
5. Keep Google OAuth refresh tokens and all server secrets away from client-readable tables and API responses. If tokens must be stored, encrypt them server-side and restrict access to the responsible Edge Function/backend. Never log access tokens, refresh tokens, or secrets.
6. Review views and SQL functions as well as tables. Views and `SECURITY DEFINER` functions can bypass expected policy protections if designed incorrectly. Prefer invoker-safe views/functions where suitable and restrict function execution grants.
7. Apply validation, rate limiting, and abuse protections to privileged Edge Function endpoints.

### What goes in the database versus Edge Functions

**Database/PostgreSQL:** relational records, unique constraints, foreign keys, simple safe queries, and atomic operations that must be protected against concurrency. Use SQL functions/RPC only when useful and authorize each function explicitly.

**Android/web Supabase clients:** normal user-scoped reads and writes that can be protected cleanly with RLS, such as reading allowed activities or creating/editing the user's own community content.

**Edge Functions:** privileged server-side integrations and workflows. Each function must validate the caller's JWT/role and input, perform only the authorized operation, and return the minimum required data. Use a server-only secret/service-role credential only where necessary; it bypasses RLS and must never be sent to clients.

**Do not automatically force all features through Edge Functions.** Use RLS for ordinary user-scoped CRUD and put only sensitive or cross-record logic behind trusted functions.

### Google Classroom integration

- Use the Google Classroom API with a separate OAuth authorization flow for the Classroom data scopes actually required. Supabase sign-in does **not** automatically grant Classroom API access.
- Request the narrowest current scopes required for courses, coursework/classwork, and announcements. Confirm every endpoint's requirements in the current Google documentation before coding.
- Keep OAuth client secrets and refresh tokens server-side. Store user-scoped tokens in a private table inaccessible to clients and encrypt them at the application layer with a key kept in server-side secrets, or choose another documented secure server-side token store.
- Make synchronization user-authorized, paginated, resumable, rate-limit-aware, and idempotent. Have the app/WorkManager request sync for the signed-in user; do not assume one student can pull every teacher's private course data.
- Keep sync batches small enough for Edge Function limits. If sustained long-running work, scheduled processing, or orchestration outgrows those limits, add a dedicated worker/backend only after documenting the need.

### Teacher website — build last

- **Stack:** React + TypeScript + Vite with `@supabase/supabase-js`.
- **Auth/data:** Same Supabase project and database as Android. Use Supabase Auth sessions, RLS, and teacher-specific authorization. Do not create a second database or duplicate business logic in the frontend.
- **MVP screens:** authorized course/section selector, workload calendar/list, data source/coverage indicators, and a proposed-deadline preview.
- **Access:** a teacher sees only authorized classes and only permitted workload information. Aggregated information should be preferred over student-level activity histories. Personal/private activities must not become visible to teachers by default.

### High-level architecture

```text
Android Student Codex (Kotlin + Compose)
  ├── Activity Beacon
  ├── Student Guidance Assistant
  ├── Student Community
  ├── Room cache / sync outbox
  └── Supabase Kotlin client (behind repositories)
                  │
                  ▼
       Supabase Auth + Data API + Storage
                  │
                  ▼
       Supabase PostgreSQL + RLS
                  ▲
                  │
       Supabase Edge Functions (trusted workflows)
                  │
                  └──── Google Classroom API (separate OAuth authorization)

Teacher Website (React + TypeScript + supabase-js)
  └── Same Supabase project, same database, same RLS model

Versioned SQL migrations + RLS tests are stored in the repository.
```

### Explicitly not in the initial stack

- No Firebase Authentication alongside Supabase Auth.
- No Prisma layer by default.
- No Express/NestJS server by default.
- No direct PostgreSQL credentials in the Android app or browser.
- No service-role/secret key in client code.

A separately deployed TypeScript backend/worker may be added only if a validated need exceeds Supabase's secure API/Edge Function model or execution limits. Record that decision in an architecture decision record (ADR) first.
---

## 3. Scope Rules: What to Build First

### MVP must-have

1. Student sign-in and minimal profile.
2. Android navigation and consistent visual design.
3. Activity Beacon calendar/list.
4. Manual activity entry.
5. Activity identity/source model and duplicate handling.
6. Google Classroom read-only integration after the core manual workflow works.
7. Sync state, errors, and last-synced time.
8. Rule-based guidance chatbot.
9. Community posts, comments/replies, likes, and image upload.
10. Server-enforced ownership and privacy permissions.
11. Automated tests for deduplication, authorization, and core workflows.

### Later enhancements — do not block the first release

- Push notifications and reminders.
- Advanced workload scoring based on estimated effort.
- Assignment groups spanning multiple sections.
- Rich moderation tools and reporting queues.
- Sophisticated chatbot intent recognition.
- Offline queue for community posting and media uploads.
- Teacher analytics beyond the deadline overview.
- Predictive or AI-based workload suggestions.

Never call a feature complete if the UI exists but the backend, persistence, validation, or error states do not work.

---

## 4. The Most Important Design: Activity Beacon Data Model

Do not represent every student's submission or every sync attempt as a new assignment. Separate the assignment from the records that report or identify it.

### Core concepts

- **Canonical activity:** The activity shown in calendars and workload calculations.
- **Source record:** Evidence that the activity came from Google Classroom, a teacher entry, or a student report.
- **Student report:** A student's claim that a face-to-face/unlisted activity exists, including their supplied details.
- **Activity group:** Optional relationship between separate section-specific activities that represent the same underlying assignment across different classes.
- **Student visibility/impact:** Which student, course, or section the activity applies to.

### Suggested tables

Names may change during implementation, but preserve these concepts.

#### `profiles`

- `id` — UUID that references `auth.users.id` and is the same stable Supabase Auth identity
- `username` (unique, if needed for the community)
- `program_id`
- `year_level`
- `created_at`, `updated_at`

Do not store a Firebase UID because Supabase Auth is the identity provider in this architecture. Keep platform-managed authentication data in `auth.users`; put only app profile fields in `public.profiles`. Do not let users update trusted role/verification fields through ordinary profile updates. Only collect profile information actually needed by the product.

#### `courses` / `sections` / `enrollments`

Represent the course/section structure and the users associated with it. Do not infer enrollment from a profile string alone when actual access decisions depend on membership.

#### `activities`

- `id`
- `title`
- `description`
- `activity_type` (assignment, quiz, exam, project, announcement, other)
- `course_id` / `section_id` when applicable
- `due_at` (timezone-aware timestamp; preserve original timezone if needed)
- `estimated_effort_minutes` (nullable; do not fabricate values)
- `scope` (personal, section, course, or other explicitly defined scope)
- `verification_status` (for example: imported, teacher-confirmed, student-reported, needs-review)
- `created_by_user_id`
- `created_at`, `updated_at`, `deleted_at` where soft deletion is needed

#### `activity_sources`

- `id`
- `activity_id`
- `provider` (`google_classroom`, `student_manual`, `teacher_manual`)
- `external_course_id` (nullable)
- `external_item_id` (nullable)
- `source_key` (unique for provider-owned records)
- `reported_by_user_id` (nullable for imported records)
- source metadata and timestamps

For Google Classroom, use a stable key such as:

```text
google_classroom:{courseId}:{courseWorkId}
google_classroom_announcement:{courseId}:{announcementId}
```

Use a database unique constraint on the provider/source identity. Use an atomic upsert/transaction so simultaneous sync requests cannot create duplicate activities.

#### `activity_reports`

Stores student-submitted reports about manual/face-to-face activities.

- `id`
- `reporter_user_id`
- `activity_id` (nullable until matched/confirmed)
- submitted title, due date, course/section, and notes
- `status` (`pending`, `matched`, `confirmed`, `rejected`, `needs_review`)
- created/updated timestamps

A student report is **not automatically a confirmed class-wide assignment**. The student should be able to keep it personal, suggest that it affects a course/section, or link it to a candidate activity where permitted.

#### `activity_groups` and `activity_group_members`

Optional grouping for identical or related assignment instances published separately in multiple sections. Preserve each section's own activity ID, source, deadline, and affected audience. Grouping is for organization/display, not a reason to erase section-level records.

#### `activity_participants` (or an equivalent explicit scope model)

Use this when a class activity applies only to selected students, or when the app needs to distinguish a personal task from a class-wide assignment. Avoid assuming every activity affects every student in a section.

### Deduplication rules

#### Rule 1 — Google Classroom records: exact identity

Google Classroom provides identifiers for coursework and announcements. For a given source item, a PostgreSQL unique constraint plus a transaction/RPC or trusted Edge Function upserts using provider + course ID + item ID. Many students syncing the same source item must resolve to one source/activity, not create a row per student.

#### Rule 2 — Manual entries: candidate matching, not blind merging

Before saving a manual activity, search likely candidates using:

- the same course/section where known
- normalized title similarity
- due date proximity
- description similarity, if available
- an existing Classroom link or reference, if supplied

Show likely matches to the student: **“Is this the same activity?”** Options: “Yes, link it”, “No, separate activity”, or “Not sure”.

Do not auto-merge on title alone. Similar titles and identical due dates can still describe different assignments. For uncertain cases, retain the report as pending/unverified or personal until there is more evidence.

#### Rule 3 — Multiple reports about a manual assignment

Keep the reports as separate pieces of evidence attached to one candidate/canonical activity. The calendar/workload view counts the canonical activity once, not the number of reports. Preserve who reported it and its verification status.

#### Rule 4 — Cross-section copies

If a teacher publishes the same task independently to two Google Classroom courses, the external IDs will differ. Keep separate section-level activities and optionally group them for the teacher's display. The workload calculations must still respect each section's audience and deadline.

#### Rule 5 — Privacy and workload calculations

- A student's private/personal activity should not automatically appear as an identifiable record on the teacher dashboard.
- Prefer aggregated, de-identified workload summaries for teachers unless a student has explicitly shared an activity or a legitimate course-level workflow requires it.
- Count each canonical activity once per affected student/audience. Do not count source rows, duplicate reports, or sync attempts.
- Show a data-coverage/last-synced indicator. The system cannot claim to know an assignment that nobody imported or reported.
- Allow corrections and preserve an audit trail for merges/changes so a bad match can be reversed.

### Workload display

Start with a transparent calendar/list sorted by due date. Do not immediately invent a “stress score”. Later, an explainable workload indicator can use known deadlines, activity type, and estimated effort. Missing effort estimates should be displayed as unknown, not treated as zero.

A “preview proposed deadline” tool belongs in the teacher website phase: teachers enter a tentative date, the system shows the existing known workload for the relevant cohort/date, and the teacher can compare alternative dates. It should support a human decision, not automatically reschedule or publish assignments.

---

## 5. Google Classroom Integration Rules

Implement the core Activity Beacon data model with manual activities first. Only then add Google Classroom.

1. Register/configure the Google Cloud project and OAuth consent properly.
2. Request only the read-only scopes needed for the features, likely including course discovery, student-visible coursework, and announcements. Confirm the exact scopes against the current Google API documentation before coding.
3. Students must explicitly connect their account and authorize access.
4. Respect school/Workspace restrictions and API permissions. Never assume every student's school account permits third-party integrations.
5. Sync only records that the signed-in user is authorized to access.
6. Persist external provider IDs and update timestamps.
7. Use pagination; handle rate limits, expired/withdrawn access, partial failures, and revoked consent.
8. Make sync idempotent. A retry must not create duplicate activities.
9. Store refresh tokens only server-side if the selected OAuth flow requires them, encrypted at rest; never embed client secrets in the APK or commit them to Git. Prefer an established OAuth flow and validate token lifecycle carefully.
10. Provide clear user states: not connected, permission required, syncing, up to date, sync failed, access revoked.
11. Keep the feature read-only for the MVP. Do not create or edit Google Classroom coursework from Student Codex.
12. Store just the fields required for workload planning; avoid copying grades, private feedback, or unnecessary student-submission information.

**Important product limitation:** Google Classroom activity visibility is controlled by Google account access and class permissions. A student integration will not magically provide a university-wide feed of every instructor's assignments. The teacher dashboard should only aggregate authorized and available data, together with properly labeled student reports.

---

## 6. Android App Navigation and UX

Use a consistent navigation layout, such as a bottom navigation bar with:

- **Home** — next deadlines, recent community posts, and quick actions.
- **Activity Beacon** — calendar/list, filters, manual entry, source labels, sync status.
- **Community** — feed, post creation, comments, replies, profile.
- **Guidance** — searchable chatbot conversation and recommended topics.
- **Profile/Settings** — program/year, connected accounts, privacy, notification preferences, sign out.

The main navigation can be adapted after the initial design; avoid cramming every action into the bottom bar.

### UI quality requirements

- Establish a design system: color tokens, typography, spacing, shapes, elevation, icon sizing, and reusable components.
- Support loading, empty, error, success, offline, and permission-denied states for every data-driven screen.
- Keep text readable and tap targets accessible.
- Use consistent date/time formatting and show the user's local timezone clearly where deadlines could be ambiguous.
- Confirm destructive actions, provide undo where sensible, and avoid losing drafts.
- Support pagination or incremental loading for the feed.
- Avoid excessively nested navigation for threaded comments.
- Provide image compression/resizing and upload progress; enforce the three-image limit on both client and server.
- Add meaningful accessibility labels and content descriptions.

---

## 7. Community Safety and Permissions

The community is not complete until the server enforces ownership and moderation rules.

- A user can edit/delete only their own content, unless a moderator with the proper role performs an authorized moderation action.
- Enforce the maximum three images on the API, not only in the UI.
- Validate uploads by MIME type, file signature, size, and allowed format; do not trust filenames alone.
- Store uploaded images in object storage; database rows store metadata and references.
- Model comments and replies as one `comments` table with a nullable `parent_comment_id`; likes should have uniqueness constraints so one user cannot like the same target repeatedly.
- Ensure nested replies cannot reference deleted/nonexistent parents or create invalid cycles.
- When replying, store the parent relationship and render `@username` from the actual parent author; do not trust a client-supplied username for identity.
- Anonymous means hidden from other students in the public interface, not necessarily untraceable to authorized moderators. Publish this clearly and avoid exposing the author's identity through API responses or image metadata.
- Add report-content workflow, rate limiting, moderation states, and abuse-resistant endpoints.
- Use database constraints and server validation for all operations.

---

## 8. Rule-Based Student Guidance Assistant

Keep the first version small and honest.

### Suggested answer categories

- Study planning
- Time management
- Exam preparation
- How to use Activity Beacon
- Google Classroom connection and sync issues
- How to create/edit manual activities
- Community rules and reporting
- Platform FAQs

### Matching design

1. Normalize input: lowercase, trim whitespace, normalize punctuation, and handle common spelling variations carefully.
2. Use intent categories and phrase/keyword rules, with explicit precedence for more specific intents.
3. Maintain responses as structured data so they can be revised without editing screen code.
4. Provide suggestions/buttons for common questions.
5. When confidence is low, say it does not know and provide a safe next step. Never invent official school policies, dates, deadlines, or instructor instructions.
6. Keep the rules engine separate from Compose UI so it can be tested without launching the app.

---

## 9. Step-by-Step Implementation Roadmap

Work in small, verifiable milestones. Complete and test each milestone before starting the next.

### Phase 0 — Repository audit and specification

- Inspect the existing repository and current build configuration before making changes.
- Identify languages, modules, architecture, installed SDKs, existing screens, build failures, and current dependencies.
- Write a short product requirements document and architecture decision record.
- Define the first MVP and clearly mark deferred features.
- Do not rewrite working code just because a different architecture looks fashionable.

**Exit criteria:** documented existing state, approved folder structure, screen map, API/data model draft, and milestone checklist.

### Phase 1 — Android app foundation

- Create or stabilize the Kotlin/Compose project.
- Set up theme/design tokens, navigation, reusable components, and screen-level ViewModels.
- Add mock repositories and representative test data so the UI can be built without a live backend.
- Implement empty/loading/error states from the beginning.
- Set up lint/static checks and a repeatable debug build.

**Exit criteria:** the app installs and navigates reliably; design language is consistent; no core screen contains hard-coded business logic.

### Phase 2 — Supabase project, database schema, migrations, and RLS

This is foundational work for Android and does not include the teacher website yet.

- Create separate development and production Supabase environments where practical. Never use production student data for development tests.
- Install/verify the Supabase CLI and document local/linked project commands. Inspect whether Docker is available before choosing the local development workflow.
- Create version-controlled SQL migrations under `supabase/migrations/` for profiles, programs/courses/sections/enrollments, activities/sources/reports, activity groups, community posts/comments/likes, and moderation records. Add tables only as the feature requires them.
- Reference `auth.users.id` from `public.profiles.id`; do not duplicate passwords or implement a parallel Firebase identity system.
- Add foreign keys, checks, indexes, uniqueness constraints, and timestamps. Use migrations as the repeatable schema source of truth.
- Enable RLS on every API-exposed table and Storage bucket/object path as appropriate. Set SQL grants intentionally; write positive and negative policies for each user role.
- Keep teacher/admin roles and class membership in trusted, admin-managed records. A user must not be able to grant themselves permissions.
- Add database policy tests (pgTAP/`supabase test db` where supported), including attempts to read or mutate another student's private records.
- Define which operations use client-side Supabase queries, which require SQL/RPC, and which require Edge Functions. Do not create a second API layer without a specific requirement.
- Add a short architecture decision record documenting the Supabase-first choice and the criteria that would justify a separate worker/backend later.

**Exit criteria:** schema can be recreated from migrations; RLS/grants are tested for allowed and denied access; no client contains a secret/service-role key; local and hosted environment setup is documented.

### Phase 3 — Supabase Auth and student profile

- Integrate Supabase Auth into Android using a compatible, pinned `supabase-kt` version behind an authentication repository. Confirm current Kotlin/Gradle compatibility first because the SDK is community-maintained.
- Add sign-in, sign-out, session persistence/refresh, and minimal profile setup. Avoid logging tokens or session secrets.
- Use the Supabase Auth UUID as the profile ID and derive identity from the validated session/JWT, never from a user ID supplied in a request body.
- Implement profile RLS so a user can read/update permitted fields on their own profile. Protect role, moderation, verification, or admin fields from client edits.
- If the selected Kotlin SDK cannot be used reliably, record the reason and isolate a single supported REST/HTTP implementation behind the same repository interface; do not mix two auth systems.

**Exit criteria:** sign-in/out and session handling work; a student can access only allowed profile data; forged profile IDs and role changes are denied by database policies; tokens are not exposed in logs or source control.

### Phase 4 — Activity Beacon using manual activities first

- Build Activity Beacon list/calendar and activity details.
- Add manual activity creation/edit/delete, course/subject, due date, type, and optional estimated effort.
- Add Room caching and clear sync/data-source labels.
- Add ownership, visibility scope, and validation rules enforced by RLS and constraints.
- Add Room cache/outbox behaviour for the activity flow and define conflict handling.
- Add tests for timezones, past-due activities, editing, deletion, and RLS.

**Exit criteria:** a student can reliably create and manage an activity end to end; records survive app restarts; date sorting works correctly.

### Phase 5 — Deduplication and report matching

- Implement unique source identity in PostgreSQL and atomic upsert via a transaction/RPC or trusted Edge Function for provider records.
- Build manual candidate matching using course/section, normalized title, due date, and other evidence.
- Show candidate activities and require confirmation for uncertain matches.
- Keep student reports separate from verified canonical activities.
- Add a reversible merge/link workflow and audit history. Restrict authoritative merges to a trusted function/policy; do not let arbitrary clients reassign source records.
- Test concurrent sync submissions, identical titles with different deadlines, same assignment reported under different titles, and separate section instances.

**Exit criteria:** repeated imports never duplicate a provider activity; ambiguous manual matches are not silently merged; workload query counts canonical activities once per affected audience.

### Phase 6 — Google Classroom read-only sync through trusted server-side logic

- Verify current Classroom endpoint permissions and select the narrowest scopes needed. Classroom authorization is separate from Student Codex Supabase Auth.
- Implement the Google OAuth callback/token exchange in a trusted Supabase Edge Function (or a separately justified backend if required). Never place the Google client secret in Android or the teacher website.
- Store any refresh tokens only in a private, client-inaccessible store; encrypt them server-side with a key kept in function/backend secrets. Do not return refresh tokens to the app, log them, or put them in public tables.
- Have the app request sync for the currently signed-in user's authorized Classroom account. Validate the Supabase JWT and ownership before accessing that user's token/data.
- Test course discovery, coursework/assignment import, and announcement import separately. Confirm what student-role versus teacher-role accounts can actually access.
- Persist provider IDs and source timestamps, paginate safely, handle rate limits/backoff and partial failures, and make sync resumable and idempotent.
- Keep sync batches within current Edge Function limits. If execution needs sustained long-running/background processing, document the limitation and propose a dedicated worker before adding one.
- Show connection state, last-sync time, revoked-access state, and useful errors in Android. Support safe disconnect and token revocation/deletion.

**Exit criteria:** a real test account can authorize the required scopes, import only the records it is allowed to access, sync twice without duplicates, handle a retry/revoked permission safely, and keep credentials inaccessible to clients.

### Phase 7 — Student workload overview and polish

- Show upcoming activities by date, course, and source.
- Provide clear labels for Classroom-imported, teacher-confirmed, and student-reported activities.
- Indicate data freshness and missing/failed syncs.
- Add empty state explaining how to connect Classroom or add a manual activity.
- Ensure private/personal entries are not accidentally exposed as class-wide records.

**Exit criteria:** the workload view is useful without the teacher website and communicates the source/verification state of its records.

### Phase 8 — Guidance Assistant

- Add structured answer content, normalization, intent rules, and fallback handling.
- Add links/actions to app features when the relevant intent is recognized.
- Write tests for common wording variations, ambiguous input, and unknown questions.

**Exit criteria:** the chatbot answers supported intents reliably and avoids inventing unverified information.

### Phase 9 — Community module

Implement in this order:

1. Feed and create post with up to three images.
2. Post ownership and edit/delete.
3. Comments and nested replies.
4. Likes with uniqueness constraints.
5. Optional anonymous presentation.
6. Profile posts.
7. Reporting/moderation, rate limiting, and upload validation.

Test deep comment threading, deletion behaviour, race conditions on likes, and anonymous identity leakage.

**Exit criteria:** complete CRUD and interaction workflows work against the backend; ownership and moderation rules are enforced server-side.

### Phase 10 — Android quality, privacy, and security pass

- Test on multiple screen sizes and supported Android versions.
- Verify offline/cache behaviour and recover from lost network connections.
- Check crashes, accessibility, input validation, image upload errors, token expiry, and revoked Google access.
- Review logs to ensure tokens, private post identities, and unnecessary academic data are not exposed.
- Add automated tests for repositories, dedup rules, API authorization, and core UI paths.
- Build a release APK and test a clean install.

**Exit criteria:** no known blocker in the main student flows; clean builds; documented setup and test instructions.

### Phase 11 — Teacher web dashboard (only after Android and database contracts stabilize)

Build the web view last, using the same Supabase project, Auth, PostgreSQL schema, and access policies. Use `@supabase/supabase-js` in the web client for safe RLS-protected reads. Use trusted Edge Functions or appropriately secured SQL for privileged/aggregated operations; never expose a server secret in the browser.

**Teacher MVP:**

- Teacher authentication/role verification.
- Course/section filters limited to authorized classes.
- Calendar/list of known upcoming activities.
- Labels for imported, teacher-confirmed, and student-reported data.
- Coverage/last-sync indicators.
- Workload summary for a chosen day/week.
- Proposed-deadline preview: enter a tentative date, view known workload and compare another date; do not publish or reschedule assignments automatically in the MVP.

**Privacy requirements:**

- Prefer aggregate workload summaries rather than named student-level activity histories.
- Do not expose private/personal student entries to teachers by default.
- Enforce permissions in the backend, not just by hiding UI controls.
- Clearly state that the dashboard shows known data, not guaranteed complete data.

**Exit criteria:** teachers can see only authorized course/section data; the dashboard uses the same canonical activity identities and workload rules as Android; RLS and aggregation access are tested; no separate data model is introduced in the frontend.

### Phase 12 — End-to-end rehearsal and delivery

- Seed clearly labeled demo data for multiple sections and several deadlines.
- Demonstrate one imported activity, one manual face-to-face report, duplicate detection, a section-specific activity, and the teacher's proposed-deadline preview.
- Rehearse the demo in a clean environment with a backup dataset in case live Google OAuth is blocked by school policy or consent configuration.
- Prepare a concise explanation of data limitations, privacy protections, and what is currently implemented versus planned.
- Deliver README, environment variable template, database migration steps, test instructions, API documentation, and known limitations.

---

## 10. Professional Engineering Rules for GLM 5.3

Give GLM 5.3 these rules at the start of the coding session and repeat the relevant phase goal with each task.

1. **Inspect before editing.** Read the current repository structure, build files, related classes, and tests. Never assume files or dependencies exist.
2. **Do not rewrite the whole project.** Make focused, incremental changes that preserve working features.
3. **One milestone at a time.** Do not start the next phase until the current phase builds and its acceptance criteria pass.
4. **No fake functionality.** Do not replace missing backend/API work with hard-coded success screens or mock production data. Mocks are allowed only in explicitly marked development/demo repositories.
5. **Compile and test after every meaningful change.** Run relevant Gradle builds/tests, Supabase migrations, `supabase test db`/RLS tests where supported, Edge Function checks, and web checks. If a tool cannot run, report why and give the exact command for the developer.
6. **Do not invent API behaviour.** Verify current Google/Android/library documentation and the project's installed versions before using a method, dependency, or OAuth scope.
7. **Secure by default.** Enable RLS and least-privilege SQL grants on every exposed table; test allowed and denied paths; keep all Supabase secret/service-role keys and Google OAuth secrets server-side; minimize OAuth scopes; and redact sensitive logs.
8. **Use database constraints for invariants.** Deduplication cannot rely only on an in-memory check or the mobile UI.
9. **Respect single source of truth.** Supabase PostgreSQL is the shared source of truth; Room is a local cache/outbox, and the teacher website uses the same Supabase project with RLS.
10. **No duplicate infrastructure by default.** Do not introduce Firebase Auth, Prisma, Express/NestJS, or a second database alongside Supabase unless an approved ADR explains a concrete need.
11. **Maintainability over cleverness.** Use clear naming, small components, explicit error states, typed models, and comments for non-obvious logic only.
12. **No dependency sprawl.** Add a library only when it solves a defined problem; explain the choice.
13. **Never claim untested completion.** Summarize what changed, commands run, results, remaining issues, and next step.

### GLM phase prompt template

Use a prompt like this for each milestone, replacing the brackets:

```text
You are the senior Android/full-stack engineer working on Student Codex.
We are currently implementing Phase [N]: [PHASE NAME] from the project's build plan.

First inspect the repository and the relevant existing code. Do not edit files yet.
Report:
1. current implementation relevant to this phase;
2. files and modules likely to change;
3. compatibility or API concerns;
4. a small implementation plan and tests.

Wait for my approval before making the changes.

After approval, implement only this phase. Preserve unrelated working behaviour.
Use the project's established architecture and current supported dependency versions.
Run the relevant build, tests, and static checks. Fix failures caused by your changes.
Do not fake an integration or hard-code production success data.

When finished, report:
- files changed and why;
- commands run and exact results;
- tests not run and why;
- known limitations;
- whether every phase exit criterion is met;
- the next smallest safe step.
```

### Recommended working loop

1. Ask GLM to inspect and plan.
2. Review the plan.
3. Implement one small vertical slice.
4. Build/test.
5. Inspect the diff and run the app.
6. Commit the working milestone to Git.
7. Continue to the next slice.

Do not ask GLM to build all three modules, Google integration, deduplication, and teacher web dashboard in one enormous prompt.

---

## 11. Testing Plan

### Supabase security and database tests

- Every Data API-exposed table and view has an intentional grant and RLS policy.
- Anonymous users cannot read student-only records or write protected data.
- One authenticated student cannot read/update/delete another student's private data.
- A student cannot grant themselves a teacher/admin role or change trusted verification fields.
- Teacher queries are restricted to authorized courses/sections; personal student activities remain private by default.
- Storage policies reject unauthorized image reads/writes and paths outside the user's allowed scope.
- Edge Functions reject missing/invalid JWTs, unauthorized role/course access, malformed input, and replayed or duplicate operations where applicable.
- Secret/service-role keys and Google OAuth tokens are absent from APK assets, browser bundles, logs, and client-readable tables.
- Migrations reproduce the schema; database policy tests pass using the documented Supabase CLI workflow.

### Activity Beacon tests

- Synchronizing the same Google Classroom coursework twice does not duplicate it.
- Two student accounts importing the same course item resolve to the same canonical activity.
- One manually reported activity can be linked to an existing activity without increasing the workload count.
- Similar titles with different deadlines are not automatically merged.
- Same title and date in different sections remains section-specific unless intentionally grouped.
- Concurrent sync requests cannot bypass the unique constraint.
- A personal activity remains private unless the user explicitly shares it.
- Timezones, missing deadlines, expired work, deleted source items, revoked access, and partial sync failures are handled predictably.
- Workload totals count canonical activities for the actual affected audience, not source reports.

### Community tests

- A user cannot edit/delete another user's content through a forged API request.
- Post image count cannot exceed three even if the client is modified.
- Duplicate likes are blocked by the database/API.
- Replies preserve the correct parent relationship.
- Anonymous identity is not returned in ordinary public feed responses.
- Upload limits and file validation work on the server.

### Teacher dashboard tests

- A teacher can access only authorized courses/sections.
- Personal student items are not exposed by default.
- Aggregated workload matches the same calculation used by Android.
- A proposed deadline preview changes the displayed load but does not silently create/publish an assignment.
- Missing/old sync data is clearly identified.

---

## 12. Definition of Done

A feature is done only when:

- the interface handles success, loading, empty, offline, and error states;
- RLS, grants, database constraints, and trusted Edge Functions validate inputs and enforce authorization where applicable;
- data persists correctly and version-controlled Supabase SQL migrations are included;
- duplicate/retry behaviour is safe;
- relevant tests pass;
- the feature has been manually exercised on a device or browser;
- sensitive information is not leaked;
- setup or behaviour changes are documented.

For the hackathon, prioritize a small end-to-end Activity Beacon workflow over incomplete breadth. The strongest demo is one that reliably shows data entering the system, being deduplicated, appearing in the student's app, and informing the teacher's later workload view.

---

## 13. Official Documentation to Check During Implementation

Use current documentation and confirm the APIs/versions available in the actual project before coding.

### Supabase

- Android/Kotlin quickstart: https://supabase.com/docs/guides/getting-started/quickstarts/kotlin
- Kotlin client reference: https://supabase.com/docs/reference/kotlin/introduction
- Supabase Auth: https://supabase.com/docs/guides/auth
- Row Level Security: https://supabase.com/docs/guides/database/postgres/row-level-security
- Secure the Data API: https://supabase.com/docs/guides/api/securing-your-api
- Storage access control: https://supabase.com/docs/guides/storage/security/access-control
- Edge Functions: https://supabase.com/docs/guides/functions
- Edge Function limits: https://supabase.com/docs/guides/functions/limits
- Supabase CLI/local development: https://supabase.com/docs/guides/local-development
- Database testing: https://supabase.com/docs/guides/database/testing
- JavaScript client: https://supabase.com/docs/reference/javascript/introduction

**SDK note:** Supabase's Kotlin documentation states that `supabase-kt` is community-maintained, not an official Supabase-maintained SDK. Confirm the current release, Kotlin/Gradle compatibility, and required modules during Phase 0. Keep it behind repository interfaces so the data layer can be replaced without rewriting Compose UI if necessary.

### Android

- App architecture: https://developer.android.com/topic/architecture
- Architecture recommendations: https://developer.android.com/topic/architecture/recommendations
- Jetpack Compose architecture: https://developer.android.com/develop/ui/compose/architecture
- Room: https://developer.android.com/training/data-storage/room
- WorkManager: https://developer.android.com/topic/libraries/architecture/workmanager

### Google Classroom

- API reference: https://developers.google.com/workspace/classroom/reference/rest
- OAuth scopes: https://developers.google.com/workspace/classroom/guides/auth
- Announcements: https://developers.google.com/workspace/classroom/reference/rest/v1/courses.announcements
- Announcement list permissions: https://developers.google.com/workspace/classroom/reference/rest/v1/courses.announcements/list

Google Classroom API access depends on the account's role, the exact endpoint and OAuth scopes, user consent, and Workspace administrator restrictions. Supabase Auth does not grant Classroom permissions. Verify this with real test accounts before promising university-wide coverage.

### Architecture guardrail

Use Supabase PostgreSQL + Auth + Storage + RLS + Edge Functions as the default architecture. Do not add Express/NestJS, Prisma, Firebase Auth, or another database just from habit. If the product later needs a dedicated worker or API service, write an architecture decision record that describes the specific limitation, why Supabase services are insufficient, and how auth/RLS and operational complexity will be handled.
