package com.example.studentcodex.data.auth

import com.example.studentcodex.data.school.SchoolSelection
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Who is currently using the app. A guest session exists only in memory and
 * never touches Supabase — it is the explicit "explore without an account"
 * path for trying the app.
 */
data class SessionInfo(
    val userId: String?,
    val email: String?,
    val isGuest: Boolean = false,
    /** "First Last" — set as soon as the user has told us their name. */
    val displayName: String? = null,
    /**
     * True once first/last name were provided (at sign-up, or through the
     * profile-completion step of a first Google sign-in). False means the
     * account exists but its profile has no name/username yet.
     */
    val profileCompleted: Boolean = true,
    /** True when the account can also sign in with email + password. */
    val hasEmailCredential: Boolean = false,
)

/** Outcome of creating a new account. */
sealed interface SignUpResult {

    /** Account created AND already signed in (project auto-confirms emails). */
    data class SignedIn(val session: SessionInfo) : SignUpResult

    /**
     * Account created, but the hosted project requires email confirmation
     * before a session can start. The user must click the link in their
     * inbox, then sign in.
     */
    data object ConfirmationRequired : SignUpResult
}

/**
 * Result of exchanging a Google ID token: the live session plus what the
 * account still needs. A [GoogleSignInResult.session] whose
 * [SessionInfo.profileCompleted] is false belongs to a first-time Google
 * user who has not entered a name yet.
 */
data class GoogleSignInResult(
    val session: SessionInfo,
    /** Google's profile name when Supabase stored it — prefills the form. */
    val suggestedName: String? = null,
)

/**
 * Sign-up rejected because the email or the Google identity is already
 * registered. Mapped to a friendly, actionable message in the UI.
 */
class DuplicateAccountException(message: String? = null) : Exception(message)

/**
 * The school references currently stored on the user's profile row, as
 * returned by [AuthRepository.loadSchoolSelection]. A null field means the
 * account predates that detail (or has never set it) - the Settings form
 * treats nulls as unchosen.
 */
data class StoredSchoolSelection(
    val programId: String? = null,
    val yearLevel: Int? = null,
    val sectionId: String? = null,
    val campusId: String? = null,
)

/**
 * Authentication boundary. UI and ViewModels only ever see this interface —
 * never the Supabase client (docs/ADR-001-supabase-first.md). Phase 4 wires
 * the Supabase implementation below; tests use fakes.
 */
interface AuthRepository {

    /** Session restored from persistent storage, or null when signed out. */
    suspend fun currentSession(): SessionInfo?

    /** Signs in with email + password. Throws on bad credentials or network trouble. */
    suspend fun signIn(email: String, password: String): SessionInfo

    /**
     * Creates an account. The names, school details (program, year
     * level, section, campus — chosen from registry dropdowns) and the
     * COURSE CODES of the student's subjects travel as user metadata so
     * the database's on_auth_user_created trigger can fill the profiles
     * row AND enroll the student in the sections behind the codes (the
     * class list the manual activity editor picks from). See
     * [SignUpResult] for the confirmation flow.
     */
    suspend fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        school: SchoolSelection,
        courseCodes: List<String> = emptyList(),
    ): SignUpResult

    /**
     * Signs in with a Google ID token obtained from Android Credential
     * Manager. Supabase creates the account on first use (the profiles
     * trigger fills the row), so this covers both "sign in" and "sign up".
     * A brand-new account reports [SessionInfo.profileCompleted] false —
     * the caller should collect the name (and a password for email
     * sign-in) via [completeGoogleProfile]. Throws
     * [DuplicateAccountException] when the email is already in use.
     */
    suspend fun signInWithGoogleIdToken(idToken: String): GoogleSignInResult

    /**
     * Completes a Google-created account: stores the names, school
     * details and the student's COURSE CODES in user metadata (the
     * on_auth_user_profile_sync trigger fills the profile, derives the
     * username, copies the school references, and enrolls the account in
     * the sections behind the codes) and — for accounts without one — sets
     * the given password so the user can also sign in with email +
     * password.
     */
    suspend fun completeGoogleProfile(
        firstName: String,
        lastName: String,
        password: String?,
        school: SchoolSelection,
        courseCodes: List<String> = emptyList(),
    ): SessionInfo

    /** Revokes the session (remote + local storage). */
    suspend fun signOut()

    /**
     * Reads the signed-in user's stored school details (program, year
     * level, section, campus) from their profiles row. Returns null when
     * nobody is signed in or the row is missing; individual fields may be
     * null on legacy accounts (see [StoredSchoolSelection]).
     */
    suspend fun loadSchoolSelection(): StoredSchoolSelection?

    /**
     * Replaces the four school references on the profiles row, and (re-)
     * enrolls the account in the sections behind the given course codes —
     * idempotent server-side. Travels as auth user metadata
     * (profile_completed + the four ids + the codes) so the
     * handle_user_profile_sync trigger coalesces it onto the row; names
     * and username are untouched because they are absent from the payload.
     */
    suspend fun updateSchoolDetails(
        school: SchoolSelection,
        courseCodes: List<String> = emptyList(),
    )

    /** Sets a new password for the signed-in account (email sign-in). */
    suspend fun changePassword(newPassword: String)

    /**
     * The distinct COURSE CODES of the signed-in student's currently
     * enrolled sections (their class list) — used to prefill the Settings
     * school sheet's course editor. Empty when nobody is signed in.
     */
    suspend fun loadMyCourseCodes(): List<String>
}

/**
 * Supabase-backed email/password authentication. Sessions persist across app
 * restarts through the Auth plugin's default SettingsSessionManager
 * (SharedPreferences on Android) — no extra code needed for "stay signed in".
 */
class SupabaseAuthRepository(
    private val client: SupabaseClient,
) : AuthRepository {

    private val auth: Auth get() = client.auth

    override suspend fun currentSession(): SessionInfo? {
        // At cold start the plugin sits in Initializing while it loads the
        // stored session. A *missing* session never leaves Initializing, so
        // wait a bounded time for an authenticated status, then fall back to
        // the snapshot (null when nothing was stored).
        val status = withTimeoutOrNull(SESSION_RESTORE_TIMEOUT_MS) {
            auth.sessionStatus.first { it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated }
        }
        val session = when (status) {
            is SessionStatus.Authenticated -> status.session
            else -> auth.currentSessionOrNull()
        }
        return session?.toSessionInfo()
    }

    override suspend fun signIn(email: String, password: String): SessionInfo {
        // Distinct local names: inside the config lambda the receiver's own
        // `email`/`password` properties would shadow these parameters.
        val loginEmail = email.trim()
        val loginPassword = password
        try {
            auth.signInWith(Email) {
                this.email = loginEmail
                this.password = loginPassword
            }
        } catch (e: Exception) {
            throw asDuplicateIfApplicable(e)
        }
        return auth.currentSessionOrNull()?.toSessionInfo()
            ?: error("Sign-in succeeded but no session is active")
    }

    override suspend fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        school: SchoolSelection,
        courseCodes: List<String>,
    ): SignUpResult {
        val signupEmail = email.trim()
        val signupPassword = password
        val givenName = firstName.trim()
        val familyName = lastName.trim()
        val result = try {
            auth.signUpWith(Email) {
                this.email = signupEmail
                this.password = signupPassword
                // The profiles trigger reads these to fill first_name /
                // last_name, derive a unique username handle, set the four
                // school references on the profiles row, and enroll the
                // student in the sections behind the course codes.
                data = buildJsonObject {
                    put(META_FIRST_NAME, givenName)
                    put(META_LAST_NAME, familyName)
                    put(META_FULL_NAME, joinFullName(givenName, familyName))
                    put(META_PROFILE_COMPLETED, true)
                    put(META_PROGRAM_ID, school.programId)
                    put(META_YEAR_LEVEL, school.yearLevel)
                    put(META_CAMPUS_ID, school.campusId)
                    put(META_YEAR_SECTION_ID, school.sectionId)
                    put(META_COURSE_CODES, JsonArray(courseCodes.map { JsonPrimitive(it) }))
                }
            }
        } catch (e: Exception) {
            throw asDuplicateIfApplicable(e)
        }
        return when {
            // Null result = the project auto-confirmed the address and signed
            // the user in during the sign-up call itself.
            result == null -> SignUpResult.SignedIn(
                auth.currentSessionOrNull()?.toSessionInfo()
                    ?: error("Sign-up auto-confirmed but no session is active"),
            )
            // Non-null result but an active session means sign-up also logged in.
            auth.currentSessionOrNull() != null -> SignUpResult.SignedIn(
                auth.currentSessionOrNull()!!.toSessionInfo(),
            )
            // Otherwise: account exists, email confirmation required.
            else -> SignUpResult.ConfirmationRequired
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): GoogleSignInResult {
        // Distinct local name: the config receiver's `idToken` would shadow
        // this parameter inside the lambda.
        val googleIdToken = idToken.trim()
        try {
            auth.signInWith(IDToken) {
                this.idToken = googleIdToken
                provider = Google
            }
        } catch (e: Exception) {
            throw asDuplicateIfApplicable(e)
        }
        val user = auth.currentUserOrNull()
            ?: error("Google sign-in succeeded but no session is active")
        return GoogleSignInResult(
            session = user.toSessionInfo(),
            // Supabase mirrors the Google profile into user metadata; use it
            // to prefill the completion form when it is available.
            suggestedName = metadataString(user.userMetadata, "name")
                ?: metadataString(user.userMetadata, META_FULL_NAME),
        )
    }

    override suspend fun completeGoogleProfile(
        firstName: String,
        lastName: String,
        password: String?,
        school: SchoolSelection,
        courseCodes: List<String>,
    ): SessionInfo {
        val givenName = firstName.trim()
        val familyName = lastName.trim()
        // Null password = the account already has an email credential (the
        // UI hides the password fields in that case).
        val newPassword = password?.takeIf { it.isNotBlank() }
        val updated = auth.updateUser {
            newPassword?.let { candidate -> this.password = candidate }
            data = buildJsonObject {
                put(META_FIRST_NAME, givenName)
                put(META_LAST_NAME, familyName)
                put(META_FULL_NAME, joinFullName(givenName, familyName))
                put(META_PROFILE_COMPLETED, true)
                put(META_PROGRAM_ID, school.programId)
                put(META_YEAR_LEVEL, school.yearLevel)
                put(META_CAMPUS_ID, school.campusId)
                put(META_YEAR_SECTION_ID, school.sectionId)
                put(META_COURSE_CODES, JsonArray(courseCodes.map { JsonPrimitive(it) }))
            }
        }
        return SessionInfo(
            userId = updated.id,
            email = updated.email,
            displayName = joinFullName(givenName, familyName),
            profileCompleted = true,
            hasEmailCredential = true,
        )
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun loadSchoolSelection(): StoredSchoolSelection? {
        val uid = auth.currentUserOrNull()?.id ?: return null
        val body = client.postgrest.from("profiles")
            .select(
                columns = Columns.list("program_id", "year_level", "campus_id", "year_section_id"),
            ) {
                filter { eq("id", uid) }
            }
            .data
        val row = body.parseRows().firstOrNull() ?: return null
        return StoredSchoolSelection(
            programId = row.stringValue("program_id"),
            yearLevel = row.intValue("year_level"),
            sectionId = row.stringValue("year_section_id"),
            campusId = row.stringValue("campus_id"),
        )
    }

    override suspend fun updateSchoolDetails(
        school: SchoolSelection,
        courseCodes: List<String>,
    ) {
        auth.updateUser {
            // Names are deliberately absent: the sync trigger coalesces, so
            // first/last name and the username stay exactly as they were.
            // profile_completed=true is what authorizes the school refresh;
            // the codes (re-)run the idempotent enrollment door.
            data = buildJsonObject {
                put(META_PROFILE_COMPLETED, true)
                put(META_PROGRAM_ID, school.programId)
                put(META_YEAR_LEVEL, school.yearLevel)
                put(META_CAMPUS_ID, school.campusId)
                put(META_YEAR_SECTION_ID, school.sectionId)
                put(META_COURSE_CODES, JsonArray(courseCodes.map { JsonPrimitive(it) }))
            }
        }
    }

    override suspend fun loadMyCourseCodes(): List<String> {
        val uid = auth.currentUserOrNull()?.id ?: return emptyList()
        val body = client.postgrest.from("enrollments")
            .select(
                columns = Columns.list("section:sections(course:courses(code))"),
            ) {
                filter { eq("user_id", uid) }
            }
            .data
        return body.parseRows().mapNotNull { row ->
            val section = row.get("section") as? JsonObject
            val course = section?.get("course") as? JsonObject
            course?.stringValue("code")
        }.distinct()
    }

    override suspend fun changePassword(newPassword: String) {
        auth.updateUser { password = newPassword }
    }

    // ------------------------------------------------------------- helpers

    /**
     * Supabase reports duplicate accounts with an error code (email_exists,
     * user_already_exists, identity_already_exists) or — on older servers —
     * a plain "already registered" message. Both shapes become the same
     * domain exception so the UI can show one clear message.
     */
    private fun asDuplicateIfApplicable(e: Exception): Exception {
        if (e is AuthRestException) {
            when (e.errorCode) {
                AuthErrorCode.EmailExists,
                AuthErrorCode.UserAlreadyExists,
                AuthErrorCode.IdentityAlreadyExists,
                -> return DuplicateAccountException(e.errorDescription)
                else -> Unit
            }
        }
        val message = e.message?.lowercase() ?: return e
        if (message.contains("already registered") || message.contains("already exists")) {
            return DuplicateAccountException(e.message)
        }
        return e
    }

    private fun UserSession.toSessionInfo(): SessionInfo =
        user?.toSessionInfo() ?: SessionInfo(userId = null, email = null)

    private fun UserInfo.toSessionInfo(): SessionInfo {
        val providers = appMetadata?.get("providers") as? JsonArray
        return SessionInfo(
            userId = id,
            email = email,
            displayName = metadataString(userMetadata, META_FULL_NAME),
            profileCompleted =
                userMetadata?.get(META_PROFILE_COMPLETED)?.jsonPrimitive?.booleanOrNull == true,
            hasEmailCredential = providers
                ?.any { it.jsonPrimitive.contentOrNull == "email" } == true,
        )
    }

    private fun metadataString(metadata: JsonObject?, key: String): String? =
        metadata?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

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

    private fun joinFullName(givenName: String, familyName: String): String =
        listOf(givenName, familyName).filter { it.isNotEmpty() }.joinToString(" ")

    private companion object {
        const val SESSION_RESTORE_TIMEOUT_MS = 2_000L

        /** User-metadata keys shared with the profiles triggers in SQL. */
        const val META_FIRST_NAME = "first_name"
        const val META_LAST_NAME = "last_name"
        const val META_FULL_NAME = "full_name"
        const val META_PROFILE_COMPLETED = "profile_completed"
        const val META_PROGRAM_ID = "program_id"
        const val META_YEAR_LEVEL = "year_level"
        const val META_CAMPUS_ID = "campus_id"
        const val META_YEAR_SECTION_ID = "year_section_id"
        const val META_COURSE_CODES = "course_codes"
    }
}
