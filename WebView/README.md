# WebView — Student Codex Teacher Dashboard

The teacher-facing web view of **Student Codex**, relocated to the project
root as a **fully self-contained static web app** — no server, no build step,
no Node.js required. This folder is the canonical home of the teacher
dashboard (the sandbox preview is only a mirror of these files).

## How to open it

**Option 1 — just double-click `index.html`.**
Everything the app needs is inside this folder:

- `vendor/supabase.min.js` — the Supabase client, bundled locally
- `vendor/lucide.min.js` — the icon set, bundled locally
- `styles.css` + `app.js` — the dashboard itself

Only the *data* goes over the network (live Supabase). Works in Chrome,
Edge, Firefox — and later inside the Android app's own WebView.

**Option 2 — serve it locally** (nicer URLs, closer to production):

```
# any static file server works, e.g.
python -m http.server 8080
# then open http://localhost:8080
```

## What it does

- **Sign in** for existing teacher accounts (demo: `demo.teacher@studentcodex.app` / `demo-teacher-123`).
  Both the sign-in and sign-up password fields carry an **eye toggle** (show/hide
  the password; the caret never leaves the field, keyboard-operable, re-hidden
  on tab switch or sign-out).
- **Create teacher account** (self-service sign-up): first/last name, email,
  password, then your **class groups** — four **multi-select dropdowns** fed
  by the live ParSU registry: Campus (optional filter), Program (all 50
  offerings, searchable), Year level and Block. Each dropdown stays
  **collapsed** (label + live summary + chevron, e.g. "BSCS", "2nd, 3rd",
  "A, B, C +5") and **rolls its checkbox panel down** only while open —
  click again, click outside, or press Escape to roll it back up; only one
  panel is open at a time. **Every checked combination instantly becomes a
  removable chip** (e.g. BSCS + 2nd year + blocks A,B → 2 classes in one
  gesture), so multi-class teachers no longer add classes one by one.
  Unchecking only changes the generator — remove a class with its ✕, or
  Clear all (which resets the checkboxes too); up to 12 per account (the
  RPC's cap, enforced with inline feedback). The `teacher_signup_multi`
  RPC (Supabase migration `20260101001600_teacher_signup_multi.sql`, live)
  grants the teacher role and enrolls the account in **every added group's
  sections** at once. Older single-group accounts (migration
  `20260101001300`) keep working; the activation card normalizes them.
  Sign-up lands straight on the dashboard (the project's email confirmation
  is off — see Notes).
- **Cohort filter bar** — Campus / Program / Year / Section dropdowns with a
  live "N of M sections · K scheduled in <month>" count, plus a one-tap
  Reset. Every dropdown lists the **full school registry** straight from the
  database (all 7 campuses, all 50 programs, 1st–5th year, blocks A–F/1–3 —
  never hard-coded), so you can pick any cohort; the count line and an
  inline note tell you when none of *your* sections match (row-level
  security still keeps the data itself limited to the sections you teach).
- **Month calendar** — assignments, quizzes, exams, projects and
  announcements are rendered *inside the day boxes* (date number top-left,
  type-colored uppercase chips below), with prev/next/Today navigation and
  a day-detail dialog (course · section · time · effort · description ·
  verification badge).
- **Student-reported feed** — read-only. When a student reports a
  face-to-face, not-in-Classroom activity in the app, it publishes
  STRAIGHT onto the calendar (a "Student-reported" badge + dashed chip
  outline mark the origin). Teachers never approve anything — the
  dashboard is view-only by design.
- **Past due = done** — entries are never deleted: the day after their due
  date they gray out (chips, day sheets, feed rows) to read as "done";
  due-today stays live until midnight. The legend documents it.
- **Data coverage** — Classroom sync state, last refresh, and the privacy rule.

## File map

| File | Role |
|---|---|
| `index.html` | Entry point — shell, overlays, toasts, script loads |
| `styles.css` | The full design system (deep-green `#2f5b41` brand palette) |
| `app.js` | All logic: auth gate, registry, session/role checks, calendar, reports, dialogs |
| `vendor/supabase.min.js` | `@supabase/supabase-js` v2 bundled as a classic script |
| `vendor/lucide.min.js` | Lucide icon set (UMD) |
| `README.md` | This file |

## Data & security model

Everything runs through the signed-in teacher's Supabase session with the
**publishable key only** — never a service key. Row-level security decides
what is visible: a teacher sees section-scoped activities for sections they
are enrolled to teach. **Personal student activities never appear here**
(the activities RLS keeps them private to their creator).

## Embedding in the Android app (later)

This folder is laid out so it can be dropped into
`app/src/main/assets/webview/` and loaded with a WebView
(`webView.loadUrl("file:///android_asset/webview/index.html")`) with
JavaScript enabled. The app already ships the same publishable key, so the
session model matches.

## Notes

- **Email confirmation is OFF** on this Supabase project (turned off
  2026-10-24). It used to block every sign-up: the account was created but
  no session was issued until the email link was clicked — impossible for
  demo addresses with no inbox, and Supabase's built-in email sending is
  rate-limited anyway. Sign-up now returns a session immediately and lands
  on the dashboard directly.
- How it was flipped (reversible, with the Supabase CLI from this repo):
  `supabase config pull` (syncs the hosted auth config into
  `supabase/config.toml`) → set `[auth.email] enable_confirmations = false`
  → `supabase config push`. The local `config.toml` now mirrors the hosted
  project, so future pushes stay surgical. To re-enable confirmation for
  production, set that key back to `true` and push — the webview handles
  both modes: with confirmation ON, sign-up shows a “Confirm your email”
  notice with a **Resend confirmation email** button, and sign-in failures
  on unconfirmed accounts offer the same resend.
- Accounts created while confirmation was still ON can simply sign in now.
  If one was created mid-sign-up (role not yet granted), the “Activate
  teacher access” card finishes the enrollment automatically.
- A multi-class demo teacher was created during verification:
  `multiclass.teacher@studentcodex.app` / `multi-class-123` (BSCS 2-A Goa +
  BSHM 1-B San Jose class groups — 6 enrollments). A second one exercises
  the checkbox quick picker: `checkbox.teacher@studentcodex.app` /
  `checkbox-123` (Cora Checkbox — BSCS 2-A + 2-B Goa, BSHM 1-B San Jose).
  A third exercises the dropdown picker: `dropdown.teacher@studentcodex.app` /
  `dropdown-123` (Dora Dropdown — BSCS 2-A Goa).
  Delete either under Dashboard → Authentication → Users when no longer
  needed.
