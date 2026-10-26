package com.example.studentcodex.data.school

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A degree program offered by the university at ONE campus (migration 0012
 * gives every `programs` row a campus: BSHM, BSF and BSIT are each offered
 * at two campuses, so the same program name can appear twice). The sign-up
 * form shows these as dropdown options, never free text.
 */
data class SchoolProgram(
    val id: String,
    val name: String,
    /** The campus offering this program (registry row reference). */
    val campusId: String,
)

/**
 * The shape every course code must have: a letter first, then letters,
 * digits, spaces and hyphens only (1–32 chars) — exactly the shape the
 * enrollment helper enforces server-side (migration 0019). Catches "!!!"
 * or a pasted subject title while accepting every real code format
 * (CC1, CS 101, MATH-111 …).
 */
fun looksLikeCourseCode(input: String): Boolean =
    input.isNotEmpty() && input.length <= 32 &&
        input.first().isLetter() &&
        input.all { it.isLetterOrDigit() || it == ' ' || it == '-' }

/** A campus of the university (seeded registry row). */
data class Campus(
    val id: String,
    val name: String,
)

/**
 * A year level (1st..5th). [level] is the integer stored on
 * `profiles.year_level`; [label] is the display text ("1st").
 */
data class YearLevel(
    val level: Int,
    val label: String,
)

/** A registrar-style block label (A..F, 1..3). */
data class YearSection(
    val id: String,
    val name: String,
)

/**
 * A course (subject) the school offers, e.g. `CS 101 — Introduction to
 * Computing`. The sign-up forms reference courses by their CODE only: a
 * KNOWN code canonicalizes to the registry row, an unknown one is accepted
 * as typed — the catalog can never list every subject a real semester
 * offers, so the server creates the course from the code at save time
 * (migration 0019). Only input that cannot be a code at all is rejected.
 */
data class CourseInfo(
    val id: String,
    val code: String,
    val title: String,
)

/** The registry lists backing the sign-up and completion forms. */
data class SchoolDirectory(
    val programs: List<SchoolProgram>,
    val campuses: List<Campus>,
    val yearLevels: List<YearLevel>,
    val yearSections: List<YearSection>,
    /** Course catalog; empty when the fetch failed or is not deployed yet. */
    val courses: List<CourseInfo> = emptyList(),
) {
    /**
     * The course behind a typed code, case-insensitive and whitespace-
     * tolerant ("cs101", "  CS 101 " both resolve). Null when the registry
     * has no such course — or when the course catalog itself is unavailable
     * (empty [courses]): the caller decides how lenient to be then.
     */
    fun courseByCode(code: String?): CourseInfo? {
        val needle = code?.trim()?.replace(Regex("\\s+"), " ")?.uppercase() ?: return null
        if (needle.isEmpty()) return null
        return courses.firstOrNull { course ->
            course.code.replace(Regex("\\s+"), " ").uppercase() == needle
        }
    }

    /**
     * Normalizes a typed course code for the class list: the CANONICAL code
     * when the registry knows it (case/whitespace-insensitive), the cleaned
     * input when it does not (the server creates the course from it), null
     * when the input cannot be a code at all. Blank input is null too —
     * callers treat that as "nothing to add".
     */
    fun normalizeCourseCode(input: String): String? {
        val cleaned = input.trim().replace(Regex("\\s+"), " ")
        if (!looksLikeCourseCode(cleaned)) return null
        return courseByCode(cleaned)?.code ?: cleaned
    }

    /**
     * The programs the Program dropdown should list: only the chosen
     * campus offerings when a campus is selected, or the all-campus list
     * while the user has not picked one yet (they may pick the program
     * first; every program is then selectable).
     */
    fun programsForCampus(campusId: String?): List<SchoolProgram> =
        campusId?.let { id -> programs.filter { it.campusId == id } } ?: programs

    /**
     * Dropdown label for [program]. Inside one campus the name is already
     * unambiguous; in the all-campus list a program offered at several
     * campuses gets the campus appended so the rows stay distinguishable.
     */
    fun programLabel(program: SchoolProgram, campusId: String?): String {
        if (campusId != null) return program.name
        val offeredElsewhereToo = programs.count { it.name == program.name } > 1
        if (!offeredElsewhereToo) return program.name
        val campusName = campuses.firstOrNull { it.id == program.campusId }?.name
            ?: return program.name
        return "${program.name} — $campusName"
    }
}

/**
 * True when [programId] may stay selected for [campusId]: with no campus
 * chosen every program is selectable; with a campus chosen the program must
 * be one of its offerings. A null program is always fine. Both the auth and
 * settings forms use this to drop a program the new campus does not offer.
 */
fun programOfferedAt(
    directory: SchoolDirectory?,
    campusId: String?,
    programId: String?,
): Boolean =
    campusId == null || programId == null ||
        directory?.programs?.any { it.id == programId && it.campusId == campusId } == true

/**
 * The user's four sign-up choices. All fields are required by the time this
 * reaches a repository: the forms only accept values that came out of a
 * [SchoolDirectory], so they are always valid registry references.
 *
 * Travels as user metadata on the auth request; the database's
 * `handle_new_user` / `handle_user_profile_sync` triggers copy it onto the
 * profiles row.
 */
data class SchoolSelection(
    val programId: String,
    val yearLevel: Int,
    val sectionId: String,
    val campusId: String,
)

/**
 * Loads the school registry (programs, campuses, year levels, year
 * sections) from Supabase. The tables are anon-readable by RLS design:
 * the sign-up form needs them before any account exists.
 */
interface SchoolDirectoryRepository {

    /** Fetches the registry lists. Throws on network/API trouble. */
    suspend fun load(): SchoolDirectory
}

/**
 * Supabase-backed registry fetch with an in-memory cache: the lists are
 * near-static reference data, so one fetch serves the whole session and
 * short-lived retries hit the cache instead of the network.
 *
 * Rows are parsed from the response's raw JSON with the runtime
 * kotlinx-serialization API (parseToJsonElement) rather than generated
 * @Serializable serializers: the project builds with AGP 9's built-in
 * Kotlin compiler, which does not run the serialization compiler plugin's
 * codegen. Same style as the auth user-metadata parsing in
 * [com.example.studentcodex.data.auth.SupabaseAuthRepository].
 */
class SupabaseSchoolDirectoryRepository(
    private val client: SupabaseClient,
) : SchoolDirectoryRepository {

    @Volatile
    private var cache: SchoolDirectory? = null

    @Volatile
    private var cachedAtMillis: Long = 0

    override suspend fun load(): SchoolDirectory {
        cache?.takeIf { isFresh() }?.let { return it }
        val directory = try {
            fetchDirectory()
        } catch (e: Exception) {
            // The ViewModel maps this to a retryable error; the log keeps
            // the root cause (DNS, serialization, API shape) diagnosable.
            Log.w(TAG, "School registry fetch failed", e)
            throw e
        }
        cache = directory
        cachedAtMillis = System.currentTimeMillis()
        return directory
    }

    private suspend fun fetchDirectory(): SchoolDirectory {
        val programs = client.postgrest.from("programs")
            .select(columns = Columns.list("id", "program_name", "campus_id"))
            .data
            .parseRows()
            .mapNotNull { row ->
                SchoolProgram(
                    id = row.stringValue("id") ?: return@mapNotNull null,
                    name = row.stringValue("program_name") ?: return@mapNotNull null,
                    campusId = row.stringValue("campus_id") ?: return@mapNotNull null,
                )
            }
            .sortedBy { it.name }

        val campuses = client.postgrest.from("campuses")
            .select(columns = Columns.list("id", "name", "sort_order"))
            .data
            .parseRows()
            .mapNotNull { row ->
                Campus(
                    id = row.stringValue("id") ?: return@mapNotNull null,
                    name = row.stringValue("name") ?: return@mapNotNull null,
                ) to (row.intValue("sort_order") ?: 0)
            }
            .sortedBy { it.second }
            .map { it.first }

        val yearLevels = client.postgrest.from("year_levels")
            .select(columns = Columns.list("level", "label"))
            .data
            .parseRows()
            .mapNotNull { row ->
                YearLevel(
                    level = row.intValue("level") ?: return@mapNotNull null,
                    label = row.stringValue("label") ?: return@mapNotNull null,
                )
            }
            .sortedBy { it.level }

        val yearSections = client.postgrest.from("year_sections")
            .select(columns = Columns.list("id", "name", "sort_order"))
            .data
            .parseRows()
            .mapNotNull { row ->
                YearSection(
                    id = row.stringValue("id") ?: return@mapNotNull null,
                    name = row.stringValue("name") ?: return@mapNotNull null,
                ) to (row.intValue("sort_order") ?: 0)
            }
            .sortedBy { it.second }
            .map { it.first }

        // Course catalog for the sign-up "your courses" entry. Best-effort
        // by design: the table is anon-readable only once migration 0017 is
        // deployed, and a not-yet-pushed database must never break the whole
        // registry (the dropdowns above still work; codes are then accepted
        // unvalidated and filtered server-side instead).
        val courses = try {
            client.postgrest.from("courses")
                .select(columns = Columns.list("id", "code", "title"))
                .data
                .parseRows()
                .mapNotNull { row ->
                    CourseInfo(
                        id = row.stringValue("id") ?: return@mapNotNull null,
                        code = row.stringValue("code") ?: return@mapNotNull null,
                        title = row.stringValue("title") ?: return@mapNotNull null,
                    )
                }
                .sortedBy { it.code }
        } catch (e: Exception) {
            Log.w(TAG, "Course catalog unavailable — codes will pass unvalidated", e)
            emptyList()
        }

        return SchoolDirectory(
            programs = programs,
            campuses = campuses,
            yearLevels = yearLevels,
            yearSections = yearSections,
            courses = courses,
        ).also {
            Log.i(
                TAG,
                "School registry loaded: ${it.programs.size} programs, " +
                    "${it.campuses.size} campuses, ${it.yearLevels.size} year levels, " +
                    "${it.yearSections.size} sections, ${it.courses.size} courses",
            )
        }
    }

    private fun isFresh(): Boolean =
        System.currentTimeMillis() - cachedAtMillis < CACHE_TTL_MILLIS

    // ------------------------------------------------------ JSON parsing

    /** Parses a PostgREST response body as an array of row objects. */
    private fun String.parseRows(): List<JsonObject> {
        val element = runCatching { Json.parseToJsonElement(this) }.getOrNull()
        return (element as? JsonArray)
            ?.filterIsInstance<JsonObject>()
            .orEmpty()
    }

    private fun JsonObject.stringValue(key: String): String? =
        get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.intValue(key: String): Int? =
        get(key)?.jsonPrimitive?.intOrNull

    private companion object {
        const val TAG = "SchoolDirectory"

        /** Reference data changes at most between semesters, not minutes. */
        const val CACHE_TTL_MILLIS = 10 * 60 * 1000L
    }
}
