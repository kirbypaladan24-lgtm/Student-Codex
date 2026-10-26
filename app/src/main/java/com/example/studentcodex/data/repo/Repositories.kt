package com.example.studentcodex.data.repo

import com.example.studentcodex.domain.model.Activity
import com.example.studentcodex.domain.model.StudentProfile

/**
 * Repository boundaries. UI and ViewModels only ever see these interfaces -
 * never Supabase, Room, or HTTP details (see docs/ARCHITECTURE.md section 2).
 */

/**
 * An unsaved manual activity (Phase 4). The repository assigns the identity
 * and audit columns; validation (title non-blank) happens in the ViewModel.
 *
 * New activities are always CLASS activities: [sectionId] is one of the
 * signed-in user's own sections (student enrollment OR taught section) and
 * the row publishes STRAIGHT to the cohort calendar for the teacher and the
 * classmates - there is no approval queue anywhere: reporting IS
 * publishing. [courseId] is the section's own course (kept in sync by the
 * ViewModel) so every list can label the class.
 *
 * [teacherConfirmed] mirrors the creator's enrollment role in the target
 * section: a TEACHER saves as verification_status 'teacher-confirmed' (the
 * guard trigger requires exactly that), a student as 'student-reported'.
 */
data class ActivityDraft(
    val title: String,
    val description: String? = null,
    val type: String = "other",
    /** Minutes; null = unknown. */
    val effortMinutes: Int? = null,
    /** Null = no deadline. */
    val dueAt: java.time.Instant? = null,
    /** True only while EDITING a pre-existing personal row (scope immutable). */
    val isPersonal: Boolean = false,
    /** The own section this class activity belongs to. */
    val sectionId: String? = null,
    /** The section's course id - labels the activity in every list. */
    val courseId: String? = null,
    /** Creator teaches the target section -> 'teacher-confirmed' row. */
    val teacherConfirmed: Boolean = false,
)

/**
 * One of the signed-in user's own sections - student enrollment or taught
 * section - the ONLY cohort question the editor ever asks. The section row
 * itself encodes the program / year / block cohort (e.g. "BSCS 2-A"), and
 * the profile already carries campus, program, year and section for
 * display: nothing about the cohort is asked again. [role] is the
 * enrollment role ("student" | "teacher") and decides the row's
 * verification_status on save.
 */
data class MySection(
    val sectionId: String,
    val sectionName: String,
    val courseId: String?,
    val courseCode: String?,
    val courseTitle: String?,
    /** Enrollment role in this section: "student" or "teacher". */
    val role: String = "student",
)

interface ActivityRepository {

    /** Activities visible to the current student, unsorted. */
    suspend fun getActivities(): List<Activity>

    /**
     * Creates a manual class activity for the current user. draft.sectionId
     * publishes it straight to the cohort calendar: teacher creators save
     * as 'teacher-confirmed', students as 'student-reported' (both
     * RLS/guard-checked against the creator's own enrollment role).
     * Returns the saved activity as the server now sees it.
     */
    suspend fun createActivity(draft: ActivityDraft): Activity

    /**
     * Updates an editable manual activity the student owns. Only the
     * mutable content fields change (title, description, type, effort,
     * due date); ownership, scope and verification are server-enforced.
     */
    suspend fun updateActivity(id: String, draft: ActivityDraft): Activity

    /**
     * Soft-deletes a manual activity the student owns. It disappears from
     * every list immediately (deleted rows are filtered server-side). For
     * a class report this is the student's own withdraw - the entry also
     * disappears from the teacher's calendar.
     */
    suspend fun deleteActivity(id: String)

    /**
     * The signed-in user's own sections - student enrollments AND taught
     * sections - with their course labels and enrollment role: the picker
     * behind the class-activity audience. Empty while signed out; empty
     * for accounts without any enrollment.
     */
    suspend fun getMySections(): List<MySection>
}

interface CommunityRepository {
    /** Recent public posts, newest first. */
    suspend fun getRecentPosts(): List<com.example.studentcodex.domain.model.CommunityPost>

    /**
     * Optimistically toggles the current user's like on a post. Returns
     * the updated post.
     */
    suspend fun toggleLike(postId: String): com.example.studentcodex.domain.model.CommunityPost

    /** Comments on a post, oldest first. */
    suspend fun getComments(postId: String): List<com.example.studentcodex.domain.model.Comment>

    /**
     * Adds a comment as the current user. Returns the created comment; the
     * caller refreshes the post's comment count from its own state.
     */
    suspend fun addComment(postId: String, text: String): com.example.studentcodex.domain.model.Comment

    /**
     * Creates a post as the current user. Either [text] or [imageUri] must
     * carry content. Returns the created post (zero likes, zero comments).
     */
    suspend fun createPost(text: String, imageUri: String?): com.example.studentcodex.domain.model.CommunityPost

    /**
     * Downloads one attached image's bytes from the private community
     * bucket by its storage path ({uid}/{postId}/{file} — what
     * [CommunityPost.imageUri] carries for saved posts). Null when the
     * object is missing or unreadable; the UI then renders text only.
     *
     * Default null keeps fakes and tests compiling: only the Supabase
     * repository has a bucket behind it.
     */
    suspend fun downloadPostImage(path: String): ByteArray? = null
}

interface ProfileRepository {
    /** The signed-in student's profile. */
    suspend fun getProfile(): StudentProfile
}

/**
 * Files content reports for the moderation queue (Phase 9). Implemented by
 * the Supabase community repository (content_reports table); fakes in tests.
 */
interface ContentReportRepository {
    /**
     * Files a report against a post. [reason] is one of the database's
     * check-constraint values (spam/harassment/off-topic/inappropriate/
     * other); [details] is optional context for moderators.
     */
    suspend fun reportPost(postId: String, reason: String, details: String?)
}

/**
 * Google Classroom connection STATE for the signed-in student (Phase
 * 6-ready read side). Tokens never cross this boundary: they live only in
 * the Edge Function's server-side store (plan Phase 6).
 */
interface ClassroomConnectionRepository {

    /** Current connection state; never throws for "no row" — that is a state. */
    suspend fun getConnectionState(): ClassroomConnectionState

    /**
     * Requests a disconnect of the linked Classroom account (the only
     * client-initiated transition the database allows; token revocation is
     * the sync service's job).
     */
    suspend fun disconnect()
}

/**
 * Summary of one Google Classroom sync run, straight from the
 * classroom-sync Edge Function's JSON response.
 */
data class ClassroomSyncResult(
    /** "ok" | "partial" | "failed". */
    val status: String,
    /** Items imported for the first time this run. */
    val imported: Int = 0,
    /** Known items whose content was refreshed this run. */
    val updated: Int = 0,
    /** ACTIVE classes the Google account participates in. */
    val classesTotal: Int = 0,
    /** Classes matched to a local section (successfully imported). */
    val matchedClasses: List<String> = emptyList(),
    /** Classes skipped because no local course code matched. */
    val skippedClasses: List<String> = emptyList(),
    /** Machine-readable error tag when status is "failed". */
    val error: String? = null,
    /** Human-readable failure text from the function, when provided. */
    val message: String? = null,
)

/**
 * Google Classroom sync: imports the user's classes' coursework and
 * announcements as read-only 'imported' activities through the Edge
 * Function. The Google access token is requested per run (Authorization
 * API) and used in transit only — never stored.
 */
interface ClassroomSyncRepository {

    /**
     * Runs one import with a freshly authorized Google access token.
     * Throws on network failure; a completed run returns the summary
     * (including a "failed" status when Google or the database refused).
     */
    suspend fun sync(googleAccessToken: String): ClassroomSyncResult
}

/** Connection state rendered by the Profile > Connections card. */
sealed interface ClassroomConnectionState {
    /** Nobody is signed in (guest mode). */
    data object NotSignedIn : ClassroomConnectionState

    /** No connection row exists — the integration was never linked. */
    data object NeverConnected : ClassroomConnectionState

    /** Linked: email known, last sync time/status available. */
    data class Connected(
        val googleEmail: String?,
        val lastSyncAt: java.time.Instant?,
        val lastSyncStatus: String?,
        val lastSyncError: String?,
    ) : ClassroomConnectionState

    /** The user revoked access on the Google side; reconnect needed. */
    data object Revoked : ClassroomConnectionState

    /** Stored authorization expired; the sync service needs a re-auth. */
    data object Expired : ClassroomConnectionState

    /** Previously linked, now deliberately unlinked. */
    data object Disconnected : ClassroomConnectionState
}
