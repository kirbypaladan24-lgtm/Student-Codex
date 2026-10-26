// ============================================================================
// Student Codex — classroom-sync Edge Function (Phase 6).
//
// Imports the signed-in user's Google Classroom coursework + announcements
// into the canonical `activities` table as read-only 'imported' evidence.
//
// Design (matches the DB layer authored in migrations 0002/0008):
//   * the caller is the ANDROID APP (or the teacher dashboard): a signed-in
//     Supabase session JWT in the Authorization header (platform-verified)
//     plus a freshly authorized GOOGLE ACCESS TOKEN in the body. Tokens are
//     used in transit only — nothing is stored server-side, so there is no
//     refresh token to leak and no client secret anywhere (the app's
//     authorization uses a public web client ID);
//   * each Classroom item is imported through upsert_activity_from_source()
//     — service-only, idempotent by the unique source_key
//     google_classroom:{courseId}:{courseWorkId} /
//     google_classroom_announcement:{courseId}:{announcementId} — so a
//     re-sync refreshes content instead of duplicating rows;
//   * a Classroom course maps to the user's own section whose COURSE CODE
//     matches the course name (normalized). Unmatched classes are skipped
//     and reported, never force-created;
//   * due dates are DATE-first semantics in the university zone: a due
//     date without a time means end-of-day 23:59 Asia/Manila (fixed +8,
//     no DST) — the same rule as the app's domain/SchoolTime.kt.
//
// Environment (auto-injected on Supabase Edge Functions):
//   SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY
// ============================================================================

const MANILA_OFFSET_MS = 8 * 60 * 60 * 1000; // Asia/Manila, UTC+8, no DST

const jsonHeaders = { "Content-Type": "application/json" };
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: jsonHeaders });

const nowIso = () => new Date().toISOString();
const truncate = (s: string, n: number) => (s.length > n ? s.slice(0, n - 1) + "…" : s);
const norm = (s: string | null | undefined) =>
  (s ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "");

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
  const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!SUPABASE_URL || !SERVICE_KEY) return json({ error: "server_misconfigured" }, 500);

  // ---- 1. Identify the caller (platform already verified the JWT). ------
  const authHeader = req.headers.get("Authorization") ?? "";
  const jwt = authHeader.toLowerCase().startsWith("bearer ") ? authHeader.slice(7).trim() : "";
  if (!jwt) return json({ error: "missing_bearer_token" }, 401);

  const meRes = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { apikey: SERVICE_KEY, Authorization: `Bearer ${jwt}` },
  });
  if (meRes.status !== 200) return json({ error: "invalid_session" }, 401);
  const me = await meRes.json();
  const uid: string | undefined = me?.id;
  if (!uid) return json({ error: "invalid_session" }, 401);

  const service = {
    apikey: SERVICE_KEY,
    Authorization: `Bearer ${SERVICE_KEY}`,
    "Content-Type": "application/json",
  };

  // ---- 2. Google access token from the request body. --------------------
  let body: { googleAccessToken?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: "invalid_body" }, 400);
  }
  const accessToken = (body.googleAccessToken ?? "").trim();
  if (!accessToken) return json({ error: "missing_google_access_token" }, 400);

  const googleGet = async (path: string) =>
    await fetch(`https://classroom.googleapis.com/v1/${path}`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });

  /** Writes the connection-state registry row (service context). */
  const setConnection = async (fields: Record<string, unknown>) => {
    await fetch(`${SUPABASE_URL}/rest/v1/google_connections`, {
      method: "POST",
      headers: { ...service, Prefer: "resolution=merge-duplicates" },
      body: JSON.stringify({ user_id: uid, ...fields }),
    });
  };

  const fail = async (error: string) => {
    await setConnection({
      status: "connected",
      last_sync_at: nowIso(),
      last_sync_status: "failed",
      last_sync_error: truncate(error, 500),
    });
    return json({ status: "failed", error, message: "Sync failed — see connection details." });
  };

  // ---- 3. List the user's ACTIVE Classroom courses (probes the token). ---
  let coursesRes: Response;
  try {
    coursesRes = await googleGet("courses?courseStates=ACTIVE&pageSize=100");
  } catch (e) {
    return await fail(`network_error: ${(e as Error).message}`);
  }
  if (coursesRes.status === 401 || coursesRes.status === 403) {
    // Google refused the token: revoked/expired consent or missing scope.
    const detail = await coursesRes.text();
    await setConnection({
      status: coursesRes.status === 401 ? "revoked" : "expired",
      last_sync_at: nowIso(),
      last_sync_status: "failed",
      last_sync_error: truncate(`google_${coursesRes.status}: ${detail}`, 500),
    });
    return json({
      status: "failed",
      error: `google_${coursesRes.status}`,
      message: "Google rejected the authorization — reconnect Google Classroom.",
    });
  }
  if (!coursesRes.ok) return await fail(`google_${coursesRes.status} on courses.list`);
  const courses: Array<{
    id: string;
    name?: string;
    section?: string;
  }> = (await coursesRes.json()).courses ?? [];

  // ---- 4. The user's local sections (course codes to match against). ----
  const enrollRes = await fetch(
    `${SUPABASE_URL}/rest/v1/enrollments?select=section_id,role,section:sections(id,name,course:courses(id,code))&user_id=eq.${uid}&limit=500`,
    { headers: service },
  );
  if (!enrollRes.ok) return await fail(`db_${enrollRes.status} on enrollments`);
  const enrollments = await enrollRes.json() as Array<{
    section_id: string;
    role: string;
    section: { id: string; name: string; course: { id: string; code: string } | null };
  }>;
  const localSections = enrollments
    .filter((e) => e.section?.course?.code)
    .map((e) => ({
      sectionId: e.section.id,
      sectionName: e.section.name,
      courseId: e.section.course!.id,
      courseCode: e.section.course!.code,
    }));

  // ---- 5. Match Classroom courses to local sections by course code. -----
  const matched: Array<{
    course: { id: string; name?: string; section?: string };
    target: { sectionId: string; sectionName: string; courseId: string; courseCode: string };
  }> = [];
  const skippedCourses: Array<{ classroomName: string; reason: string }> = [];

  for (const course of courses) {
    const nc = norm(course.name);
    const candidates = localSections.filter(
      (ls) => nc === norm(ls.courseCode) || (nc.length > 0 && nc.startsWith(norm(ls.courseCode))),
    );
    if (candidates.length === 0) {
      skippedCourses.push({
        classroomName: course.name ?? course.id,
        reason: "no matching course code in your subjects",
      });
      continue;
    }
    // Several sections share the course code: prefer the one whose name
    // matches the Classroom course's own section label.
    const bySectionLabel = course.section
      ? candidates.find((c) => norm(c.sectionName) === norm(course.section))
      : undefined;
    matched.push({ course, target: bySectionLabel ?? candidates[0] });
  }

  // ---- 6. Import coursework + announcements for every matched course. ---
  let imported = 0;
  let updated = 0;
  const skippedCoursework: Array<{ classroomName: string; reason: string }> = [];

  /** Calls the trusted, idempotent import RPC as the service. */
  const importItem = async (args: {
    sourceKey: string;
    externalCourseId: string;
    externalItemId: string;
    title: string;
    description: string | null;
    activityType: string;
    courseId: string;
    sectionId: string;
    dueAt: string | null;
    sourceMeta: Record<string, unknown>;
  }) => {
    // Count new vs refreshed by looking for the existing source row first.
    const existing = await fetch(
      `${SUPABASE_URL}/rest/v1/activity_sources?select=activity_id&source_key=eq.${encodeURIComponent(args.sourceKey)}`,
      { headers: service },
    );
    const existed = existing.ok
      ? ((await existing.json()) as unknown[]).length > 0
      : false;

    const rpcRes = await fetch(`${SUPABASE_URL}/rest/v1/rpc/upsert_activity_from_source`, {
      method: "POST",
      headers: service,
      body: JSON.stringify({
        p_source_key: args.sourceKey,
        p_provider: "google_classroom",
        p_external_course_id: args.externalCourseId,
        p_external_item_id: args.externalItemId,
        p_requesting_user_id: uid,
        p_title: args.title,
        p_description: args.description,
        p_activity_type: args.activityType,
        p_course_id: args.courseId,
        p_section_id: args.sectionId,
        p_due_at: args.dueAt,
        p_estimated_effort_minutes: null,
        p_scope: "section",
        p_source_meta: args.sourceMeta,
      }),
    });
    if (!rpcRes.ok) throw new Error(`rpc_${rpcRes.status}: ${await rpcRes.text()}`);
    if (existed) updated += 1;
    else imported += 1;
  };

  /** Classroom dueDate+dueTime → ISO instant at Manila wall-clock. */
  const dueIso = (dueDate?: { year?: number; month?: number; day?: number }, dueTime?: {
    hours?: number;
    minutes?: number;
  }) => {
    if (!dueDate?.year || !dueDate.month || !dueDate.day) return null;
    // A due DATE without a time means end-of-day (23:59) — the intuitive
    // reading of "due Friday". Manila wall clock = UTC + 8h.
    const hh = dueTime?.hours ?? 23;
    const mm = dueTime?.minutes ?? 59;
    return new Date(
      Date.UTC(dueDate.year, dueDate.month - 1, dueDate.day, hh, mm) - MANILA_OFFSET_MS,
    ).toISOString();
  };

  const workTypeToActivity = (workType?: string) => {
    switch (workType) {
      case "ASSIGNMENT":
        return "assignment";
      case "SHORT_ANSWER_QUESTION":
      case "MULTIPLE_CHOICE_QUESTION":
        return "quiz";
      default:
        return "other";
    }
  };

  for (const { course, target } of matched) {
    // --- coursework ---
    let cwList: Array<{
      id: string;
      title?: string;
      description?: string;
      workType?: string;
      state?: string;
      dueDate?: { year?: number; month?: number; day?: number };
      dueTime?: { hours?: number; minutes?: number };
      alternateLink?: string;
    }> = [];
    const cwRes = await googleGet(`courses/${course.id}/courseWork?pageSize=100`);
    if (cwRes.ok) {
      cwList = (await cwRes.json()).courseWork ?? [];
    } else if (cwRes.status === 403) {
      // e.g. a teacher's class without the students.readonly scope: the
      // class matches, but its coursework is not readable with student
      // scopes. Announcements may still import.
      skippedCoursework.push({
        classroomName: course.name ?? course.id,
        reason: "coursework not granted by the current Google scopes",
      });
    } else if (cwRes.status !== 404) {
      skippedCoursework.push({
        classroomName: course.name ?? course.id,
        reason: `google_${cwRes.status} on courseWork`,
      });
    }
    for (const cw of cwList) {
      if (cw.state && cw.state !== "PUBLISHED") continue;
      try {
        await importItem({
          sourceKey: `google_classroom:${course.id}:${cw.id}`,
          externalCourseId: course.id,
          externalItemId: cw.id,
          title: cw.title ?? "Untitled coursework",
          description: cw.description ?? null,
          activityType: workTypeToActivity(cw.workType),
          courseId: target.courseId,
          sectionId: target.sectionId,
          dueAt: dueIso(cw.dueDate, cw.dueTime),
          sourceMeta: {
            courseName: course.name,
            sectionLabel: course.section ?? null,
            workType: cw.workType ?? null,
            alternateLink: cw.alternateLink ?? null,
          },
        });
      } catch (e) {
        return await fail(`coursework import: ${(e as Error).message}`);
      }
    }

    // --- announcements ---
    const annRes = await googleGet(`courses/${course.id}/announcements?pageSize=100`);
    if (annRes.ok) {
      const anns: Array<{
        id: string;
        text?: string;
        creationTime?: string;
        updateTime?: string;
      }> = (await annRes.json()).announcements ?? [];
      for (const ann of anns) {
        try {
          await importItem({
            sourceKey: `google_classroom_announcement:${course.id}:${ann.id}`,
            externalCourseId: course.id,
            externalItemId: ann.id,
            title: truncate((ann.text ?? "Announcement").replace(/\s+/g, " ").trim(), 80),
            description: ann.text ?? null,
            activityType: "announcement",
            courseId: target.courseId,
            sectionId: target.sectionId,
            // An announcement has no due date; pin it to its post time so
            // it lands on the calendar day it was made.
            dueAt: ann.creationTime ?? ann.updateTime ?? nowIso(),
            sourceMeta: {
              courseName: course.name,
              sectionLabel: course.section ?? null,
              kind: "announcement",
            },
          });
        } catch (e) {
          return await fail(`announcement import: ${(e as Error).message}`);
        }
      }
    } else if (annRes.status !== 404 && annRes.status !== 403) {
      skippedCoursework.push({
        classroomName: course.name ?? course.id,
        reason: `google_${annRes.status} on announcements`,
      });
    }
  }

  // ---- 7. Record the outcome in the connection registry. ----------------
  const partial = skippedCourses.length > 0 || skippedCoursework.length > 0;
  await setConnection({
    status: "connected",
    last_sync_at: nowIso(),
    last_sync_status: partial ? "partial" : "ok",
    last_sync_error: partial
      ? truncate(
          [
            skippedCourses.length > 0
              ? `skipped classes: ${skippedCourses.map((s) => s.classroomName).join(", ")}`
              : null,
            skippedCoursework.length > 0
              ? `coursework unreadable: ${skippedCoursework.map((s) => s.classroomName).join(", ")}`
              : null,
          ].filter(Boolean).join(" · "),
          500,
        )
      : null,
  });

  return json({
    status: partial ? "partial" : "ok",
    imported,
    updated,
    classesTotal: courses.length,
    matchedClasses: matched.map((m) => ({
      classroomName: m.course.name ?? m.course.id,
      courseCode: m.target.courseCode,
      sectionName: m.target.sectionName,
    })),
    skippedClasses: skippedCourses,
    skippedCoursework,
    syncedAt: nowIso(),
  });
});
