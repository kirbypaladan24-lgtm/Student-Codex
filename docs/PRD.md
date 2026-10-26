# Student Codex - Product Requirements Document (MVP)

Status: Approved baseline for the first release. Source of truth for scope decisions.
Related: `.plan/PLAN.md` (full build plan), `docs/ADR-001-supabase-first.md`, `docs/ARCHITECTURE.md`.

## 1. Product vision

Student Codex helps students manage academic life and helps teachers make better-informed
scheduling decisions. One student-focused Android app with three connected modules and a
separate teacher web view, using one Supabase project and one shared PostgreSQL source of truth.

## 2. Users

- **Primary:** university/school students who juggle deadlines from Google Classroom,
  face-to-face classes, and personal tasks.
- **Secondary:** teachers who want to see aggregated cohort workload before publishing a
  new deadline (teacher web dashboard, built last).

## 3. Modules (priority order)

1. **Activity Beacon (primary differentiator, MVP-critical)**
   - Collects Google Classroom coursework/announcements the signed-in student is
     authorized to access (read-only).
   - Lets students record face-to-face/unlisted activities manually.
   - Deduplicates combined activity sources; never counts the same assignment twice.
   - Shows the student their own upcoming workload (transparent calendar/list first).
   - Later: teacher website shows aggregated workload and a proposed-deadline preview.
2. **Student Guidance Assistant**
   - Deterministic, rule-based chatbot for study guidance, time management, app
     navigation, and common academic questions.
   - Clear fallback when no suitable answer exists; never invents official policies.
3. **Student Community**
   - Posts with up to three images; likes on posts and comments; nested replies with
     automatic `@username` references; author edit/delete; optional anonymous posting;
     minimal profile (university, program, year level); reporting and moderation.

## 4. MVP must-have (from the build plan)

1. Student sign-in and minimal profile.
2. Android navigation and consistent visual design.
3. Activity Beacon calendar/list.
4. Manual activity entry.
5. Activity identity/source model and duplicate handling.
6. Google Classroom read-only integration (after the core manual workflow works).
7. Sync state, errors, and last-synced time.
8. Rule-based guidance chatbot.
9. Community posts, comments/replies, likes, and image upload.
10. Server-enforced ownership and privacy permissions (RLS).
11. Automated tests for deduplication, authorization, and core workflows.

## 5. Explicitly deferred (do not block the first release)

Push notifications/reminders, advanced workload scoring, cross-section assignment
groups, rich moderation queues, sophisticated chatbot intent recognition, offline
community posting queue, teacher analytics beyond the deadline overview, predictive
workload suggestions.

## 6. Success criteria (hackathon demo)

A reliable end-to-end Activity Beacon workflow: data enters the system (imported and
manual), is deduplicated, appears in the student's app, and informs the teacher's
workload view. Every screen handles success, loading, empty, offline, and error states.
No feature is called complete when only the UI exists.
