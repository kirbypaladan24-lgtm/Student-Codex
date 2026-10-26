package com.example.studentcodex.data.supabase

import com.example.studentcodex.data.repo.ActivityDraft
import com.example.studentcodex.data.repo.ActivityRepository
import com.example.studentcodex.data.repo.MySection
import com.example.studentcodex.domain.model.Activity
import com.example.studentcodex.domain.model.ActivitySource
import com.example.studentcodex.domain.model.ActivityType
import com.example.studentcodex.domain.model.Course
import com.example.studentcodex.domain.model.VerificationStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Supabase-backed Activity Beacon repository (Phases 4 + 5).
 *
 * Reads go through the `activities` table (RLS returns exactly the rows the
 * signed-in student may see: their own + participants + section/course
 * scope through enrollments; soft-deleted rows are visible only to their
 * creator and filtered out here). Manual writes are student-reported rows
 * the student owns: personal planner entries, or CLASS REPORTS published
 * straight to the section's cohort calendar (migration 0015 - a report IS
 * the activity; there is no approval queue anywhere). The section picker
 * comes from the student's own enrollments, so the cohort (campus /
 * program / year / block) is always derived, never asked.
 *
 * Row parsing uses the runtime JSON API (see [PostgrestJson] KDoc for why
 * the project does not use @Serializable).
 */
class SupabaseActivityRepository(
    private val client: SupabaseClient,
) : ActivityRepository {

    private val auth get() = client.auth

    override suspend fun getActivities(): List<Activity> {
        // One join query: activity columns + course code/title for labels.
        val body = client.postgrest.from("activities")
            .select(
                columns = Columns.list(
                    "id", "title", "description", "activity_type", "due_at",
                    "estimated_effort_minutes", "scope", "verification_status",
                    "deleted_at",
                    "course:courses(id,code,title)",
                    "activity_sources(provider)",
                ),
            ) {
                filter { filter("deleted_at", FilterOperator.IS, null) }
                order("due_at", Order.ASCENDING)
            }
            .data
        return PostgrestJson.parseRows(body).mapNotNull { row -> row.toActivity() }
    }

    override suspend fun createActivity(draft: ActivityDraft): Activity {
        val uid = auth.currentUserOrNull()?.id
            ?: error("Cannot create an activity while signed out")
        val body = client.postgrest.from("activities")
            .insert(
                buildJsonObject {
                    put("title", draft.title)
                    put("description", draft.description)
                    put("activity_type", draft.type)
                    put("due_at", draft.dueAt?.toString())
                    put("estimated_effort_minutes", draft.effortMinutes)
                    put("scope", if (draft.sectionId != null) "section" else "personal")
                    put("section_id", draft.sectionId)
                    put("course_id", draft.sectionId?.let { draft.courseId })
                    // Guard trigger (migration 0015): a SECTION row must
                    // carry the verification its creator's enrollment can
                    // back - 'teacher-confirmed' for a teacher of the
                    // section, 'student-reported' for an enrolled student.
                    // Sending student-reported as a teacher raised the
                    // reported "...requires the creator to be enrolled in
                    // that section" error.
                    put(
                        "verification_status",
                        if (draft.teacherConfirmed) "teacher-confirmed" else "student-reported",
                    )
                    put("created_by_user_id", uid)
                },
            ) {
                // RETURNING: the inserted row (PostgREST semantics).
                select(Columns.list(ACTIVITY_COLUMNS))
            }
            .data
        val row = PostgrestJson.parseRows(body).firstOrNull()
            ?: error("Server did not return the created activity")
        return row.toActivity(
            source = ActivitySource.MANUAL,
            course = null,
            isPersonal = draft.sectionId == null,
        ) ?: error("Server returned an incomplete activity row")
    }

    override suspend fun updateActivity(id: String, draft: ActivityDraft): Activity {
        val body = client.postgrest.from("activities")
            .update(
                buildJsonObject {
                    put("title", draft.title)
                    put("description", draft.description)
                    put("activity_type", draft.type)
                    put("due_at", draft.dueAt?.toString())
                    put("estimated_effort_minutes", draft.effortMinutes)
                },
            ) {
                filter { eq("id", id) }
                select(Columns.list(ACTIVITY_COLUMNS))
            }
            .data
        val row = PostgrestJson.parseRows(body).firstOrNull()
            ?: error("Activity not found or not editable by you")
        return row.toActivity(
            source = ActivitySource.MANUAL,
            course = null,
            isPersonal = draft.isPersonal,
        ) ?: error("Server returned an incomplete activity row")
    }

    override suspend fun deleteActivity(id: String) {
        // Soft delete: the row gets deleted_at and disappears from feeds.
        client.postgrest.from("activities")
            .update(buildJsonObject { put("deleted_at", java.time.Instant.now().toString()) }) {
                filter { eq("id", id) }
            }
        // No RETURNING needed; a miss is a silent no-op that the next
        // list refresh reveals honestly.
    }

    /**
     * The signed-in user's own sections with course labels - student
     * enrollments AND taught sections. This is the ONLY cohort question
     * the editor asks: which class. Everything else (campus, program,
     * year, block) is already on the profile and baked into the section
     * itself. The row's role drives the saved verification_status.
     */
    override suspend fun getMySections(): List<MySection> {
        val uid = auth.currentUserOrNull()?.id ?: return emptyList()
        val body = client.postgrest.from("enrollments")
            .select(
                columns = Columns.list(
                    "section_id",
                    "role",
                    "section:sections(id, name, course_id, course:courses(id, code, title))",
                ),
            ) {
                filter { eq("user_id", uid) }
                order("enrolled_at", Order.ASCENDING)
            }
            .data
        return PostgrestJson.parseRows(body).mapNotNull { row ->
            val section = row.get("section") as? kotlinx.serialization.json.JsonObject
                ?: return@mapNotNull null
            val course = section.get("course") as? kotlinx.serialization.json.JsonObject
            MySection(
                sectionId = section.stringValue("id") ?: row.stringValue("section_id")
                    ?: return@mapNotNull null,
                sectionName = section.stringValue("name") ?: "",
                courseId = section.stringValue("course_id") ?: course?.stringValue("id"),
                courseCode = course?.stringValue("code"),
                courseTitle = course?.stringValue("title"),
                role = row.stringValue("role") ?: "student",
            )
        }
    }

    /**
     * Maps one joined row to the domain activity. Determined fields
     * (source/verification) come from the row itself; the fallbacks keep
     * old call sites compiling.
     */
    private fun kotlinx.serialization.json.JsonObject.toActivity(
        source: ActivitySource? = null,
        course: Course? = null,
        isPersonal: Boolean = false,
    ): Activity? {
        val id = stringValue("id") ?: return null
        val title = stringValue("title") ?: return null
        val verification = when (stringValue("verification_status")) {
            "imported" -> VerificationStatus.IMPORTED
            "teacher-confirmed" -> VerificationStatus.TEACHER_CONFIRMED
            "student-reported" -> VerificationStatus.STUDENT_REPORTED
            else -> VerificationStatus.NEEDS_REVIEW
        }
        // Embedded course object (PostgREST one-to-one join).
        val embeddedCourse = get("course") as? kotlinx.serialization.json.JsonObject
        val mappedCourse = course
            ?: embeddedCourse?.let { c ->
                Course(
                    id = c.stringValue("id") ?: return@let null,
                    code = c.stringValue("code") ?: "",
                    title = c.stringValue("title") ?: "",
                )
            }
        // Embedded sources array: presence of a google_classroom source row
        // marks the activity as Classroom-imported evidence (dedup rule 1).
        val sources = get("activity_sources") as? kotlinx.serialization.json.JsonArray
        val hasClassroomSource = sources
            ?.filterIsInstance<kotlinx.serialization.json.JsonObject>()
            ?.any { it.stringValue("provider") == "google_classroom" } == true
        val resolvedSource = source ?: when {
            hasClassroomSource -> ActivitySource.GOOGLE_CLASSROOM
            verification == VerificationStatus.STUDENT_REPORTED -> ActivitySource.STUDENT_REPORT
            else -> ActivitySource.MANUAL
        }
        val scope = stringValue("scope") ?: "personal"
        return Activity(
            id = id,
            title = title,
            description = stringValue("description"),
            type = when (stringValue("activity_type")) {
                "assignment" -> ActivityType.ASSIGNMENT
                "quiz" -> ActivityType.QUIZ
                "exam" -> ActivityType.EXAM
                "project" -> ActivityType.PROJECT
                "announcement" -> ActivityType.ANNOUNCEMENT
                else -> ActivityType.OTHER
            },
            course = mappedCourse,
            dueAt = instantValue("due_at"),
            effortMinutes = intValue("estimated_effort_minutes"),
            source = resolvedSource,
            verification = verification,
            isPersonal = if (isPersonal) true else scope == "personal",
        )
    }

    private fun kotlinx.serialization.json.JsonObject.stringValue(key: String) =
        PostgrestJson.run { stringValue(key) }

    private fun kotlinx.serialization.json.JsonObject.intValue(key: String) =
        PostgrestJson.run { intValue(key) }

    private fun kotlinx.serialization.json.JsonObject.instantValue(key: String) =
        PostgrestJson.run { instantValue(key) }

    private companion object {
        const val TAG = "SupabaseActivities"
        const val ACTIVITY_COLUMNS =
            "id, title, description, activity_type, due_at, estimated_effort_minutes, scope, verification_status"
    }
}
