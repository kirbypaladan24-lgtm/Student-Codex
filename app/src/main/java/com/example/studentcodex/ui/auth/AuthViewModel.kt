package com.example.studentcodex.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studentcodex.data.auth.AuthRepository
import com.example.studentcodex.data.auth.DuplicateAccountException
import com.example.studentcodex.data.auth.GoogleAuthManager
import com.example.studentcodex.data.auth.GoogleAuthOutcome
import com.example.studentcodex.data.auth.SessionInfo
import com.example.studentcodex.data.auth.SignUpResult
import com.example.studentcodex.data.school.SchoolDirectory
import com.example.studentcodex.data.school.SchoolDirectoryRepository
import com.example.studentcodex.data.school.SchoolSelection
import com.example.studentcodex.data.school.looksLikeCourseCode
import com.example.studentcodex.data.school.programOfferedAt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which form the gate is showing. */
enum class AuthMode { SIGN_IN, SIGN_UP }

/**
 * Form-level failures. Validation problems are resolved to strings in the UI
 * (they are local and translatable); remote failures carry the server's
 * message because it is the most actionable text we have.
 */
sealed interface AuthError {
    enum class Validation : AuthError {
        EMAIL, PASSWORD_LENGTH, PASSWORD_MISMATCH, NAME,
        /** One or more of the four school dropdowns is still unchosen. */
        SCHOOL,
        /** No course code added yet — the class list powers manual activities. */
        COURSES,
    }

    /**
     * Google Sign-In failed on the device side. [GoogleFailure.NO_ACCOUNT]
     * and [GoogleFailure.GENERIC] are device/environment problems;
     * [GoogleFailure.REJECTED] means Google Play services refused the
     * request itself (GMS code 13) - the app's package name/SHA-1 signing
     * fingerprint is missing from the Google Cloud OAuth setup.
     */
    data class Google(
        val failure: GoogleFailure,
        val detail: String? = null,
    ) : AuthError

    /** Sign-up rejected: the email or Google identity is already registered. */
    data object EmailTaken : AuthError

    data class Remote(val message: String?) : AuthError
}

/** Why Google Sign-In failed on the device; resolved to strings in the UI. */
enum class GoogleFailure { NO_ACCOUNT, REJECTED, GENERIC }

/**
 * The school registry backing the form dropdowns: programs, campuses, year
 * levels and year sections, loaded from the database (never hardcoded).
 */
sealed interface SchoolDirectoryState {
    /** First fetch in flight; the dropdowns render disabled. */
    data object Loading : SchoolDirectoryState

    data class Ready(val directory: SchoolDirectory) : SchoolDirectoryState

    /** Fetch failed; the forms show an inline error with a retry. */
    data object Failed : SchoolDirectoryState
}

sealed interface AuthUiState {

    /** Restoring a previously persisted Supabase session at cold start. */
    data object CheckingSession : AuthUiState

    data class SignedOut(
        val mode: AuthMode = AuthMode.SIGN_IN,
        val email: String = "",
        val password: String = "",
        val firstName: String = "",
        val lastName: String = "",
        val confirmPassword: String = "",
        /** School dropdown selections (registry row ids; null = unchosen). */
        val programId: String? = null,
        val yearLevel: Int? = null,
        val sectionId: String? = null,
        val campusId: String? = null,
        /** The student's added course codes (canonical registry codes). */
        val courseCodes: List<String> = emptyList(),
        /** Search text in the course picker field. */
        val courseCodeInput: String = "",
        /** True when the last add attempt could not be a course code. */
        val courseCodeError: Boolean = false,
        val isLoading: Boolean = false,
        val error: AuthError? = null,
        /** Set after sign-up when the hosted project requires email confirmation. */
        val confirmationRequired: Boolean = false,
        /** Set when Google Sign-In was tapped but is not configured in the build. */
        val showGoogleSetup: Boolean = false,
    ) : AuthUiState

    /**
     * A real session exists (just created via Google, or restored from
     * storage) but the account has no name yet. One form collects first /
     * last name — the database derives the username from them — and, for
     * accounts without one, a password so email sign-in also works later.
     * School details are collected here too, exactly like at email sign-up.
     */
    data class CompletingProfile(
        val email: String? = null,
        /** False for Google-only accounts: they still need a password. */
        val hasPasswordCredential: Boolean = false,
        val firstName: String = "",
        val lastName: String = "",
        val password: String = "",
        val confirmPassword: String = "",
        /** School dropdown selections (registry row ids; null = unchosen). */
        val programId: String? = null,
        val yearLevel: Int? = null,
        val sectionId: String? = null,
        val campusId: String? = null,
        /** The student's added course codes (canonical registry codes). */
        val courseCodes: List<String> = emptyList(),
        /** Search text in the course picker field. */
        val courseCodeInput: String = "",
        /** True when the last add attempt could not be a course code. */
        val courseCodeError: Boolean = false,
        val isLoading: Boolean = false,
        val error: AuthError? = null,
    ) : AuthUiState

    /** Signed in — either with a real Supabase session or as a demo guest. */
    data class SignedIn(val session: SessionInfo) : AuthUiState
}

/**
 * App-level authentication state. One instance lives at the root of
 * [com.example.studentcodex.ui.StudentCodexApp]; the gate and the profile's
 * sign-out button are its two UI touchpoints.
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val googleAuth: GoogleAuthManager,
    private val schoolDirectoryRepository: SchoolDirectoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.CheckingSession)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _schoolDirectory =
        MutableStateFlow<SchoolDirectoryState>(SchoolDirectoryState.Loading)
    val schoolDirectory: StateFlow<SchoolDirectoryState> = _schoolDirectory.asStateFlow()

    private var directoryJob: Job? = null

    init {
        restoreSession()
        loadSchoolDirectory()
    }

    fun onEmailChange(value: String) = updateSignedOut { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = updateSignedOut { it.copy(password = value, error = null) }

    fun onFirstNameChange(value: String) = updateSignedOut { it.copy(firstName = value, error = null) }

    fun onLastNameChange(value: String) = updateSignedOut { it.copy(lastName = value, error = null) }

    fun onConfirmPasswordChange(value: String) =
        updateSignedOut { it.copy(confirmPassword = value, error = null) }

    fun onProfileFirstNameChange(value: String) =
        updateCompletingProfile { it.copy(firstName = value, error = null) }

    fun onProfileLastNameChange(value: String) =
        updateCompletingProfile { it.copy(lastName = value, error = null) }

    fun onProfilePasswordChange(value: String) =
        updateCompletingProfile { it.copy(password = value, error = null) }

    fun onProfileConfirmPasswordChange(value: String) =
        updateCompletingProfile { it.copy(confirmPassword = value, error = null) }

    /**
     * School dropdown selections. The same four handlers serve both forms:
     * only one of them is ever visible, the other update is a no-op.
     */
    fun onSchoolProgramChange(programId: String?) {
        updateSignedOut { it.copy(programId = programId, error = null) }
        updateCompletingProfile { it.copy(programId = programId, error = null) }
    }

    fun onSchoolYearLevelChange(yearLevel: Int?) {
        updateSignedOut { it.copy(yearLevel = yearLevel, error = null) }
        updateCompletingProfile { it.copy(yearLevel = yearLevel, error = null) }
    }

    fun onSchoolSectionChange(sectionId: String?) {
        updateSignedOut { it.copy(sectionId = sectionId, error = null) }
        updateCompletingProfile { it.copy(sectionId = sectionId, error = null) }
    }

    /**
     * Campus comes FIRST in the forms: choosing one narrows the Program
     * dropdown to that campus offerings (see
     * [SchoolDirectory.programsForCampus]). A program the new campus does
     * not offer — e.g. BSHM picked from the all-campus list, then Goa
     * chosen — is dropped so the stale id can never be submitted.
     */
    fun onSchoolCampusChange(campusId: String?) {
        val directory = (schoolDirectory.value as? SchoolDirectoryState.Ready)?.directory
        updateSignedOut {
            it.copy(
                campusId = campusId,
                error = null,
                programId = it.programId?.takeIf { id -> programOfferedAt(directory, campusId, id) },
            )
        }
        updateCompletingProfile {
            it.copy(
                campusId = campusId,
                error = null,
                programId = it.programId?.takeIf { id -> programOfferedAt(directory, campusId, id) },
            )
        }
    }

    /** Search text typed into the course picker field (either form). */
    fun onCourseCodeInputChange(value: String) {
        updateSignedOut { it.copy(courseCodeInput = value, courseCodeError = false) }
        updateCompletingProfile { it.copy(courseCodeInput = value, courseCodeError = false) }
    }

    /**
     * Adds a course code as a chip. KNOWN codes canonicalize against the
     * registry (case-insensitive, duplicates fold silently); UNKNOWN codes
     * are accepted as typed — the catalog can never list every subject a
     * real semester offers, so the server creates the course (and the
     * cohort section) from the code at save time (migration 0019). Only
     * input that cannot be a code at all (letters first, then
     * letters/digits/spaces/hyphens) is rejected. While the directory
     * itself is unavailable the same shape check runs locally.
     *
     * [code] is an explicit pick from the course dropdown (a row tap);
     * null reads the field's typed search text (keyboard "done" path).
     */
    fun addCourseCode(code: String? = null) {
        val state = uiState.value
        val input = if (code != null) {
            code
        } else {
            when (state) {
                is AuthUiState.SignedOut -> state.courseCodeInput
                is AuthUiState.CompletingProfile -> state.courseCodeInput
                else -> return
            }
        }
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        val directory = (schoolDirectory.value as? SchoolDirectoryState.Ready)?.directory
        val resolved: String? = if (directory != null) {
            directory.normalizeCourseCode(trimmed)
        } else {
            trimmed.replace(Regex("\\s+"), " ").takeIf { looksLikeCourseCode(it) }
        }
        if (resolved == null) {
            updateSignedOut { it.copy(courseCodeError = true) }
            updateCompletingProfile { it.copy(courseCodeError = true) }
            return
        }
        updateSignedOut { s ->
            if (s.courseCodes.any { it.equals(resolved, ignoreCase = true) }) {
                s.copy(courseCodeInput = "", courseCodeError = false)
            } else {
                s.copy(courseCodes = s.courseCodes + resolved, courseCodeInput = "", courseCodeError = false)
            }
        }
        updateCompletingProfile { s ->
            if (s.courseCodes.any { it.equals(resolved, ignoreCase = true) }) {
                s.copy(courseCodeInput = "", courseCodeError = false)
            } else {
                s.copy(courseCodes = s.courseCodes + resolved, courseCodeInput = "", courseCodeError = false)
            }
        }
    }

    /** Removes an added course chip (the enrollments door is append-only —
     * removing here only stops the code from being (re-)submitted). */
    fun removeCourseCode(code: String) {
        updateSignedOut { it.copy(courseCodes = it.courseCodes.filterNot { c -> c.equals(code, ignoreCase = true) }) }
        updateCompletingProfile { it.copy(courseCodes = it.courseCodes.filterNot { c -> c.equals(code, ignoreCase = true) }) }
    }

    /** Retry after [SchoolDirectoryState.Failed]. */
    fun retrySchoolDirectory() = loadSchoolDirectory()

    fun switchMode() = updateSignedOut {
        val nextMode = if (it.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN
        it.copy(
            mode = nextMode,
            error = null,
            // Confirm field only exists in sign-up mode; keep credentials.
            confirmPassword = "",
            confirmationRequired = false,
            showGoogleSetup = false,
        )
    }

    fun dismissConfirmation() = updateSignedOut { it.copy(confirmationRequired = false) }

    fun dismissGoogleSetup() = updateSignedOut { it.copy(showGoogleSetup = false) }

    /** Primary submit button: behavior depends on the visible mode. */
    fun submit() {
        when ((uiState.value as? AuthUiState.SignedOut)?.mode) {
            AuthMode.SIGN_IN -> signIn()
            AuthMode.SIGN_UP -> signUp()
            null -> Unit
        }
    }

    fun signIn() {
        val state = uiState.value as? AuthUiState.SignedOut ?: return
        if (state.isLoading) return
        val email = state.email.trim()
        when {
            !isValidEmail(email) ->
                updateSignedOut { it.copy(error = AuthError.Validation.EMAIL) }
            state.password.length < MIN_PASSWORD_LENGTH ->
                updateSignedOut { it.copy(error = AuthError.Validation.PASSWORD_LENGTH) }
            else -> runAuth { authRepository.signIn(email, state.password) }
        }
    }

    fun signUp() {
        val state = uiState.value as? AuthUiState.SignedOut ?: return
        if (state.isLoading) return
        val email = state.email.trim()
        val programId = state.programId
        val yearLevel = state.yearLevel
        val sectionId = state.sectionId
        val campusId = state.campusId
        when {
            !isValidEmail(email) ->
                updateSignedOut { it.copy(error = AuthError.Validation.EMAIL) }
            state.firstName.isBlank() || state.lastName.isBlank() ->
                updateSignedOut { it.copy(error = AuthError.Validation.NAME) }
            programId == null || yearLevel == null || sectionId == null || campusId == null ->
                updateSignedOut { it.copy(error = AuthError.Validation.SCHOOL) }
            state.password.length < MIN_PASSWORD_LENGTH ->
                updateSignedOut { it.copy(error = AuthError.Validation.PASSWORD_LENGTH) }
            state.password != state.confirmPassword ->
                updateSignedOut { it.copy(error = AuthError.Validation.PASSWORD_MISMATCH) }
            state.courseCodes.isEmpty() ->
                updateSignedOut { it.copy(error = AuthError.Validation.COURSES) }
            else -> {
                val school = SchoolSelection(
                    programId = programId,
                    yearLevel = yearLevel,
                    sectionId = sectionId,
                    campusId = campusId,
                )
                updateSignedOut {
                    it.copy(
                        isLoading = true,
                        error = null,
                        confirmationRequired = false,
                        showGoogleSetup = false,
                    )
                }
                viewModelScope.launch {
                    try {
                        when (
                            val result = authRepository.signUp(
                                email, state.password, state.firstName.trim(), state.lastName.trim(),
                                school,
                                state.courseCodes,
                            )
                        ) {
                            is SignUpResult.SignedIn ->
                                _uiState.value = AuthUiState.SignedIn(result.session)
                            is SignUpResult.ConfirmationRequired ->
                                // Switch to the sign-in form; the notice explains
                                // what to do once the email link was clicked.
                                updateSignedOut {
                                    it.copy(
                                        isLoading = false,
                                        mode = AuthMode.SIGN_IN,
                                        password = "",
                                        confirmPassword = "",
                                        confirmationRequired = true,
                                    )
                                }
                        }
                    } catch (e: Exception) {
                        publishRemoteError(e)
                    }
                }
            }
        }
    }

    /**
     * Google Sign-In. The system account picker runs as its own UI, so the
     * form's loading flag only covers the Supabase token exchange that
     * follows. When no web client ID was provided at build time, tapping
     * the button explains the setup instead of silently doing nothing.
     *
     * First-time Google users land on [AuthUiState.CompletingProfile]: one
     * form collects their name (-> username in the database), school
     * details, and a password so email sign-in works too. Returning users
     * go straight in.
     *
     * The activity comes from the screen (LocalActivity) so the picker has
     * a window to attach to; it is used immediately and never stored. The
     * nullable type is the seam that keeps this flow testable on the JVM.
     */
    fun signInWithGoogle(activity: Activity?) {
        val state = uiState.value as? AuthUiState.SignedOut ?: return
        if (state.isLoading) return
        if (!googleAuth.isConfigured) {
            updateSignedOut { it.copy(showGoogleSetup = true) }
            return
        }
        viewModelScope.launch {
            when (val outcome = googleAuth.fetchIdToken(activity)) {
                is GoogleAuthOutcome.Token -> {
                    try {
                        val result = authRepository.signInWithGoogleIdToken(outcome.idToken)
                        if (result.session.profileCompleted) {
                            _uiState.value = AuthUiState.SignedIn(result.session)
                        } else {
                            _uiState.value = AuthUiState.CompletingProfile(
                                email = result.session.email,
                                hasPasswordCredential = result.session.hasEmailCredential,
                                firstName = suggestedFirstName(result.suggestedName),
                                lastName = suggestedLastName(result.suggestedName),
                            )
                        }
                    } catch (e: Exception) {
                        publishRemoteError(e)
                    }
                }
                GoogleAuthOutcome.NotConfigured ->
                    updateSignedOut { it.copy(showGoogleSetup = true) }
                GoogleAuthOutcome.Cancelled -> Unit
                GoogleAuthOutcome.NoAccount ->
                    updateSignedOut {
                        it.copy(error = AuthError.Google(GoogleFailure.NO_ACCOUNT))
                    }
                is GoogleAuthOutcome.Rejected ->
                    updateSignedOut {
                        it.copy(error = AuthError.Google(GoogleFailure.REJECTED, outcome.code))
                    }
                is GoogleAuthOutcome.Error ->
                    updateSignedOut {
                        it.copy(error = AuthError.Google(GoogleFailure.GENERIC, outcome.message))
                    }
            }
        }
    }

    /** Submit button of the profile-completion form. */
    fun submitProfileCompletion() {
        val state = uiState.value as? AuthUiState.CompletingProfile ?: return
        if (state.isLoading) return
        val firstName = state.firstName.trim()
        val lastName = state.lastName.trim()
        val programId = state.programId
        val yearLevel = state.yearLevel
        val sectionId = state.sectionId
        val campusId = state.campusId
        val needsPassword = !state.hasPasswordCredential
        when {
            firstName.isEmpty() || lastName.isEmpty() ->
                updateCompletingProfile { it.copy(error = AuthError.Validation.NAME) }
            programId == null || yearLevel == null || sectionId == null || campusId == null ->
                updateCompletingProfile { it.copy(error = AuthError.Validation.SCHOOL) }
            needsPassword && state.password.length < MIN_PASSWORD_LENGTH ->
                updateCompletingProfile { it.copy(error = AuthError.Validation.PASSWORD_LENGTH) }
            needsPassword && state.password != state.confirmPassword ->
                updateCompletingProfile { it.copy(error = AuthError.Validation.PASSWORD_MISMATCH) }
            state.courseCodes.isEmpty() ->
                updateCompletingProfile { it.copy(error = AuthError.Validation.COURSES) }
            else -> {
                val school = SchoolSelection(
                    programId = programId,
                    yearLevel = yearLevel,
                    sectionId = sectionId,
                    campusId = campusId,
                )
                updateCompletingProfile { it.copy(isLoading = true, error = null) }
                viewModelScope.launch {
                    try {
                        val session = authRepository.completeGoogleProfile(
                            firstName,
                            lastName,
                            state.password.takeIf { needsPassword },
                            school,
                            state.courseCodes,
                        )
                        _uiState.value = AuthUiState.SignedIn(session)
                    } catch (e: Exception) {
                        updateCompletingProfile {
                            it.copy(isLoading = false, error = remoteError(e))
                        }
                    }
                }
            }
        }
    }

    /**
     * "Sign out instead" on the completion form: revoke the half-finished
     * session and return to the gate. Next Google sign-in asks again.
     */
    fun cancelProfileCompletion() {
        if (uiState.value !is AuthUiState.CompletingProfile) return
        _uiState.value = AuthUiState.SignedOut()
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } catch (e: Exception) {
                // Local state is already cleared; next sign-in overwrites
                // the stored session anyway. Swallow and move on.
            }
        }
    }

    /**
     * The explicit bypass: a guest session in memory only. No Supabase
     * account, no network calls — the app runs on demo repositories.
     */
    fun signInAsGuest() {
        if (uiState.value is AuthUiState.SignedIn) return
        _uiState.value = AuthUiState.SignedIn(
            SessionInfo(userId = null, email = null, isGuest = true),
        )
    }

    fun signOut() {
        val current = uiState.value as? AuthUiState.SignedIn ?: return
        // The UI returns to the gate immediately; the remote sign-out runs
        // behind it. Guests never had a Supabase session to revoke.
        _uiState.value = AuthUiState.SignedOut()
        if (!current.session.isGuest) {
            viewModelScope.launch {
                try {
                    authRepository.signOut()
                } catch (e: Exception) {
                    // Local state is already cleared; next sign-in overwrites
                    // the stored session anyway. Swallow and move on.
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun restoreSession() {
        viewModelScope.launch {
            val session = try {
                authRepository.currentSession()
            } catch (e: Exception) {
                null
            }
            _uiState.value = when {
                session == null -> AuthUiState.SignedOut()
                // Accounts created before names were collected (or a Google
                // sign-up abandoned mid-form) complete their profile once.
                !session.isGuest && !session.profileCompleted ->
                    AuthUiState.CompletingProfile(
                        email = session.email,
                        hasPasswordCredential = session.hasEmailCredential,
                    )
                else -> AuthUiState.SignedIn(session)
            }
        }
    }

    private fun loadSchoolDirectory() {
        if (directoryJob?.isActive == true) return
        _schoolDirectory.value = SchoolDirectoryState.Loading
        directoryJob = viewModelScope.launch {
            try {
                _schoolDirectory.value =
                    SchoolDirectoryState.Ready(schoolDirectoryRepository.load())
            } catch (e: Exception) {
                _schoolDirectory.value = SchoolDirectoryState.Failed
            }
        }
    }

    private fun runAuth(block: suspend () -> SessionInfo) {
        updateSignedOut {
            it.copy(isLoading = true, error = null, confirmationRequired = false, showGoogleSetup = false)
        }
        viewModelScope.launch {
            try {
                val session = block()
                _uiState.value = AuthUiState.SignedIn(session)
            } catch (e: Exception) {
                publishRemoteError(e)
            }
        }
    }

    private fun publishRemoteError(e: Exception) {
        updateSignedOut {
            it.copy(isLoading = false, error = remoteError(e))
        }
    }

    private fun remoteError(e: Exception): AuthError = when {
        e is DuplicateAccountException -> AuthError.EmailTaken
        else -> AuthError.Remote(e.message?.takeIf { msg -> msg.isNotBlank() })
    }

    private fun updateSignedOut(reduce: (AuthUiState.SignedOut) -> AuthUiState.SignedOut) {
        val current = _uiState.value as? AuthUiState.SignedOut ?: return
        _uiState.value = reduce(current)
    }

    private fun updateCompletingProfile(
        reduce: (AuthUiState.CompletingProfile) -> AuthUiState.CompletingProfile,
    ) {
        val current = _uiState.value as? AuthUiState.CompletingProfile ?: return
        _uiState.value = reduce(current)
    }

    /**
     * Google's profile name ("Juan Dela Cruz") prefills the form: the first
     * word becomes the first name, everything after it the last name.
     */
    private fun suggestedFirstName(suggestedName: String?): String =
        suggestedName?.trim()?.split(Regex("\\s+"))?.firstOrNull().orEmpty()

    private fun suggestedLastName(suggestedName: String?): String {
        val parts = suggestedName?.trim()?.split(Regex("\\s+")).orEmpty()
        return if (parts.size > 1) parts.drop(1).joinToString(" ") else ""
    }

    private fun isValidEmail(email: String): Boolean {
        val at = email.indexOf('@')
        return at > 0 && at < email.length - 1 && !email.contains(' ') && email.count { it == '@' } == 1
    }

    companion object {
        /** Supabase's default minimum password length. */
        const val MIN_PASSWORD_LENGTH = 6
    }
}
